package com.teo.parent.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    /** Pairing a further child while others are already set up — offers a way back instead of
     *  the second-parent option, which only makes sense for a brand-new account. */
    isAdditionalChild: Boolean = false,
    onCancel: () -> Unit = {},
    onJoinAsParent: (String) -> Unit = {},
    joinLoading: Boolean = false,
    joinError: String? = null,
    onClearJoinError: () -> Unit = {},
    viewModel: PairingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.childPaired) {
        if (uiState.childPaired) onPaired()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isAdditionalChild) "Привяжите телефон ещё одного ребёнка" else "Привяжите телефон ребёнка",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Установите приложение «Nest Kid» на телефон ребёнка и введите там этот код. Код действует 15 минут.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(32.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.loading && uiState.code == null) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = uiState.code?.let { it.take(3) + " " + it.drop(3) } ?: "——— ———",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                }
            }
        }

        uiState.errorMessage?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(onClick = { viewModel.generateCode() }, modifier = Modifier.fillMaxWidth()) {
            Text("Сгенерировать новый код")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Ждём подключение ребёнка…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(24.dp))
        if (isAdditionalChild) {
            TextButton(onClick = onCancel) { Text("Отмена") }
        } else {
            TextButton(onClick = { onClearJoinError(); showJoinDialog = true }) {
                Text("Я второй родитель — ввести код приглашения")
            }
        }
    }

    if (showJoinDialog) {
        JoinAsParentDialog(
            loading = joinLoading,
            error = joinError,
            onJoin = onJoinAsParent,
            onDismiss = { showJoinDialog = false }
        )
    }
}

@Composable
private fun JoinAsParentDialog(
    loading: Boolean,
    error: String?,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Присоединиться к семье") },
        text = {
            Column {
                Text(
                    "Попросите другого родителя открыть «Ещё» → «Пригласить второго родителя» и введите показанный код.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { value -> code = value.filter { it.isDigit() }.take(6) },
                    label = { Text("Код из 6 цифр") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onJoin(code) }, enabled = code.length == 6 && !loading) {
                if (loading) CircularProgressIndicator() else Text("Присоединиться")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
