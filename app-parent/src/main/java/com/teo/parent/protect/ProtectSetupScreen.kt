package com.teo.parent.protect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.teo.parent.accessibility.AccessibilityPermissionHelper
import com.teo.parent.admin.DeviceAdminHelper

@Composable
fun ProtectSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var deviceAdmin by remember { mutableStateOf(DeviceAdminHelper.isActive(context)) }
    var accessibility by remember { mutableStateOf(AccessibilityPermissionHelper.isEnabled(context)) }
    var showAccessibilityExplainer by remember { mutableStateOf(false) }

    fun refresh() {
        deviceAdmin = DeviceAdminHelper.isActive(context)
        accessibility = AccessibilityPermissionHelper.isEnabled(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(deviceAdmin, accessibility) {
        if (deviceAdmin && accessibility) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Защитим и ваше приложение",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Чтобы ребёнок случайно (или специально) не удалил это приложение с вашего телефона.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        ProtectRow(
            title = "Защита от удаления",
            description = "Потребует ваш код перед удалением",
            granted = deviceAdmin,
            onClick = { DeviceAdminHelper.requestActivation(context) }
        )
        Spacer(Modifier.height(12.dp))
        ProtectRow(
            title = "Специальные возможности",
            description = "Дополнительная защита от удаления через настройки",
            granted = accessibility,
            onClick = { showAccessibilityExplainer = true }
        )
    }

    if (showAccessibilityExplainer) {
        AlertDialog(
            onDismissRequest = { showAccessibilityExplainer = false },
            title = { Text("Перед тем как продолжить") },
            text = {
                Text(
                    "Если пункт «Специальные возможности» окажется заблокирован — нажмите значок ⋮ " +
                        "в правом верхнем углу экрана приложения и выберите «Разрешить ограниченные настройки», " +
                        "затем вернитесь и включите переключатель для «Родительский контроль»."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAccessibilityExplainer = false
                    AccessibilityPermissionHelper.openSettings(context)
                }) { Text("Понятно, открыть настройки") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityExplainer = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ProtectRow(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Text(text = "Готово", color = MaterialTheme.colorScheme.secondary)
            } else {
                OutlinedButton(onClick = onClick) { Text("Разрешить") }
            }
        }
    }
}
