package com.teo.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class EventType {
    NEW_INSTALL, LIMIT_REACHED, UNINSTALL_ATTEMPT_BLOCKED, LOW_BATTERY, SOS, GEOFENCE
}

data class EventLogEntry(
    @DocumentId val id: String = "",
    val type: EventType = EventType.NEW_INSTALL,
    val packageName: String? = null,
    val message: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    val read: Boolean = false
)

enum class RequestStatus { PENDING, APPROVED, DENIED }

data class MoreTimeRequest(
    @DocumentId val id: String = "",
    val packageName: String = "",
    @ServerTimestamp val requestedAt: Date? = null,
    val status: RequestStatus = RequestStatus.PENDING,
    val resolvedMinutes: Int? = null
)

data class InstalledApp(
    @DocumentId val packageName: String = "",
    val appLabel: String = "",
    @ServerTimestamp val firstSeenAt: Date? = null,
    val isSystemApp: Boolean = false
)
