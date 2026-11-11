package com.teo.parent.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import com.teo.core.ui.components.NestStyledCard as Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.ui.displayName
import com.teo.parent.accessibility.AccessibilityPermissionHelper
import com.teo.parent.admin.DeviceAdminHelper
import com.teo.parent.notifications.SosNotifications
import com.teo.parent.protect.ProtectSetupChoice
import com.teo.parent.protect.ProtectSetupScreen

/**
 * "Код защиты и вход": change the protection code (proving it's the parent first — current code,
 * fingerprint, or the account password if the code is forgotten) and switch fingerprint/face
 * unlock on or off for this phone.
 */
@Composable
fun SecuritySettingsScreen(
    onClose: () -> Unit,
    viewModel: PinChallengeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var changing by remember { mutableStateOf(false) }
    var settingUpProtection by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var biometricOn by remember { mutableStateOf(BiometricUnlock.isEnabled(context)) }
    val biometricAvailable = remember { BiometricUnlock.isAvailable(context) }

    when {
        settingUpProtection -> ProtectSetupScreen(
            embedded = true,
            onDone = {
                ProtectSetupChoice.setSkipped(context, false)
                settingUpProtection = false
            },
            onSkip = { settingUpProtection = false }
        )
        changing && uiState.unlocked -> SetPinScreen(
            title = "Новый код защиты",
            embedded = true,
            onSaved = {
                viewModel.codeResetDone()
                changing = false
                viewModel.lock()
            },
            onCancel = {
                changing = false
                viewModel.lock()
            }
        )
        changing -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Сначала введите текущий код", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            CodeEntry(
                secretType = uiState.secretType,
                loading = uiState.loading,
                errorMessage = uiState.errorMessage,
                confirmLabel = "Продолжить",
                onVerify = viewModel::verify,
                onBiometricSuccess = viewModel::unlockWithBiometric
            )
            TextButton(onClick = { showForgotDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Забыли код?") }
            TextButton(onClick = { changing = false; viewModel.lock() }, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Код защиты", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Сейчас: ${uiState.secretType.displayName()}. Новый код сразу начнёт действовать на всех телефонах семьи.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.lock(); changing = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Сменить код защиты")
                    }
                }
            }
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Вход по отпечатку или лицу", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (biometricAvailable) {
                                "Только на этом телефоне. Подойдёт любой отпечаток, сохранённый в телефоне, — не добавляйте туда отпечатки детей. Код защиты продолжит работать как запасной вариант."
                            } else {
                                "Недоступно: на телефоне не настроен отпечаток пальца или разблокировка лицом."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = biometricOn,
                        enabled = biometricAvailable,
                        onCheckedChange = { wanted ->
                            val activity = context as? FragmentActivity
                            if (!wanted) {
                                BiometricUnlock.setEnabled(context, false)
                                biometricOn = false
                            } else if (activity != null) {
                                // Turning it on needs one successful scan, so it's the parent's
                                // finger/face that's enrolled for this, not just anyone's.
                                BiometricUnlock.prompt(activity, "Включить вход по отпечатку") {
                                    BiometricUnlock.setEnabled(context, true)
                                    biometricOn = true
                                }
                            }
                        }
                    )
                }
            }
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val protectedNow = DeviceAdminHelper.isActive(context) && AccessibilityPermissionHelper.isEnabled(context)
                    Text("Защита приложения от удаления", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (protectedNow) "Включена: удалить Nest Parent с этого телефона можно только с кодом защиты."
                        else "Выключена: Nest Parent можно удалить с этого телефона без кода.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    if (!protectedNow) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { settingUpProtection = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Включить защиту")
                        }
                    }
                }
            }
            if (!SosNotifications.canShowFullScreen(context)) {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("SOS на весь экран", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Не разрешено: SOS придёт обычным срочным уведомлением, а не на весь экран.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { SosNotifications.openFullScreenSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Разрешить")
                        }
                    }
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
        }
    }

    if (showForgotDialog) {
        ForgotCodeDialog(
            loading = uiState.loading,
            errorMessage = uiState.errorMessage,
            onConfirm = { password ->
                viewModel.unlockWithAccountPassword(password)
            },
            onDismiss = { showForgotDialog = false }
        )
    }
    LaunchedEffect(uiState.unlocked) {
        if (uiState.unlocked) showForgotDialog = false
    }
}
