package com.teo.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A non-phone device paired to the family — currently just the Windows laptop client. Sibling to
 * [Family.childUid], not a replacement: the phone keeps using the single-slot childUid claim, and
 * each additional device gets its own doc here, keyed by its own anonymous auth uid (same
 * doc-id-doubles-as-auth-uid convention as childUid).
 *
 * There is deliberately no "unlock"/persistent-lock-state field: LockWorkStation() only returns
 * the OS to its lock screen, it doesn't block re-login, so enforcement on the Windows side is a
 * repeating action (re-lock whenever the session comes back unlocked while still over budget),
 * not a state this doc needs to track. Raising [dailyLimitMinutes] or the date rolling over
 * (which the device itself resets [todayMinutesUsed] for) is what actually ends it.
 */
data class Device(
    @DocumentId val id: String = "",
    val type: String = "windows",
    val label: String = "",
    val dailyLimitMinutes: Int? = null,
    @ServerTimestamp val createdAt: Date? = null,
    val todayDateKey: String = "",
    val todayMinutesUsed: Int = 0,
    @ServerTimestamp val lastSeenAt: Date? = null,
    val pendingCommand: PendingCommand? = null
)
