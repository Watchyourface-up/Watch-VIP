package com.spiralcore.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionMigrationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun existingPlaintextDatabaseIsCopiedAndVerifiedBeforeUse() {
        val source = context.getDatabasePath("spiralcore.db")
        val encrypted = context.getDatabasePath("spiralcore.encrypted.db")
        val preferences = context.getSharedPreferences("database_encryption_state", Context.MODE_PRIVATE)
        source.parentFile?.mkdirs()
        source.delete()
        encrypted.delete()
        File(encrypted.parentFile, "${encrypted.name}.migrating").delete()
        preferences.edit().clear().commit()
        context.getSharedPreferences("database_key_metadata", Context.MODE_PRIVATE).edit().clear().commit()

        SQLiteDatabase.openOrCreateDatabase(source, null).use { db ->
            db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE links(id INTEGER PRIMARY KEY, url TEXT NOT NULL, title TEXT NOT NULL, categoryId INTEGER NOT NULL, createdAt INTEGER NOT NULL, shellSeed INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX index_links_categoryId ON links(categoryId)")
            db.execSQL("CREATE UNIQUE INDEX index_links_url ON links(url)")
            db.execSQL("CREATE UNIQUE INDEX index_links_shellSeed ON links(shellSeed)")
            db.execSQL("INSERT INTO categories VALUES(1, 'شخصي', 0)")
            db.execSQL("INSERT INTO links VALUES(1, 'https://example.com/', 'example.com', 1, 123, 17)")
        }

        val keyManager = DatabaseKeyManager(context)
        val migrator = DatabaseEncryptionMigrator(context, keyManager)
        migrator.prepareEncryptedDatabase()

        assertTrue(source.exists())
        assertTrue(encrypted.exists())
        assertEquals("encrypted_ready", preferences.getString("state", null))

        CipherDatabase.loadLibs(context)
        val key = keyManager.getOrCreateDatabaseKey()
        try {
            val database = CipherDatabase.openOrCreateDatabase(encrypted, key, null)
            database.use { db ->
                db.rawQuery("SELECT COUNT(*) FROM links", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
                db.rawQuery("SELECT name FROM categories WHERE id = 1", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("شخصي", cursor.getString(0))
                }
            }
        } finally {
            key.fill(0)
        }
    }

    @Test
    fun encryptedDatabaseCannotBeOpenedWithAnIncorrectKey() {
        val source = context.getDatabasePath("spiralcore.db")
        val encrypted = context.getDatabasePath("spiralcore.encrypted.db")
        source.parentFile?.mkdirs()
        source.delete()
        encrypted.delete()
        context.getDatabasePath("spiralcore.encrypted.db.migrating").delete()
        context.getSharedPreferences("database_encryption_state", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("database_key_metadata", Context.MODE_PRIVATE).edit().clear().commit()

        SQLiteDatabase.openOrCreateDatabase(source, null).use { db ->
            db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE links(id INTEGER PRIMARY KEY, url TEXT NOT NULL, title TEXT NOT NULL, categoryId INTEGER NOT NULL, createdAt INTEGER NOT NULL, shellSeed INTEGER NOT NULL)")
            db.execSQL("INSERT INTO categories VALUES(1, 'شخصي', 0)")
        }
        val manager = DatabaseKeyManager(context)
        DatabaseEncryptionMigrator(context, manager).prepareEncryptedDatabase()
        CipherDatabase.loadLibs(context)
        val correctKey = manager.getOrCreateDatabaseKey()
        val wrongKey = ByteArray(32) { (it + 1).toByte() }
        try {
            // First prove the encrypted file opens with its legitimate key.
            CipherDatabase.openOrCreateDatabase(encrypted, correctKey, null).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM sqlite_master", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                }
            }

            var opened = false
            try {
                CipherDatabase.openOrCreateDatabase(encrypted, wrongKey, null).use { db ->
                    db.rawQuery("SELECT COUNT(*) FROM sqlite_master", null).use { cursor ->
                        opened = cursor.moveToFirst()
                    }
                }
            } catch (_: Exception) {
                opened = false
            }
            assertFalse("Wrong database key must not open the encrypted vault", opened)
        } finally {
            correctKey.fill(0)
            wrongKey.fill(0)
        }
    }

    @Test
    fun interruptedMigrationRetainsPlaintextSourceAndRetries() {
        val source = context.getDatabasePath("spiralcore.db")
        val encrypted = context.getDatabasePath("spiralcore.encrypted.db")
        val temporary = File(encrypted.parentFile, "${encrypted.name}.migrating")
        val preferences = context.getSharedPreferences("database_encryption_state", Context.MODE_PRIVATE)

        source.parentFile?.mkdirs()
        source.delete()
        encrypted.delete()
        temporary.delete()
        preferences.edit().clear().putString("state", "encrypting").commit()

        SQLiteDatabase.openOrCreateDatabase(source, null).use { db ->
            db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE links(id INTEGER PRIMARY KEY, url TEXT NOT NULL, title TEXT NOT NULL, categoryId INTEGER NOT NULL, createdAt INTEGER NOT NULL, shellSeed INTEGER NOT NULL)")
            db.execSQL("INSERT INTO categories VALUES(1, 'شخصي', 0)")
            db.execSQL("INSERT INTO links VALUES(1, 'https://resume.example/', 'resume.example', 1, 123, 21)")
        }
        // A partial encrypted destination is intentionally not valid.
        temporary.writeBytes(byteArrayOf(1, 2, 3, 4))

        DatabaseEncryptionMigrator(context, DatabaseKeyManager(context)).prepareEncryptedDatabase()

        assertTrue("Plaintext source must be preserved after recovery", source.exists())
        assertTrue("Encrypted output must be fully verified", encrypted.exists())
        assertEquals("encrypted_ready", preferences.getString("state", null))
    }
}
