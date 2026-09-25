package com.teo.parent.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ReconnectChildScreen(
    onClose: () -> Unit,
    viewModel: ReconnectChildViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.childPaired) {
        if (uiState.childPaired) onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!uiState.confirmed) {
            Text(
                text = "Переподключить телефон ребёнка",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Используйте, если ребёнок каким-то способом удалил приложение и его нужно " +
                    "подключить заново. Текущая привязка телефона будет отвязана — на телефоне ребёнка " +
                    "нужно будет установить приложение (если его ещё нет) и ввести новый код.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = { viewModel.confirm() }, modifier = Modifier.fillMaxWidth()) {
                Text("Отвязать и создать новый код")
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        } else {
            Text(
                text = "Привяжите телефон ребёнка",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Введите этот код в приложении «Nest Kid» на телефоне ребёнка. Код действует 15 минут.",
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
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
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
        }
    }
}
