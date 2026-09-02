package com.teo.child.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.teo.child.admin.DeviceAdminHelper

fun hasNotificationPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

fun hasAllChildPermissions(context: Context): Boolean =
    UsageAccessHelper.hasUsageAccess(context) &&
        OverlayPermissionHelper.hasOverlayPermission(context) &&
        hasNotificationPermission(context) &&
        BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context) &&
        DeviceAdminHelper.isActive(context) &&
        AccessibilityPermissionHelper.isEnabled(context) &&
        LocationPermissionHelper.hasForegroundLocation(context) &&
        LocationPermissionHelper.hasBackgroundLocation(context)
