package com.teo.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class EventType {
    NEW_INSTALL, LIMIT_REACHED, UNINSTALL_ATTEMPT_BLOCKED, LOW_BATTERY, SOS, GEOFENCE, PROTECTION_TAMPERED,
    LOCATION_DISABLED,
    /** Fired the instant the child taps "Установить" in Google Play — well before the app actually
     *  finishes installing (ACTION_PACKAGE_ADDED, which is what NEW_INSTALL is tied to). We can't
     *  stop the download itself (would need Device Owner/MDM), but the parent gets an immediate
     *  heads-up while it's happening instead of finding out only once it's already on the phone. */
    INSTALL_ATTEMPT,
    /** Fired the instant airplane mode is switched on — a race against the radio actually shutting
     *  off a moment later, so this is best-effort, but it's the only way the parent finds out at
     *  all: once the radio is off, there's nothing left to report anything with, and the device
     *  goes dark exactly like a lost/switched-off phone until airplane mode is turned back off. */
    AIRPLANE_MODE_ENABLED
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

/** APPROVED apps were either already installed when monitoring first started (grandfathered in —
 *  a fresh setup shouldn't lock the child out of their existing apps) or have since been explicitly
 *  allowed by the parent. PENDING/DENIED apps are blocked from opening on the child device until
 *  the parent acts — see MonitorForegroundService's pendingApprovalPackages check. */
enum class InstallApprovalStatus { APPROVED, PENDING, DENIED }

data class InstalledApp(
    @DocumentId val packageName: String = "",
    val appLabel: String = "",
    @ServerTimestamp val firstSeenAt: Date? = null,
    val isSystemApp: Boolean = false,
    /** The app's real launcher icon, captured on the child's device (where it's actually
     *  installed) and embedded directly here as a small PNG — same no-Storage-needed trick used
     *  for task photos. Null for apps recorded before this existed, until the next backfill pass. */
    val iconBase64: String? = null,
    val approvalStatus: String = InstallApprovalStatus.APPROVED.name
)
