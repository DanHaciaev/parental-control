package com.teo.child.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the single currently-showing [LockScreenBlockActivity] instance (if any) so
 * MonitorForegroundService/AirplaneModeGuard can avoid relaunching it every ~3s tick while a block
 * condition holds, and can dismiss it the instant that condition resolves — the Activity has no
 * other way to know the condition it was launched for is gone, since it's not polling anything
 * itself.
 *
 * Also owns the screen-off wake-back-on guard (see [registerScreenOffGuard]) — this was originally
 * on the Activity itself, registered in onResume/unregistered in onPause, but confirmed live that
 * doesn't work: pressing power triggers onPause at roughly the same moment the screen actually
 * turns off, and losing that race meant the receiver was already unregistered before
 * ACTION_SCREEN_OFF ever reached it. Application-context-scoped here instead, active for the whole
 * time a block is showing regardless of the Activity's own lifecycle churn.
 */
@Singleton
class LockScreenBlockController @Inject constructor(@ApplicationContext private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    @Volatile private var activity: LockScreenBlockActivity? = null
    @Volatile private var currentKind: String? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    fun notifyShowing(activity: LockScreenBlockActivity, kind: String) {
        this.activity = activity
        this.currentKind = kind
        registerScreenOffGuard()
    }

    fun notifyDestroyed(activity: LockScreenBlockActivity) {
        if (this.activity == activity) {
            this.activity = null
            this.currentKind = null
            unregisterScreenOffGuard()
        }
    }

    fun isShowing(kind: String): Boolean = activity != null && currentKind == kind

    fun dismiss() {
        activity?.finish()
    }

    /** There's no public API to block the power button/lock outright (needs Device Owner, already
     *  ruled out for this app) — the closest available approximation: the child can still
     *  physically lock the screen for a moment, but the instant it turns off while a block is
     *  showing, briefly force it back on. [LockScreenBlockActivity]'s own showWhenLocked/
     *  turnScreenOn flags then put the same block right back in front, same "keep nagging, don't
     *  let them just wait it out" intent as the rest of this app's blocking. */
    private fun registerScreenOffGuard() {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (activity == null) return
                runCatching {
                    @Suppress("DEPRECATION")
                    val wakeLock = powerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                        "teo:lockScreenBlockWake"
                    )
                    wakeLock.acquire(3_000L)
                    wakeLock.release()
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        screenOffReceiver = receiver
    }

    private fun unregisterScreenOffGuard() {
        screenOffReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        screenOffReceiver = null
    }
}
