package com.teo.parent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.teo.parent.nav.ParentNavHost
import com.teo.parent.notifications.EventNotifications
import com.teo.parent.ui.theme.ParentalControlTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // MainActivity is singleTask (manifest) so tapping a notification while the app is already
    // running delivers here via onNewIntent instead of spawning a second instance — this state is
    // what actually carries the deep-link across that.
    private val openInstallApprovals = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openInstallApprovals.value = consumeDeepLink(intent)
        setContent {
            ParentalControlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ParentNavHost(openInstallApprovals = openInstallApprovals)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openInstallApprovals.value = consumeDeepLink(intent)
    }

    private fun consumeDeepLink(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EventNotifications.EXTRA_OPEN_INSTALL_APPROVALS, false) == true
}
