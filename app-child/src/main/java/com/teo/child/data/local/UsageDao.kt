package com.teo.child.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage WHERE dateKey = :dateKey AND packageName = :packageName LIMIT 1")
    suspend fun get(dateKey: String, packageName: String): UsageEntity?

    @Upsert
    suspend fun upsert(entity: UsageEntity)

    @Query("SELECT * FROM usage WHERE dateKey = :dateKey")
    suspend fun getForDay(dateKey: String): List<UsageEntity>

    @Query("SELECT * FROM usage WHERE dateKey = :dateKey")
    fun observeForDay(dateKey: String): Flow<List<UsageEntity>>

    @Query("SELECT * FROM usage WHERE uploaded = 0")
    suspend fun getPendingUpload(): List<UsageEntity>

    @Query("UPDATE usage SET uploaded = 1 WHERE dateKey = :dateKey AND packageName = :packageName")
    suspend fun markUploaded(dateKey: String, packageName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<UsageEntity>)
}
