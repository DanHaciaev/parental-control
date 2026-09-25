package com.teo.child.data.local

import androidx.room.Entity

/** Total device usage per hour of day (all apps combined) — backs the parent-side hourly bar chart. */
@Entity(tableName = "hourly_usage", primaryKeys = ["dateKey", "hour"])
data class HourlyUsageEntity(
    val dateKey: String,
    val hour: Int,
    val minutesUsed: Int = 0,
    val secondsAccumulated: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val uploaded: Boolean = false
)
