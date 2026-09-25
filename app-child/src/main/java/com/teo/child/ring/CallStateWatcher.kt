package com.teo.child.ring

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

/**
 * Watches call state transitions (ringing/idle) to unmute for any incoming call and restore
 * afterward. Never reads the caller's number — that path (matching specifically the parent's number
 * via a CallScreeningService) was tried and abandoned: Samsung's own InCallUI runs its own
 * call-screening pipeline that wins the race, so a third-party screening service is never actually
 * invoked for real calls on this device. Plain call-state tracking has no such OEM gatekeeping.
 */
class CallStateWatcher(context: Context, private val onRinging: () -> Unit, private val onIdle: () -> Unit) {
    private val appContext = context.applicationContext
    private val telephonyManager = appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var modernCallback: TelephonyCallback? = null
    private var legacyListener: PhoneStateListener? = null

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleState(state)
            }
            modernCallback = callback
            runCatching { telephonyManager.registerTelephonyCallback(appContext.mainExecutor, callback) }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java", ReplaceWith(""))
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = handleState(state)
            }
            legacyListener = listener
            @Suppress("DEPRECATION")
            runCatching { telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE) }
        }
    }

    private fun handleState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> onRinging()
            TelephonyManager.CALL_STATE_IDLE -> onIdle()
        }
    }

    fun stop() {
        modernCallback?.let { runCatching { telephonyManager.unregisterTelephonyCallback(it) } }
        modernCallback = null
        legacyListener?.let {
            @Suppress("DEPRECATION")
            runCatching { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        legacyListener = null
    }
}
