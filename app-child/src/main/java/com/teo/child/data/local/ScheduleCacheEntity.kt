package com.teo.child.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local mirror of Firestore schedules/{id} — read by the poll loop, which must never block on network. */
@Entity(tableName = "schedule_cache")
data class ScheduleCacheEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val blockAllApps: Boolean,
    /** Comma-joined package names; empty string when none — no @TypeConverter, matching this codebase's flat style. */
    val blockedPackageNamesCsv: String,
    /** Comma-joined ISO day-of-week ints (1=Monday..7=Sunday); empty string = every day, same
     *  convention/flat style as [blockedPackageNamesCsv]. */
    val daysOfWeekCsv: String,
    val enabled: Boolean
)
