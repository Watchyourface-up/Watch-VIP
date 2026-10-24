package com.spiralcore.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Transaction
    @Query("SELECT * FROM links ORDER BY createdAt DESC, id DESC")
    fun observeLinks(): Flow<List<LinkWithCategory>>

    @Query("SELECT * FROM categories ORDER BY position ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM links WHERE shellSeed = :seed)")
    suspend fun shellSeedExists(seed: Int): Boolean

    @Insert
    suspend fun insertLink(link: LinkEntity): Long
}
