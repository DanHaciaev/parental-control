package com.teo.core.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Self-diagnostic snapshot the child device writes about its own health,
 * surfaced in the parent app so a broken permission/service on Samsung
 * doesn't look like a silent, unexplained failure.
 */
data class DeviceStatus(
    @ServerTimestamp val lastSeenAt: Date? = null,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val appVersion: String = "",
    val osVersion: String = "",
    val protectionServiceRunning: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val usageAccessEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val deviceAdminActive: Boolean = false,
    val batteryOptimizationExempt: Boolean = false,
    val notificationPolicyAccess: Boolean = false,
    val locationServicesEnabled: Boolean = true,
    val currentForegroundApp: String? = null,
    val currentForegroundAppLabel: String? = null
)
