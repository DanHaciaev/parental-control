package com.teo.parent.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isSignUpMode) { viewModel.clearError() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSignUpMode) "Создать аккаунт родителя" else "Вход для родителя",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Вход по email — если поменяете телефон, всё восстановится автоматически.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        uiState.errorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (isSignUpMode) viewModel.signUp(email, password) else viewModel.signIn(email, password)
            },
            enabled = !uiState.loading && email.isNotBlank() && password.length >= 6,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (isSignUpMode) "Создать аккаунт" else "Войти")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!isSignUpMode) {
            TextButton(
                onClick = {
                    viewModel.clearPasswordResetMessage()
                    showResetDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Забыли пароль?") }
        }

        TextButton(
            onClick = { isSignUpMode = !isSignUpMode },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isSignUpMode) "У меня уже есть аккаунт — войти"
                else "Ещё нет аккаунта — зарегистрироваться"
            )
        }
    }

    if (showResetDialog) {
        PasswordResetDialog(
            initialEmail = email,
            loading = uiState.loading,
            message = uiState.passwordResetMessage,
            onSend = viewModel::sendPasswordReset,
            onDismiss = { showResetDialog = false }
        )
    }
}

@Composable
private fun PasswordResetDialog(
    initialEmail: String,
    loading: Boolean,
    message: String?,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var resetEmail by remember { mutableStateOf(initialEmail) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Восстановить пароль") },
        text = {
            Column {
                Text(
                    "Пришлём письмо со ссылкой — по ней можно задать новый пароль. Потом войдите с ним здесь.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(resetEmail) }, enabled = resetEmail.isNotBlank() && !loading) {
                Text(if (message == null) "Отправить" else "Отправить ещё раз")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (message == null) "Отмена" else "Готово") }
        }
    )
}

