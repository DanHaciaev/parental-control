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
    const val DEVICE_STATUS = "deviceStatus"
    const val DEVICE_STATUS_CURRENT_DOC = "current"
    const val LOCATION = "location"
    const val LOCATION_CURRENT_DOC = "current"
    const val GEOFENCES = "geofences"
    const val INSTALLED_APPS = "installedApps"
    const val EVENTS = "events"
    const val REQUESTS = "requests"
    const val TASKS = "tasks"

    fun familyDoc(familyId: String) = "$FAMILIES/$familyId"
    fun ruleDoc(familyId: String, packageName: String) = "$FAMILIES/$familyId/$RULES/$packageName"
    fun usageDailyDoc(familyId: String, dateKey: String, packageName: String) =
        "$FAMILIES/$familyId/$USAGE_DAILY/${dateKey}_$packageName"
    fun installedAppDoc(familyId: String, packageName: String) =
        "$FAMILIES/$familyId/$INSTALLED_APPS/$packageName"
    fun pairingCodeDoc(code: String) = "$PAIRING_CODES/$code"
}
