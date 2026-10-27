package com.spiralcore.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlatformSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.io.File

/**
 * Converts the existing Room SQLite file into a separate SQLCipher file.
 *
 * The plaintext source is never overwritten. The encrypted file is verified before the durable
 * state advances to ENCRYPTED_READY. Once this state is committed, future launches prefer the
 * encrypted file. The plaintext source is retained until encrypted Room successfully opens and
 * the application explicitly calls markEncryptedDatabaseOpened().
 */
internal class DatabaseEncryptionMigrator(
    context: Context,
    private val keyManager: DatabaseKeyManager,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val sourceFile = appContext.getDatabasePath(PLAINTEXT_DATABASE_NAME)
    private val encryptedFile = appContext.getDatabasePath(ENCRYPTED_DATABASE_NAME)
    private val temporaryFile = File(encryptedFile.parentFile, "${encryptedFile.name}.migrating")

    @Synchronized
    fun prepareEncryptedDatabase() {
        CipherDatabase.loadLibs(appContext)
        when (preferences.getString(STATE_KEY, STATE_PLAINTEXT)) {
            STATE_ENCRYPTED_READY, STATE_ENCRYPTED_COMMITTED -> {
                verifyEncryptedFile()
            }
            STATE_PLAINTEXT -> encryptIfNeeded()
            STATE_ENCRYPTING -> recoverInterruptedMigration()
            else -> throw IllegalStateException("Unknown database encryption migration state")
        }
    }

    @Synchronized
    fun markEncryptedDatabaseOpened() {
        check(preferences.getString(STATE_KEY, STATE_PLAINTEXT) in setOf(
            STATE_ENCRYPTED_READY,
            STATE_ENCRYPTED_COMMITTED,
        )) { "Encrypted database has not completed migration" }

        verifyEncryptedFile()
        check(preferences.edit().putString(STATE_KEY, STATE_ENCRYPTED_COMMITTED).commit()) {
            "Unable to persist encrypted database state"
        }

        // Deletion happens only after the encrypted Room instance has opened successfully.
        // Failure to delete the source must not stop the application from using encrypted data.
        // Preserve the verified original file until a separate cleanup policy is implemented.
        // Flash storage cannot guarantee forensic erasure; keeping it avoids an unsafe one-shot
        // delete immediately after first successful open.
        preferences.edit().putBoolean(PLAINTEXT_CLEANUP_PENDING, sourceFile.exists()).commit()
    }

    private fun encryptIfNeeded() {
        if (!sourceFile.exists()) {
            // First install: no plaintext database exists, so Room may create a fresh encrypted DB.
            check(preferences.edit().putString(STATE_KEY, STATE_ENCRYPTED_READY).commit()) {
                "Unable to persist initial encrypted database state"
            }
            return
        }

        check(preferences.edit().putString(STATE_KEY, STATE_ENCRYPTING).commit()) {
            "Unable to persist migration start state"
        }
        migratePlaintextToEncrypted()
    }

    private fun recoverInterruptedMigration() {
        // Keep the plaintext source. A valid final file can be recovered; partial temporary
        // files are disposable only while the plaintext source remains available.
        if (encryptedFile.exists() && canOpenEncrypted(encryptedFile)) {
            check(preferences.edit().putString(STATE_KEY, STATE_ENCRYPTED_READY).commit()) {
                "Unable to persist recovered encrypted database state"
            }
            return
        }
        if (!sourceFile.exists()) {
            throw IllegalStateException(
                "Database migration was interrupted and neither a usable encrypted file nor the plaintext source exists",
            )
        }
        temporaryFile.delete()
        encryptedFile.delete()
        migratePlaintextToEncrypted()
    }

    private fun migratePlaintextToEncrypted() {
        check(sourceFile.exists()) { "The plaintext database is missing" }
        temporaryFile.delete()
        // Do not destroy an existing encrypted database until the replacement is verified.
        // If migration was previously interrupted, recovery chooses a verified file before retrying.
        val key = keyManager.getOrCreateDatabaseKey()
        try {
            val oldCounts: Map<String, Long>
            PlatformSQLiteDatabase.openDatabase(
                sourceFile.absolutePath,
                null,
                PlatformSQLiteDatabase.OPEN_READONLY,
            ).use { source ->
                source.rawQuery("PRAGMA quick_check", null).use { cursor ->
                    check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                        "Plaintext database integrity check failed"
                    }
                }
                oldCounts = tableCounts(source)
            }

            SQLCipherMigrationHelper(appContext, temporaryFile, key).use { helper ->
                val destination = helper.writableDatabase
                try {
                    // SQLCipher's documented plaintext conversion path is ATTACH + sqlcipher_export.
                    destination.execSQL("ATTACH DATABASE '${sourceFile.absolutePath.replace("'", "''")}' AS plaintext KEY ''")
                    destination.rawQuery("SELECT sqlcipher_export('main', 'plaintext')", null).use {
                        check(it.moveToFirst()) { "SQLCipher export did not return a result" }
                    }
                    destination.execSQL("DETACH DATABASE plaintext")

                    val newCounts = tableCounts(destination)
                    check(oldCounts == newCounts) {
                        "Database verification failed after encryption export"
                    }
                    destination.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                        check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                            "Encrypted database integrity check failed"
                        }
                    }
                } finally {
                    destination.close()
                }
            }

            check(canOpenEncrypted(temporaryFile, key)) {
                "Encrypted database verification failed"
            }

            check(!encryptedFile.exists()) {
                "Encrypted destination already exists and must be recovered before replacement"
            }
            check(temporaryFile.renameTo(encryptedFile)) {
                "Unable to atomically promote encrypted database file"
            }
            check(preferences.edit().putString(STATE_KEY, STATE_ENCRYPTED_READY).commit()) {
                "Unable to persist successful encryption migration state"
            }
        } catch (exception: Exception) {
            // Keep the original database untouched. A partial destination is safe to discard.
            temporaryFile.delete()
            throw IllegalStateException(
                "Could not encrypt the existing database; plaintext data was retained",
                exception,
            )
        } finally {
            key.fill(0)
        }
    }

    private fun verifyEncryptedFile() {
        check(encryptedFile.exists() && canOpenEncrypted(encryptedFile)) {
            "Encrypted database is missing or cannot be opened with the stored key"
        }
    }

    private fun canOpenEncrypted(file: File, keyOverride: ByteArray? = null): Boolean = try {
        if (!file.exists()) return false
        val key = keyOverride ?: keyManager.getOrCreateDatabaseKey()
        val helper = SQLCipherMigrationHelper(appContext, file, key)
        helper.writableDatabase.use { database ->
            database.rawQuery("PRAGMA cipher_integrity_check", null).use { cursor ->
                check(cursor.moveToFirst())
                do {
                    check(cursor.getString(0).equals("ok", ignoreCase = true)) {
                        "Encrypted database integrity check failed"
                    }
                } while (cursor.moveToNext())
            }
            database.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                check(cursor.moveToFirst()) { "Encrypted database schema is unreadable" }
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun tableCounts(database: PlatformSQLiteDatabase): Map<String, Long> {
        val tables = linkedMapOf<String, Long>()
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val table = cursor.getString(0)
                database.rawQuery("SELECT COUNT(*) FROM \"${table.replace("\"", "\"\"")}\"", null).use { countCursor ->
                    check(countCursor.moveToFirst())
                    tables[table] = countCursor.getLong(0)
                }
            }
        }
        return tables
    }

    private fun tableCounts(database: net.zetetic.database.sqlcipher.SQLiteDatabase): Map<String, Long> {
        val tables = linkedMapOf<String, Long>()
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val table = cursor.getString(0)
                database.rawQuery("SELECT COUNT(*) FROM \"${table.replace("\"", "\"\"")}\"", null).use { countCursor ->
                    check(countCursor.moveToFirst())
                    tables[table] = countCursor.getLong(0)
                }
            }
        }
        return tables
    }

    private class SQLCipherMigrationHelper(
        context: Context,
        file: File,
        key: ByteArray,
    ) : SQLiteOpenHelper(context, file.absolutePath, key, null, 2, 0, null, null, false) {
        override fun onCreate(database: CipherDatabase) = Unit
        override fun onUpgrade(database: CipherDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val PLAINTEXT_DATABASE_NAME = "spiralcore.db"
        const val ENCRYPTED_DATABASE_NAME = "spiralcore.encrypted.db"
        const val PREFERENCES_NAME = "database_encryption_state"
        const val STATE_KEY = "state"
        const val STATE_PLAINTEXT = "plaintext"
        const val STATE_ENCRYPTING = "encrypting"
        const val STATE_ENCRYPTED_READY = "encrypted_ready"
        const val STATE_ENCRYPTED_COMMITTED = "encrypted_committed"
        const val PLAINTEXT_CLEANUP_PENDING = "plaintext_cleanup_pending"
    }
}
