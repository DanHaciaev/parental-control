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

    /** Sums minutes only across apps currently tagged TIME_LIMIT — backs the total screen-time cap. */
    @Query(
        """
        SELECT SUM(u.minutesUsedToday) FROM usage u
        INNER JOIN rule_cache r ON u.packageName = r.packageName
        WHERE u.dateKey = :dateKey AND r.mode = 'TIME_LIMIT'
        """
    )
    suspend fun getTotalTimedMinutesForDay(dateKey: String): Int?

    @Query("UPDATE usage SET uploaded = 1 WHERE dateKey = :dateKey AND packageName = :packageName")
    suspend fun markUploaded(dateKey: String, packageName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<UsageEntity>)
}
