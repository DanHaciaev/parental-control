package com.teo.parent.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.DeviceStatus
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun DeviceStatusDialog(
    onDismiss: () -> Unit,
    viewModel: DeviceStatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Как дела на телефоне ребёнка", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                when {
                    uiState.loading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    uiState.status == null -> Text("Пока нет данных. Телефон ребёнка ещё не выходил на связь.")
                    else -> {
                        val status = uiState.status!!
                        Text(
                            text = "Последний раз на связи: ${timeAgo(status.lastSeenAt)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Батарея: ${status.batteryPercent}%${if (status.isCharging) " (заряжается)" else ""}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(16.dp))

                        StatusRow("Служба контроля запущена", status.protectionServiceRunning)
                        StatusRow("Доступ к статистике использования", status.usageAccessEnabled)
                        StatusRow("Показ поверх экрана", status.overlayEnabled)
                        StatusRow("Защита от удаления", status.deviceAdminActive)
                        StatusRow("Специальные возможности", status.accessibilityEnabled)
                        StatusRow("Не ограничен по батарее", status.batteryOptimizationExempt)

                        if (!allOk(status)) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Что-то из списка выключено — попросите ребёнка открыть приложение " +
                                    "«Семейный помощник», там будет видно, что нужно разрешить заново.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(if (ok) "✅" else "❌")
    }
}

private fun allOk(status: DeviceStatus): Boolean =
    status.protectionServiceRunning && status.usageAccessEnabled && status.overlayEnabled &&
        status.deviceAdminActive && status.accessibilityEnabled && status.batteryOptimizationExempt

private fun timeAgo(date: Date?): String {
    if (date == null) return "неизвестно"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - date.time)
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 24 * 60 -> "${minutes / 60} ч назад"
        else -> "${minutes / (24 * 60)} дн назад"
    }
}
