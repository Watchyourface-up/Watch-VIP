package com.spiralcore.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LinkEntity::class, CategoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SpiralDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao

    companion object {
        @Volatile
        private var instance: SpiralDatabase? = null

        fun getInstance(context: Context): SpiralDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpiralDatabase::class.java,
                    "spiralcore.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "DELETE FROM links WHERE id NOT IN (SELECT MIN(id) FROM links GROUP BY url)",
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_links_url ON links(url)")
            }
        }
    }
}
