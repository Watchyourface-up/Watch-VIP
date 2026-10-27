package com.spiralcore.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [LinkEntity::class, CategoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SpiralDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao

    companion object {
        private const val ENCRYPTED_DATABASE_NAME = "spiralcore.encrypted.db"

        @Volatile
        private var instance: SpiralDatabase? = null

        fun getInstance(context: Context): SpiralDatabase {
            val appContext = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: buildEncryptedDatabase(appContext).also {
                    instance = it
                }
            }
        }

        private fun buildEncryptedDatabase(context: Context): SpiralDatabase {
            val keyManager = DatabaseKeyManager(context)
            val migrator = DatabaseEncryptionMigrator(context, keyManager)
            migrator.prepareEncryptedDatabase()
            val key = keyManager.getOrCreateDatabaseKey()
            try {
                // sqlcipher-android 4.19 requires explicit loading of its bundled native library.
                System.loadLibrary("sqlcipher")
                val database = Room.databaseBuilder(
                    context,
                    SpiralDatabase::class.java,
                    ENCRYPTED_DATABASE_NAME,
                )
                    .openHelperFactory(SupportOpenHelperFactory(key.copyOf(), null, false))
                    .addMigrations(MIGRATION_1_2)
                    .build()

                // Force Room to open and migrate the encrypted database before marking the
                // plaintext source eligible for cleanup. Close the cursor deterministically.
                database.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master").use { cursor ->
                    check(cursor.moveToFirst()) { "Encrypted Room schema could not be read" }
                }
                migrator.markEncryptedDatabaseOpened()
                return database
            } finally {
                key.fill(0)
            }
        }

        /**
         * Preserve the oldest row and the earliest createdAt for duplicate URLs during the
         * legacy plaintext Room v1 to v2 migration. The database encryption conversion happens
         * before Room opens this file and uses a separate encrypted destination.
         */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    UPDATE links
                    SET createdAt = (
                        SELECT MIN(duplicate.createdAt)
                        FROM links AS duplicate
                        WHERE duplicate.url = links.url
                    )
                    WHERE id = (
                        SELECT MIN(keeper.id)
                        FROM links AS keeper
                        WHERE keeper.url = links.url
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    DELETE FROM links
                    WHERE id NOT IN (
                        SELECT MIN(id)
                        FROM links
                        GROUP BY url
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_links_url ON links(url)",
                )
            }
        }
    }
}
