package com.teo.child.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.teo.child.data.ChildPreferences
import com.teo.child.monitor.MonitorForegroundService
import com.teo.child.work.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var childPreferences: ChildPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (childPreferences.familyId.first() != null) {
                    MonitorForegroundService.start(context)
                    WorkScheduler.scheduleAll(context)
                    WorkScheduler.kickImmediate(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
