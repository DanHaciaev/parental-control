package com.teo.child.pin

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.child.admin.DeviceAdminHelper
import com.teo.child.ui.theme.ParentalControlTheme
import dagger.hilt.android.AndroidEntryPoint

/** Launched by ProtectionAccessibilityService when it catches an uninstall attempt against this
 *  app itself, or by MonitorForegroundService's airplane-mode block — a fixed code (see
 *  PinChallengeViewModel) is the only way past either, in person, on this device. [EXTRA_PURPOSE]
 *  selects which happens after a correct code. */
@AndroidEntryPoint
class PinChallengeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val purpose = intent.getStringExtra(EXTRA_PURPOSE) ?: PURPOSE_UNINSTALL
        if (purpose == PURPOSE_AIRPLANE_MODE) {
            // The airplane-mode block is also shown over the lock screen (see
            // OverlayBlockerController's showOverLockScreen kdoc) — without this, tapping "Ввести
            // код защиты" from there would genuinely launch this Activity but leave it stuck behind
            // the keyguard, the same hidden-behind-something problem one layer deeper. Not applied
            // for the uninstall-guard purpose, which is only ever triggered while the phone is
            // already unlocked and in active use (reacting to Settings/PackageInstaller screens).
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
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }
        setContent {
            ParentalControlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PinChallengeScreen(purpose = purpose, onDone = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_PURPOSE = "purpose"
        const val PURPOSE_UNINSTALL = "uninstall"
        const val PURPOSE_AIRPLANE_MODE = "airplane_mode"
    }
}

@Composable
private fun PinChallengeScreen(
    purpose: String,
    onDone: () -> Unit,
    viewModel: PinChallengeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }
    var protectionRemoved by remember { mutableStateOf(false) }
    val isAirplaneModePurpose = purpose == PinChallengeActivity.PURPOSE_AIRPLANE_MODE

    LaunchedEffect(uiState.unlocked) {
        if (uiState.unlocked && isAirplaneModePurpose) viewModel.markAirplaneModePinVerified()
    }

    // Without this, the system back gesture/button just finishes this Activity and reveals
    // whatever dangerous screen it was covering (e.g. Settings' "Deactivate device admin?"
    // confirmation) — confirmed live that it's still fully intact and tappable underneath,
    // letting the child bypass the code entirely by backing out instead of cancelling. Only
    // while still locked: once a correct code has actually been entered, normal back behavior
    // (e.g. leaving the airplane-mode success screen) is fine.
    BackHandler(enabled = !uiState.unlocked) { goHomeThenFinish(context, onDone) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (!uiState.unlocked) {
            Text(
                text = "Введите код защиты",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 32) pin = it },
                label = { Text("Код") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            uiState.errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.verify(pin) },
                enabled = !uiState.loading && pin.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Подтвердить") }
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = { goHomeThenFinish(context, onDone) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Отмена") }
        } else if (isAirplaneModePurpose) {
            Text(
                text = "Код верный",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Теперь выключи авиарежим — потяни шторку сверху экрана и нажми на значок самолёта.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
        } else if (!protectionRemoved) {
            Text(
                text = "Код верный",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Защита от удаления будет снята, и откроется системное окно удаления приложения.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    DeviceAdminHelper.relinquish(context)
                    protectionRemoved = true
                    val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(uninstallIntent)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Удалить приложение") }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Не сейчас") }
        }
    }
}

/** Leaving this screen (cancel button or back) has to actually get off whatever dangerous screen
 *  it was covering, not just reveal it again fully intact underneath — see the BackHandler above
 *  for what this closes. */
private fun goHomeThenFinish(context: Context, onDone: () -> Unit) {
    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(homeIntent)
    onDone()
}
