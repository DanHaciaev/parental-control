package com.teo.child.monitor

import com.teo.child.block.LockScreenBlockActivity
import com.teo.child.block.LockScreenBlockController
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks whether the parent's protection PIN has been entered to get past the current
 *  airplane-mode block (see MonitorForegroundService's airplane-mode broadcast receiver) — a
 *  one-shot grant for this ON-period only. Turning airplane mode off clears it, so turning it
 *  back on later requires the PIN again too, same as if it had never been entered. */
@Singleton
class AirplaneModeGuard @Inject constructor(
    private val overlayController: OverlayBlockerController,
    private val lockScreenBlockController: LockScreenBlockController
) {
    @Volatile private var pinVerified = false

    fun markPinVerified() {
        pinVerified = true
        overlayController.hide()
        // PIN entry itself is reached by tapping through from LockScreenBlockActivity when the
        // device was locked (see that class's kdoc) — without this it would sit stale behind the
        // now-finished PinChallengeActivity once the child backs out of it.
        if (lockScreenBlockController.isShowing(LockScreenBlockActivity.KIND_AIRPLANE_MODE)) {
            lockScreenBlockController.dismiss()
        }
    }

    fun shouldBlock(isAirplaneModeOn: Boolean): Boolean = isAirplaneModeOn && !pinVerified

    fun onAirplaneModeChanged(isOn: Boolean) {
        if (!isOn) pinVerified = false
    }
}
