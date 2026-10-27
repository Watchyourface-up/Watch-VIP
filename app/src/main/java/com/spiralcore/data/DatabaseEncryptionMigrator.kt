package com.spiralcore.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlatformSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.io.File

/**
 * Converts the existing Room SQLite file into a separate SQLCipher file.
 * Plaintext data is retained until the encrypted database has been independently verified
 * and Room has opened the encrypted copy successfully.
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
            STATE_ENCRYPTED_READY, STATE_ENCRYPTED_COMMITTED,
        )) { "Encrypted database has not completed migration" }

        verifyEncryptedFile()
        check(preferences.edit().putString(STATE_KEY, STATE_ENCRYPTED_COMMITTED).commit()) {
            "Unable to persist encrypted database state"
        }
        retryPlaintextCleanupIfNeeded()
    }

    private fun retryPlaintextCleanupIfNeeded() {
        if (preferences.getString(STATE_KEY, STATE_PLAINTEXT) != STATE_ENCRYPTED_COMMITTED) return
        val cleanupSucceeded = removePlaintextDatabase()
        check(preferences.edit().putBoolean(PLAINTEXT_CLEANUP_PENDING, !cleanupSucceeded).commit()) {
            "Unable to persist plaintext cleanup status"
        }
        // Flash storage cannot guarantee forensic erasure.
    }

    private fun removePlaintextDatabase(): Boolean {
        if (!sourceFile.exists()) return true
        val sidecars = listOf(
            File(sourceFile.absolutePath + "-wal"),
            File(sourceFile.absolutePath + "-shm"),
            File(sourceFile.absolutePath + "-journal"),
        )
        for (file in sidecars) {
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
        check(sourceFile.exists()) {
            "Database migration was interrupted and neither a usable encrypted file nor plaintext source exists"
        }
        temporaryFile.delete()
        encryptedFile.delete()
        migratePlaintextToEncrypted()
    }

    private fun migratePlaintextToEncrypted() {
        check(sourceFile.exists()) { "The plaintext database is missing" }
        check(!encryptedFile.exists()) {
            "Encrypted destination already exists and must be recovered before replacement"
        }
        if (temporaryFile.exists()) {
            check(temporaryFile.delete()) { "Unable to remove stale temporary database" }
        }
        val key = keyManager.getOrCreateDatabaseKey()
        try {
            val source = PlatformSQLiteDatabase.openDatabase(
                sourceFile.absolutePath, null, PlatformSQLiteDatabase.OPEN_READONLY,
            )
            val expected = source.use { db ->
                db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                    check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                        "Plaintext database integrity check failed"
                    }
                }
                val version = db.rawQuery("PRAGMA user_version", null).use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                }
                check(version in 1..CURRENT_ROOM_VERSION) {
                    "Unsupported Room database version $version; plaintext data was retained"
                }
                Snapshot(
                    version = version,
                    schema = schemaSnapshot(db),
                    rows = rowSnapshot(db),
                )
            }

            SQLCipherMigrationHelper(appContext, temporaryFile, key, expected.version).use { helper ->
                val destination = helper.writableDatabase
                try {
                    val escapedSource = sourceFile.absolutePath.replace("'", "''")
                    destination.execSQL("ATTACH DATABASE '$escapedSource' AS plaintext KEY ''")
                    destination.rawQuery("SELECT sqlcipher_export('main', 'plaintext')", null).use { cursor ->
                        check(cursor.moveToFirst()) { "SQLCipher export did not return a result" }
                    }
                    destination.execSQL("PRAGMA user_version = ${expected.version}")
                    destination.execSQL("DETACH DATABASE plaintext")

                    check(schemaSnapshot(destination) == expected.schema) {
                        "Database schema verification failed after encryption export"
                    }
                    check(rowSnapshot(destination) == expected.rows) {
                        "Database row verification failed after encryption export"
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
            check(temporaryFile.renameTo(encryptedFile)) {
                "Unable to promote encrypted database file"
            }
            check(preferences.edit().putString(STATE_KEY, STATE_ENCRYPTED_READY).commit()) {
                "Unable to persist successful encryption migration state"
            }
        } catch (exception: Exception) {
            if (temporaryFile.exists()) temporaryFile.delete()
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

    private fun schemaSnapshot(database: PlatformSQLiteDatabase): List<String> =
        database.rawQuery(
            """
            SELECT type, name, tbl_name, COALESCE(sql, '')
            FROM sqlite_master
            WHERE name NOT LIKE 'sqlite_%'
            ORDER BY type, name, tbl_name, sql
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add("${cursor.getString(0)}|${cursor.getString(1)}|${cursor.getString(2)}|${cursor.getString(3)}")
                }
            }
        }

    private fun schemaSnapshot(database: CipherDatabase): List<String> =
        database.rawQuery(
            """
            SELECT type, name, tbl_name, COALESCE(sql, '')
            FROM sqlite_master
            WHERE name NOT LIKE 'sqlite_%'
            ORDER BY type, name, tbl_name, sql
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add("${cursor.getString(0)}|${cursor.getString(1)}|${cursor.getString(2)}|${cursor.getString(3)}")
                }
            }
        }

    /**
     * Captures all stored values as typed, length-delimited fields. Row ordering is based on
     * rowid where available; WITHOUT ROWID tables use their primary-key/index order.
     */
    private fun rowSnapshot(database: PlatformSQLiteDatabase): Map<String, List<String>> =
        readRows(database) { query, args -> database.rawQuery(query, args) }

    private fun rowSnapshot(database: CipherDatabase): Map<String, List<String>> =
        readRows(database) { query, args -> database.rawQuery(query, args) }

    private fun readRows(
        database: Any,
        query: (String, Array<String>?) -> android.database.Cursor,
    ): Map<String, List<String>> {
        val tables = when (database) {
            is PlatformSQLiteDatabase -> database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null,
            )
            is CipherDatabase -> database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null,
            )
            else -> error("Unsupported database handle")
        }
        return tables.use { tableCursor ->
            val result = linkedMapOf<String, List<String>>()
            while (tableCursor.moveToNext()) {
                val table = tableCursor.getString(0)
                val escapedTable = table.replace("\"", "\"\"")
                val sql = "SELECT * FROM \"$escapedTable\" ORDER BY rowid"
                val rows = query(sql, null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(buildString {
                                for (index in 0 until cursor.columnCount) {
                                    val type = cursor.getType(index)
                                    val value = when (type) {
                                        android.database.Cursor.FIELD_TYPE_NULL -> "N"
                                        android.database.Cursor.FIELD_TYPE_INTEGER -> "I:${cursor.getLong(index)}"
                                        android.database.Cursor.FIELD_TYPE_FLOAT -> "F:${cursor.getDouble(index)}"
                                        android.database.Cursor.FIELD_TYPE_STRING -> "S:${cursor.getString(index).length}:${cursor.getString(index)}"
                                        android.database.Cursor.FIELD_TYPE_BLOB -> {
                                            val bytes = cursor.getBlob(index)
                                            "B:${bytes.size}:" + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                        }
                                        else -> error("Unsupported SQLite value type")
                                    }
                                    append(value.length).append(':').append(value).append('|')
                                }
                            })
                        }
                    }
                }
                result[table] = rows
            }
            result
        }
    }

    private data class Snapshot(
        val version: Int,
        val schema: List<String>,
        val rows: Map<String, List<String>>,
    )

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
