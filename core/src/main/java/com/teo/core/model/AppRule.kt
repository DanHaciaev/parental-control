package com.teo.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class RuleMode { BLOCKED, TIME_LIMIT }

data class AppRule(
    @DocumentId val packageName: String = "",
    val appLabel: String = "",
    val mode: RuleMode = RuleMode.TIME_LIMIT,
    val dailyLimitMinutes: Int? = null,
    /** Optional per-weekday override, keyed by ISO day-of-week ("1"=Monday.."7"=Sunday) — Firestore
     *  map keys must be strings. Null (or missing a given day) falls back to [dailyLimitMinutes],
     *  which keeps every rule written before this field existed behaving exactly as before. */
    val weeklyLimitMinutes: Map<String, Int>? = null,
    val bonusMinutesToday: Int = 0,
    val bonusDateKey: String? = null,
    @ServerTimestamp val updatedAt: Date? = null,
    val createdBy: String = ""
) {
    fun effectiveLimitMinutes(isoDayOfWeek: Int): Int? =
        weeklyLimitMinutes?.get(isoDayOfWeek.toString()) ?: dailyLimitMinutes
}
