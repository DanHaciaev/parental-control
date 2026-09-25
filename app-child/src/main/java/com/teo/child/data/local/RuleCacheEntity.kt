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
    /** 7 comma-joined minute values (Mon..Sun); null when the rule has no per-weekday override —
     *  no @TypeConverter, matching this codebase's flat CSV style (see ScheduleCacheEntity). */
    val weeklyLimitMinutesCsv: String?,
    val bonusMinutesToday: Int,
    val bonusDateKey: String?
)
