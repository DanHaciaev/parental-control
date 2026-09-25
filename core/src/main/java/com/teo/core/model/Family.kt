package com.teo.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class CommandType {
    LOCK_NOW, REFRESH_STATUS,
    RING_DEVICE, STOP_RING, SET_RINGER_NORMAL, SET_RINGER_SILENT,
    /** Requires WRITE_SECURE_SETTINGS on the child device — a normal app permission can't grant
     *  itself this, it has to be handed to the app once via `adb shell pm grant ... WRITE_SECURE_SETTINGS`
     *  when the device is set up (see MonitorForegroundService's command handler). Doesn't survive
     *  an uninstall+reinstall of the child app without redoing that step. */
    ENABLE_LOCATION,
    /** Pulled the "Выключить авиарежим" remote button — confirmed live it can't actually work in
     *  the one case it would matter (child has zero connectivity, full airplane mode): the command
     *  itself travels over Firestore, so a device with no network at all can't receive the very
     *  command meant to restore its network. Kept here, unused, purely so Firestore can still
     *  deserialize any family doc whose pendingCommand still has this literal value from testing.
     *  Never sent again. */
    @Deprecated("No longer sent; kept only for Firestore deserialization of old documents")
    DISABLE_AIRPLANE_MODE,
    /** Removed in favor of a local PIN-gated uninstall (see app-child's PinChallengeActivity) —
     *  kept here, unused, purely so Firestore can still deserialize any family doc whose
     *  pendingCommand field still has this literal value from before the change. Never sent again. */
    @Deprecated("No longer sent; kept only for Firestore deserialization of old documents")
    RELEASE_PROTECTION
}
enum class CommandStatus { PENDING, DONE }

data class PendingCommand(
    val type: CommandType = CommandType.REFRESH_STATUS,
    @ServerTimestamp val requestedAt: Date? = null,
    val status: CommandStatus = CommandStatus.PENDING
)

data class Family(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val parentEmail: String = "",
    val parentPhone: String? = null,
    val childUid: String? = null,
    val childName: String? = null,
    val childDeviceName: String? = null,
    val timezone: String = "Europe/Moscow",
    val protectionPinHash: String = "",
    val protectionPinSalt: String = "",
    /** Minutes since midnight; both null = bedtime disabled. */
    val bedtimeStartMinutes: Int? = null,
    val bedtimeEndMinutes: Int? = null,
    /** Daily cap in minutes summed across apps currently tagged TIME_LIMIT; null = disabled. */
    val totalScreenTimeCapMinutes: Int? = null,
    /** Optional per-weekday override for the total cap, keyed by ISO day-of-week ("1"=Monday.."7"=Sunday)
     *  — same convention as [AppRule.weeklyLimitMinutes]. Missing a given day falls back to
     *  [totalScreenTimeCapMinutes]. */
    val weeklyTotalCapMinutes: Map<String, Int>? = null,
    /** Packages that stay reachable during ANY full-device block — a "Блокировать всё" schedule,
     *  or the total daily time cap running out. The app itself and core telecom packages are always
     *  exempt regardless of this list, so a locked-out child can still see why and still take calls. */
    val fullBlockAllowedPackages: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null,
    val pendingCommand: PendingCommand? = null
) {
    fun effectiveTotalCapMinutes(isoDayOfWeek: Int): Int? =
        weeklyTotalCapMinutes?.get(isoDayOfWeek.toString()) ?: totalScreenTimeCapMinutes
}
