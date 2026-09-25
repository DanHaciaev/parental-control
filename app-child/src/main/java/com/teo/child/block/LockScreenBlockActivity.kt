package com.teo.child.block

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teo.child.pin.PinChallengeActivity
import com.teo.child.ui.theme.ParentalControlTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A genuine Activity (unlike every other block screen, which is a raw [com.teo.child.monitor.OverlayBlockerController]
 * WindowManager overlay) — confirmed live that a plain overlay window is hard-blocked by Android
 * from rendering above the secure lock screen no matter what flags it's given (see that class's
 * kdoc). Only an Activity using [setShowWhenLocked] gets that privilege.
 *
 * Exists specifically for the airplane-mode and location-disabled blocks: a child could otherwise
 * turn on airplane mode or disable location, lock the screen (or just leave it locked), and never
 * see any consequence — the regular overlay would still be sitting there, just invisible behind
 * the keyguard, so nothing ever visibly demands the parent's PIN and monitoring stays broken
 * indefinitely. Launched by [com.teo.child.monitor.MonitorForegroundService] *alongside* the
 * regular overlay (which still handles the already-unlocked case as before); this one only
 * matters when the device is actually locked, and is a harmless no-op sitting behind the overlay
 * otherwise, since [android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] always
 * renders above a regular Activity window.
 *
 * The screen-off/wake-back-on guard for this lives in [LockScreenBlockController], not here —
 * confirmed live that registering it in onResume/unregistering in onPause loses a race against
 * the power button: onPause fires at roughly the same moment the screen actually turns off, so the
 * receiver could already be gone before ACTION_SCREEN_OFF ever reached it.
 */
@AndroidEntryPoint
class LockScreenBlockActivity : ComponentActivity() {

    @Inject lateinit var controller: LockScreenBlockController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_AIRPLANE_MODE
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)

        controller.notifyShowing(this, kind)

        setContent {
            ParentalControlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LockScreenBlockScreen(
                        title = title,
                        reason = reason,
                        actionLabel = if (kind == KIND_AIRPLANE_MODE) "Ввести код защиты" else "Открыть настройки",
                        onAction = {
                            if (kind == KIND_AIRPLANE_MODE) {
                                startActivity(
                                    Intent(this, PinChallengeActivity::class.java).apply {
                                        putExtra(PinChallengeActivity.EXTRA_PURPOSE, PinChallengeActivity.PURPOSE_AIRPLANE_MODE)
                                    }
                                )
                            } else {
                                startActivity(
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        controller.notifyDestroyed(this)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_REASON = "reason"
        const val KIND_AIRPLANE_MODE = "airplane_mode"
        const val KIND_LOCATION_DISABLED = "location_disabled"
    }
}

@Composable
private fun LockScreenBlockScreen(title: String, reason: String, actionLabel: String, onAction: () -> Unit) {
    // Swallows back presses the same way OverlayBlockerController's overlay does — this block
    // can't be dismissed by navigating away.
    BackHandler(enabled = true) {}
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text(text = reason, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
    }
}
