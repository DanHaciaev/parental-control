package com.teo.child.work

import android.app.ActivityManager
import android.content.Context
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.location.LocationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.teo.child.admin.DeviceAdminHelper
import com.teo.child.data.ChildPreferences
import com.teo.child.monitor.MonitorForegroundService
import com.teo.child.permission.AccessibilityPermissionHelper
import com.teo.child.permission.BatteryOptimizationHelper
import com.teo.child.permission.NotificationPolicyPermissionHelper
import com.teo.child.permission.OverlayPermissionHelper
import com.teo.child.permission.UsageAccessHelper
import com.teo.core.model.DeviceStatus
import com.teo.core.repository.DeviceStatusRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Self-health-check the parent can read remotely — the whole reason this exists is that
 * Samsung silently killing the service otherwise looks like an unexplained, undebuggable failure.
 */
@HiltWorker
class DeviceStatusWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val childPreferences: ChildPreferences,
    private val deviceStatusRepository: DeviceStatusRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val familyId = childPreferences.familyId.first() ?: return Result.success()

        return try {
            val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = batteryManager.isCharging

            deviceStatusRepository.updateHealthStatus(
                familyId,
                DeviceStatus(
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                    appVersion = appVersionName(),
                    osVersion = Build.VERSION.RELEASE ?: "?",
                    protectionServiceRunning = isMonitorServiceRunning(),
                    accessibilityEnabled = AccessibilityPermissionHelper.isEnabled(applicationContext),
                    usageAccessEnabled = UsageAccessHelper.hasUsageAccess(applicationContext),
                    overlayEnabled = OverlayPermissionHelper.hasOverlayPermission(applicationContext),
                    deviceAdminActive = DeviceAdminHelper.isActive(applicationContext),
                    batteryOptimizationExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(applicationContext),
                    notificationPolicyAccess = NotificationPolicyPermissionHelper.isGranted(applicationContext),
                    locationServicesEnabled = isLocationServicesEnabled()
                )
            )
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun isLocationServicesEnabled(): Boolean {
        val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return true
        return runCatching { LocationManagerCompat.isLocationEnabled(locationManager) }.getOrDefault(true)
    }

    private fun isMonitorServiceRunning(): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == MonitorForegroundService::class.java.name }
    }

    private fun appVersionName(): String = runCatching {
        applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
    }.getOrNull() ?: "?"
}
