package com.teo.child.sos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.teo.child.data.ChildPreferences
import com.teo.core.model.EventLogEntry
import com.teo.core.model.EventType
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs the SOS event and places the call to the parent — shared by the in-app SOS button
 * (HomeScreen, which additionally handles the runtime permission prompt via its own launcher)
 * and the hardware volume-button combo (ProtectionAccessibilityService, which can only check
 * an already-granted permission — a background service can't prompt for one).
 */
@Singleton
class SosTrigger @Inject constructor(
    private val childPreferences: ChildPreferences,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository
) {
    suspend fun trigger(context: Context, message: String = "Ребёнок нажал кнопку SOS") {
        val familyId = childPreferences.familyId.first() ?: return

        runCatching {
            eventRepository.logEvent(familyId, EventLogEntry(type = EventType.SOS, message = message))
        }

        // The volume-button path has no on-screen confirmation like the in-app button's text change —
        // this is the only feedback the child gets that it actually worked.
        runCatching { vibrate(context) }

        val phone = runCatching { familyRepository.getFamily(familyId)?.parentPhone }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return

        val hasCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$phone")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun vibrate(context: Context) {
        // VIBRATOR_MANAGER_SERVICE only exists from API 31 — this app's minSdk is 26.
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 200), -1))
    }
}
