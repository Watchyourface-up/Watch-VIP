package com.spiralcore.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LinkEntity::class, CategoryEntity::class],
    version = 1,
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
                ).build().also { instance = it }
            }
    }
}
