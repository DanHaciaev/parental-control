package com.teo.child.work

import android.app.ActivityManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.teo.child.data.ChildPreferences
import com.teo.child.monitor.MonitorForegroundService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Samsung's own background-kill lists can stop the monitor service outside Doze — this restarts it. */
@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val childPreferences: ChildPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val familyId = childPreferences.familyId.first() ?: return Result.success()
        if (!isServiceRunning()) {
            MonitorForegroundService.start(applicationContext)
        }
        // KEEP policy makes this a no-op if already scheduled — cheap insurance against Samsung
        // silently dropping jobs from the WorkManager queue along with the service.
        WorkScheduler.scheduleAll(applicationContext)
        return Result.success()
    }

    private fun isServiceRunning(): Boolean {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == MonitorForegroundService::class.java.name }
    }
}
