package com.spiralcore.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlatformSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.io.File

/**
 * Converts the existing Room SQLite file into a separate SQLCipher file.
 *
 * The plaintext source is never overwritten during conversion. The encrypted file is verified
 * before durable state changes, and plaintext is retained until encrypted Room opens successfully.
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
        when (preferences.getString(STATE_KEY, STATE_PLAINTEXT)) {
            STATE_ENCRYPTED_READY, STATE_ENCRYPTED_COMMITTED -> {
                verifyEncryptedFile()
                retryPlaintextCleanupIfNeeded()
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

        // Verify the file that Room opened before recording it as authoritative.
        verifyEncryptedFile()
        check(preferences.edit().putString(STATE_KEY, STATE_ENCRYPTED_COMMITTED).commit()) {
            "Unable to persist encrypted database state"
        }
        retryPlaintextCleanupIfNeeded()
    }

    private fun retryPlaintextCleanupIfNeeded() {
        val alreadyCommitted = preferences.getString(STATE_KEY, STATE_PLAINTEXT) == STATE_ENCRYPTED_COMMITTED
        if (!alreadyCommitted) return
        val cleanupSucceeded = removePlaintextDatabase()
        check(
            preferences.edit()
                .putBoolean(PLAINTEXT_CLEANUP_PENDING, !cleanupSucceeded)
                .commit(),
        ) { "Unable to persist plaintext cleanup status" }

        // Cleanup failure must not prevent the user opening their verified encrypted vault.
        // It remains recorded and is retried on later launches. Never log URLs or key material.
    }

    private fun removePlaintextDatabase(): Boolean {
        if (!sourceFile.exists()) return true
        val sidecars = listOf(
            File(sourceFile.absolutePath + "-wal"),
            File(sourceFile.absolutePath + "-shm"),
            File(sourceFile.absolutePath + "-journal"),
        )
        sidecars.forEach { file ->
            if (file.exists() && !file.delete()) return false
        }
        return !sourceFile.exists() || sourceFile.delete()
    }

    private fun encryptIfNeeded() {
        if (!sourceFile.exists()) {
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
        if (encryptedFile.exists()) {
            if (canOpenEncrypted(encryptedFile)) {
                check(preferences.edit().putString(STATE_KEY, STATE_ENCRYPTED_READY).commit()) {
                    "Unable to persist recovered encrypted database state"
                }
                return
            }
            check(sourceFile.exists()) {
                "Encrypted destination cannot be verified and the plaintext source is missing"
            }
            check(encryptedFile.delete()) {
                "Unable to remove invalid encrypted destination; refusing to continue"
            }
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
        val key = keyManager.getOrCreateDatabaseKey()
        try {
            val oldCounts: Map<String, Long>
            val sourceVersion = PlatformSQLiteDatabase.openDatabase(
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
                source.rawQuery("PRAGMA user_version", null).use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                }
            }
            check(sourceVersion in 1..CURRENT_ROOM_VERSION) {
                "Unsupported Room database version $sourceVersion; plaintext data was retained"
            }

            SQLCipherMigrationHelper(appContext, temporaryFile, key, sourceVersion).use { helper ->
                val destination = helper.writableDatabase
                try {
                    // Source filename comes from Android's app-private database directory.
                    // Quote as a SQL string literal and escape embedded apostrophes.
                    val escapedSource = sourceFile.absolutePath.replace("'", "''")
                    destination.execSQL("ATTACH DATABASE '$escapedSource' AS plaintext KEY ''")
                    destination.rawQuery("SELECT sqlcipher_export('main', 'plaintext')", null).use { cursor ->
                        check(cursor.moveToFirst()) { "SQLCipher export did not return a result" }
                    }
                    destination.execSQL("PRAGMA user_version = $sourceVersion")
                    destination.execSQL("DETACH DATABASE plaintext")

                    check(oldCounts == tableCounts(destination)) {
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

            check(canOpenEncrypted(temporaryFile, key)) { "Encrypted database verification failed" }
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

    private fun canOpenEncrypted(file: File, keyOverride: ByteArray? = null): Boolean {
        if (!file.exists()) return false
        val key = keyOverride ?: keyManager.getOrCreateDatabaseKey()
        return try {
            SQLCipherMigrationHelper(appContext, file, key, CURRENT_ROOM_VERSION).use { helper ->
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
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            if (keyOverride == null) key.fill(0)
        }
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

    private fun tableCounts(database: CipherDatabase): Map<String, Long> {
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
        databaseVersion: Int,
    ) : SQLiteOpenHelper(context, file.absolutePath, key, null, databaseVersion, 0, null, null, false) {
        override fun onCreate(database: CipherDatabase) = Unit
        override fun onUpgrade(database: CipherDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val PLAINTEXT_DATABASE_NAME = "spiralcore.db"
        const val CURRENT_ROOM_VERSION = 2
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
