package com.teo.child.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.teo.child.data.ChildPreferences
import com.teo.core.model.EventLogEntry
import com.teo.core.model.EventType
import com.teo.core.repository.EventRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProtectionDeviceAdminReceiver : DeviceAdminReceiver() {

    @Inject lateinit var childPreferences: ChildPreferences
    @Inject lateinit var eventRepository: EventRepository

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Это приложение защищено родительским контролем и не должно отключаться без разрешения родителя."

    /** Fires the instant Android actually revokes admin — by this point deactivation has already
     *  gone through (this warning is the OS's only gate, via onDisableRequested above; once the
     *  user confirms it, no app can prevent or undo it). Logging here rather than waiting for
     *  HomeViewModel.onProtectionTamperedLocally (which only notices on the app's own next launch)
     *  is what makes this actually timely — a child who disables protection on purpose has every
     *  reason not to reopen the monitoring app right afterward. */
    override fun onDisabled(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val familyId = childPreferences.familyId.first() ?: return@launch
                runCatching {
                    eventRepository.logEvent(
                        familyId,
                        EventLogEntry(
                            type = EventType.PROTECTION_TAMPERED,
                            message = "Защита была отключена на телефоне ребёнка (Device Admin выключен вручную)"
                        )
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, ProtectionDeviceAdminReceiver::class.java)
    }
}
