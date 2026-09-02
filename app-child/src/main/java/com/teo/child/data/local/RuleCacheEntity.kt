package com.teo.child.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local mirror of Firestore rules/{packageName} — read by the poll loop, which must never block on network. */
@Entity(tableName = "rule_cache")
data class RuleCacheEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val mode: String,
    val dailyLimitMinutes: Int?,
    val bonusMinutesToday: Int,
    val bonusDateKey: String?
)
