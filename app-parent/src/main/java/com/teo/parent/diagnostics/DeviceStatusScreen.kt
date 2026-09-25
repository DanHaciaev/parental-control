package com.teo.parent.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.DeviceStatus
import com.teo.parent.util.timeAgo

@Composable
fun DeviceStatusScreen(viewModel: DeviceStatusViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        when {
            uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
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
                StatusRow("Геолокация включена", status.locationServicesEnabled)

                if (!allOk(status)) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Что-то из списка выключено — попросите ребёнка открыть приложение " +
                            "«Nest Kid», там будет видно, что нужно разрешить заново.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
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
        status.deviceAdminActive && status.accessibilityEnabled && status.batteryOptimizationExempt &&
        status.locationServicesEnabled
