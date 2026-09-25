package com.teo.child.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import java.util.concurrent.TimeUnit

object WorkScheduler {

    /** All periodic jobs the child device relies on — call this everywhere protection can start (app open, boot, watchdog). */
    fun scheduleAll(context: Context) {
        scheduleUsageUpload(context)
        scheduleHourlyUsageUpload(context)
        scheduleServiceWatchdog(context)
        scheduleLocationUpload(context)
        scheduleDeviceStatus(context)
    }

    /** One-off immediate run so the parent sees fresh data right after pairing/app-open instead of waiting up to 15 min. */
    fun kickImmediate(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val wm = WorkManager.getInstance(context)
        wm.enqueue(OneTimeWorkRequestBuilder<LocationUploadWorker>().setConstraints(constraints).build())
        wm.enqueue(OneTimeWorkRequestBuilder<UsageUploadWorker>().setConstraints(constraints).build())
        wm.enqueue(OneTimeWorkRequestBuilder<HourlyUsageUploadWorker>().setConstraints(constraints).build())
        wm.enqueue(OneTimeWorkRequestBuilder<DeviceStatusWorker>().setConstraints(constraints).build())
    }

    fun scheduleUsageUpload(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageUploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "usage_upload", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun scheduleHourlyUsageUpload(context: Context) {
        val request = PeriodicWorkRequestBuilder<HourlyUsageUploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "hourly_usage_upload", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun scheduleServiceWatchdog(context: Context) {
        val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "service_watchdog", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun scheduleLocationUpload(context: Context) {
        val request = PeriodicWorkRequestBuilder<LocationUploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "location_upload", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun scheduleDeviceStatus(context: Context) {
        val request = PeriodicWorkRequestBuilder<DeviceStatusWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "device_status", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}
