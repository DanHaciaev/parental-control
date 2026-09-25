package com.teo.core

/**
 * Single source of truth for every Firestore collection/document path.
 * Keeps :app-parent and :app-child from drifting on schema string literals.
 */
object FirestorePaths {
    const val FAMILIES = "families"
    const val PAIRING_CODES = "pairingCodes"

    const val RULES = "rules"
    const val USAGE_DAILY = "usageDaily"
    const val USAGE_HOURLY = "usageHourly"
    const val DEVICE_STATUS = "deviceStatus"
    const val DEVICE_STATUS_CURRENT_DOC = "current"
    const val LOCATION = "location"
    const val LOCATION_CURRENT_DOC = "current"
    const val GEOFENCES = "geofences"
    const val INSTALLED_APPS = "installedApps"
    const val EVENTS = "events"
    const val REQUESTS = "requests"
    const val TASKS = "tasks"
    const val ASSIGNED_SKILLS = "assignedSkills"
    const val SKILL_PROGRESS = "skillProgress"
    const val SCHEDULES = "schedules"
    const val HISTORY = "history"
    const val DEVICES = "devices"
    const val LISTEN_SESSION = "listenSession"
    const val LISTEN_SESSION_CURRENT_DOC = "current"
    const val CALLER_ICE_CANDIDATES = "callerCandidates"
    const val CALLEE_ICE_CANDIDATES = "calleeCandidates"

    fun familyDoc(familyId: String) = "$FAMILIES/$familyId"
    fun deviceDoc(familyId: String, deviceId: String) = "$FAMILIES/$familyId/$DEVICES/$deviceId"
    fun listenSessionDoc(familyId: String) =
        "$FAMILIES/$familyId/$LISTEN_SESSION/$LISTEN_SESSION_CURRENT_DOC"
    fun ruleDoc(familyId: String, packageName: String) = "$FAMILIES/$familyId/$RULES/$packageName"
    fun usageDailyDoc(familyId: String, dateKey: String, packageName: String) =
        "$FAMILIES/$familyId/$USAGE_DAILY/${dateKey}_$packageName"
    fun usageHourlyDoc(familyId: String, dateKey: String, hour: Int) =
        "$FAMILIES/$familyId/$USAGE_HOURLY/${dateKey}_${hour.toString().padStart(2, '0')}"
    fun installedAppDoc(familyId: String, packageName: String) =
        "$FAMILIES/$familyId/$INSTALLED_APPS/$packageName"
    fun pairingCodeDoc(code: String) = "$PAIRING_CODES/$code"
}
