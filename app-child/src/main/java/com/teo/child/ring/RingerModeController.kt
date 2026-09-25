package com.teo.child.ring

import android.content.Context
import android.media.AudioManager
import com.teo.child.permission.NotificationPolicyPermissionHelper

/** Persistent ringer-mode override. Requires "Do Not Disturb access" — no-ops gracefully if not granted. */
object RingerModeController {

    fun setNormal(context: Context) = setMode(context, AudioManager.RINGER_MODE_NORMAL)

    fun setSilent(context: Context) = setMode(context, AudioManager.RINGER_MODE_SILENT)

    private var previousMode: Int? = null
    private var previousRingVolume: Int? = null

    /**
     * Temporarily forces ringer-normal + max ring volume while a call is ringing, even if the child
     * left the phone silent/vibrate — restore with [restoreAfterIncomingCall] once it ends.
     *
     * Originally scoped to calls recognized as specifically the parent's via a CallScreeningService,
     * but that proved unreliable in practice: Samsung's own InCallUI runs its own call-screening
     * pipeline that wins the race and answers the filtering, so a third-party screening service placed
     * that role for is never actually invoked for real calls on this device — confirmed via Telecom's
     * own logs showing our filter completing in ~1ms with no bind/onScreenCall, while Samsung's did the
     * real work. So this now applies to every incoming call, not just the parent's — the tradeoff being
     * that unmute happens for any caller, not only the parent.
     */
    fun boostForIncomingCall(context: Context) {
        if (!NotificationPolicyPermissionHelper.isGranted(context)) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (previousMode == null) previousMode = audioManager.ringerMode
        if (previousRingVolume == null) previousRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        runCatching { audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL }
        runCatching {
            audioManager.setStreamVolume(
                AudioManager.STREAM_RING, audioManager.getStreamMaxVolume(AudioManager.STREAM_RING), 0
            )
        }
    }

    fun restoreAfterIncomingCall(context: Context) {
        val mode = previousMode ?: return
        previousMode = null
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { audioManager.ringerMode = mode }
        previousRingVolume?.let { volume ->
            runCatching { audioManager.setStreamVolume(AudioManager.STREAM_RING, volume, 0) }
        }
        previousRingVolume = null
    }

    private fun setMode(context: Context, mode: Int) {
        if (!NotificationPolicyPermissionHelper.isGranted(context)) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { audioManager.ringerMode = mode }
    }
}
