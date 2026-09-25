package com.teo.child.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface HourlyUsageDao {
    @Query("SELECT * FROM hourly_usage WHERE dateKey = :dateKey AND hour = :hour LIMIT 1")
    suspend fun get(dateKey: String, hour: Int): HourlyUsageEntity?

    @Upsert
    suspend fun upsert(entity: HourlyUsageEntity)

    @Query("SELECT * FROM hourly_usage WHERE dateKey = :dateKey ORDER BY hour")
    suspend fun getForDay(dateKey: String): List<HourlyUsageEntity>

    @Query("SELECT * FROM hourly_usage WHERE uploaded = 0")
    suspend fun getPendingUpload(): List<HourlyUsageEntity>

    @Query("UPDATE hourly_usage SET uploaded = 1 WHERE dateKey = :dateKey AND hour = :hour")
    suspend fun markUploaded(dateKey: String, hour: Int)
}
