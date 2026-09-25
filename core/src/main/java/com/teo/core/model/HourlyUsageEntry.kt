package com.teo.core.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/** Total device usage for one hour of the day (all apps combined) — backs the parent-side hourly bar chart. */
data class HourlyUsageEntry(
    val dateKey: String = "",
    val hour: Int = 0,
    val minutesUsed: Int = 0,
    @ServerTimestamp val lastUpdated: Date? = null
)
