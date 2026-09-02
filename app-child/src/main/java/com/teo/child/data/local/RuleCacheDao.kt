package com.teo.child.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleCacheDao {
    @Query("SELECT * FROM rule_cache WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): RuleCacheEntity?

    @Query("SELECT * FROM rule_cache")
    fun observeAll(): Flow<List<RuleCacheEntity>>

    @Query("DELETE FROM rule_cache")
    suspend fun clear()

    @Upsert
    suspend fun upsertAll(entities: List<RuleCacheEntity>)

    /** Firestore is the source of truth for rules — each sync fully replaces the local mirror. */
    @Transaction
    suspend fun replaceAll(entities: List<RuleCacheEntity>) {
        clear()
        upsertAll(entities)
    }
}
