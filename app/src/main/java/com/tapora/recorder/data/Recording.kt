package com.tapora.recorder.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey val id: String,
    val title: String,
    val path: String,
    val createdAt: Long,
    val durationMs: Long,
    val fileSizeBytes: Long = 0L,
    val isComplete: Boolean = true
)

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings WHERE isComplete = 1 ORDER BY createdAt DESC") fun observeAll(): Flow<List<Recording>>
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC") suspend fun all(): List<Recording>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(recording: Recording)
    @Query("UPDATE recordings SET title = :title WHERE id = :id") suspend fun rename(id: String, title: String)
    @Query("DELETE FROM recordings WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM recordings WHERE id = :id") suspend fun deleteMissing(id: String) = delete(id)
}

@Database(entities = [Recording::class], version = 2, exportSchema = false)
abstract class TaporaDatabase : RoomDatabase() {
    abstract fun recordings(): RecordingDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN fileSizeBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recordings ADD COLUMN isComplete INTEGER NOT NULL DEFAULT 1")
            }
        }
        @Volatile private var instance: TaporaDatabase? = null
        fun get(context: Context): TaporaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TaporaDatabase::class.java, "tapora.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
