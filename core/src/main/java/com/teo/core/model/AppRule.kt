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
    val bonusMinutesToday: Int = 0,
    val bonusDateKey: String? = null,
    @ServerTimestamp val updatedAt: Date? = null,
    val createdBy: String = ""
)
