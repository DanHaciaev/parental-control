package com.teo.parent.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teo.core.model.Device
import com.teo.parent.R
import com.teo.parent.dashboard.EmojiIcon

@Composable
fun DeviceCard(device: Device, onClick: () -> Unit) {
    val capMinutes = device.dailyLimitMinutes
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiIcon(R.drawable.ic_emoji_laptop, size = 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        device.label.ifBlank { "Ноутбук" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = if (capMinutes != null) "${device.todayMinutesUsed} из $capMinutes мин"
                        else "Не настроен — нажмите, чтобы включить",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
            }
            if (capMinutes != null && capMinutes > 0) {
                Spacer(Modifier.height(10.dp))
                val progress = (device.todayMinutesUsed.toFloat() / capMinutes).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (progress >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailSheet(
    device: Device,
    onDismiss: () -> Unit,
    onSetLimit: (Int?) -> Unit,
    onLockNow: () -> Unit,
    onRemove: () -> Unit
) {
    var minutes by remember { mutableIntStateOf(device.dailyLimitMinutes ?: 60) }
    var limitEnabled by remember { mutableStateOf(device.dailyLimitMinutes != null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiIcon(R.drawable.ic_emoji_laptop, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(text = device.label.ifBlank { "Ноутбук" }, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Сегодня использовано: ${device.todayMinutesUsed} мин",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Дневной лимит", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    limitEnabled = !limitEnabled
                    if (!limitEnabled) onSetLimit(null)
                }) {
                    Text(if (limitEnabled) "Отключить" else "Включить")
                }
            }

            if (limitEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { minutes = (minutes - 5).coerceAtLeast(5) }) {
                        Text("–", style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "$minutes мин/день",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.width(140.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { minutes = (minutes + 5).coerceAtMost(600) }) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSetLimit(if (limitEnabled) minutes else null) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Сохранить лимит") }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onLockNow, modifier = Modifier.fillMaxWidth()) {
                Text("Заблокировать сейчас")
            }

            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = { showRemoveConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Отвязать устройство") }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Отвязать устройство?") },
            text = { Text("Ноутбук перестанет получать лимиты и команды от этого аккаунта.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onRemove()
                }) { Text("Отвязать") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Отмена") }
            }
        )
    }
}
