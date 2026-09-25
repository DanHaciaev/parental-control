package com.teo.child.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleCacheDao {
    @Query("SELECT * FROM schedule_cache")
    fun observeAll(): Flow<List<ScheduleCacheEntity>>

    @Query("DELETE FROM schedule_cache")
    suspend fun clear()

    @Upsert
    suspend fun upsertAll(entities: List<ScheduleCacheEntity>)

    /** Firestore is the source of truth for schedules — each sync fully replaces the local mirror. */
    @Transaction
    suspend fun replaceAll(entities: List<ScheduleCacheEntity>) {
        clear()
        upsertAll(entities)
    }
}
