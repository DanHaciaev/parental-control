package com.teo.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A named time window during which either all apps, or a parent-chosen subset, are blocked.
 * Replaces the old single Family.bedtimeStartMinutes/EndMinutes pair — see
 * ScheduleRepository.migrateLegacyBedtimeIfNeeded for the one-time migration.
 */
data class Schedule(
    @DocumentId val id: String = "",
    val name: String = "",
    val startMinutes: Int = 0,
    val endMinutes: Int = 0,
    val blockAllApps: Boolean = true,
    /** Only meaningful when blockAllApps == false. */
    val blockedPackageNames: List<String> = emptyList(),
    /** ISO day-of-week, 1=Monday..7=Sunday — same convention as Family.weeklyTotalCapMinutes'
     *  keys. Defaults to every day (both for new schedules and for deserializing older documents
     *  saved before this field existed, so nothing already-scheduled silently stops running). */
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val enabled: Boolean = true,
    @ServerTimestamp val updatedAt: Date? = null,
    val createdBy: String = ""
)
