package com.teo.child.data.local

import androidx.room.Entity

/**
 * secondsAccumulated holds the sub-minute remainder between polling ticks so short bursts
 * of usage aren't lost to integer-minute rounding; minutesUsedToday is what actually gets
 * compared against the daily limit and uploaded to Firestore.
 */
@Entity(tableName = "usage", primaryKeys = ["dateKey", "packageName"])
data class UsageEntity(
    val dateKey: String,
    val packageName: String,
    val minutesUsedToday: Int = 0,
    val secondsAccumulated: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val uploaded: Boolean = false
)
