package com.teo.parent.pin

import com.teo.core.R as CoreR
import com.teo.core.ui.components.NestOnboardingScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.teo.core.model.SecretType
import com.teo.core.ui.SecretInput

/** Full-screen code gate shown on cold start and whenever the app returns from background, so a child with a briefly-unlocked phone can't get into the parent controls. */
@Composable
fun AppLockScreen(viewModel: PinChallengeViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    var showForgotDialog by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize()) {
        NestOnboardingScreen(title = "Nest Parent", subtitle = "Введите код защиты", headerIcon = CoreR.drawable.nest_ic_lock) {
            CodeEntry(
                secretType = uiState.secretType,
                loading = uiState.loading,
                errorMessage = uiState.errorMessage,
                confirmLabel = "Войти",
                onVerify = viewModel::verify,
                onBiometricSuccess = viewModel::unlockWithBiometric,
                autoPromptBiometric = true
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showForgotDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Забыли код?")
            }
        }
    }

    if (showForgotDialog) {
        ForgotCodeDialog(
            loading = uiState.loading,
            errorMessage = uiState.errorMessage,
            onConfirm = viewModel::unlockWithAccountPassword,
            onDismiss = { showForgotDialog = false }
        )
    }
}

/**
 * Code entry of whichever [SecretType] the parent chose, plus — on this phone only, and only
 * when switched on — a fingerprint/face shortcut (BiometricUnlock). The code entry always stays
 * visible: the biometric is an extra way in, never the only one.
 */
@Composable
fun ColumnScope.CodeEntry(
    secretType: SecretType,
    loading: Boolean,
    errorMessage: String?,
    confirmLabel: String,
    onVerify: (String) -> Unit,
    onBiometricSuccess: () -> Unit,
    autoPromptBiometric: Boolean = false
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricEnabled = remember { activity != null && BiometricUnlock.isEnabled(context) }
    var code by remember(secretType) { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (autoPromptBiometric && biometricEnabled && activity != null) {
            BiometricUnlock.prompt(activity, "Вход в Nest Parent", onBiometricSuccess)
        }
    }

    SecretInput(
        type = secretType,
        value = code,
        onValueChange = { code = it },
        label = "Код",
        isError = errorMessage != null,
        onSubmit = { if (code.isNotEmpty()) onVerify(code) },
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
    errorMessage?.let {
        Spacer(Modifier.height(12.dp))
        Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(24.dp))
    if (secretType != SecretType.PATTERN) {
        Button(
            onClick = { onVerify(code) },
            enabled = !loading && code.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(confirmLabel) }
        Spacer(Modifier.height(8.dp))
    }
    if (biometricEnabled && activity != null) {
        OutlinedButton(
            onClick = { BiometricUnlock.prompt(activity, "Подтвердите, что это вы", onBiometricSuccess) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Отпечаток или лицо") }
    }
}

/** Account password instead of the forgotten code — see PinChallengeViewModel.unlockWithAccountPassword. */
@Composable
fun ForgotCodeDialog(
    loading: Boolean,
    errorMessage: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Забыли код защиты?") },
        text = {
            Column {
                Text(
                    "Введите пароль от аккаунта Nest — тот, с которым вы входили по email. После этого задайте новый код.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль от аккаунта") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty() && !loading) {
                Text("Продолжить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
