package com.teo.child.permission

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.teo.child.admin.DeviceAdminHelper

@Composable
fun PermissionsScreen(
    onAllGranted: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    var showAccessibilityExplainer by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.allGranted) {
        if (uiState.allGranted) onAllGranted()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setNotificationsGranted(granted) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setLocationForegroundGranted(granted) }

    val callPhoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setCallPhoneGranted(granted) }

    val readPhoneStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setReadPhoneStateGranted(granted) }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setActivityRecognitionGranted(granted) }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setRecordAudioGranted(granted) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Осталось немного настроек",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Это нужно, чтобы приложение действительно работало — по одному разу для каждого пункта.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        PermissionRow(
            title = "Доступ к статистике использования",
            description = "Чтобы считать время в приложениях",
            granted = uiState.usageAccess,
            onClick = { UsageAccessHelper.openSettings(context) }
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Показ поверх других приложений",
            description = "Чтобы показать экран, когда время закончилось",
            granted = uiState.overlay,
            onClick = { OverlayPermissionHelper.openSettings(context) }
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Уведомления",
            description = "Для значка в строке состояния",
            granted = uiState.notifications,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Работа в фоне без ограничений",
            description = "Чтобы телефон не «засыпал» приложение",
            granted = uiState.batteryExempt,
            onClick = { BatteryOptimizationHelper.requestIgnore(context) }
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(
                text = "На телефонах Samsung этого может быть недостаточно: зайдите в Настройки → " +
                    "Обслуживание батареи и устройства → Батарея → Ограничения фона, и уберите это " +
                    "приложение из списка «Спящие приложения» / «Неиспользуемые приложения».",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Геолокация",
            description = "Чтобы родители видели, где телефон",
            granted = uiState.locationForeground,
            onClick = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
        )
        if (uiState.locationForeground) {
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                title = "Геолокация всегда",
                description = "Разрешить «Всегда» в настройках, а не только при открытом приложении",
                granted = uiState.locationBackground,
                onClick = { LocationPermissionHelper.openAppSettingsForBackgroundLocation(context) }
            )
        }
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Защита от удаления",
            description = "Чтобы приложение нельзя было удалить без разрешения родителя",
            granted = uiState.deviceAdmin,
            onClick = { DeviceAdminHelper.requestActivation(context) }
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Экстренный звонок родителю",
            description = "Чтобы кнопка SOS дозванивалась сразу, без открытия набора номера",
            granted = uiState.callPhone,
            onClick = { callPhoneLauncher.launch(Manifest.permission.CALL_PHONE) }
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Управление режимом звука (необязательно)",
            description = "Позволяет родителю удалённо включить звук звонка",
            granted = uiState.notificationPolicyAccess,
            onClick = { NotificationPolicyPermissionHelper.openSettings(context) }
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Звук при входящем звонке (необязательно)",
            description = "Чтобы любой звонок звучал, даже если телефон на беззвучном, и возвращал тишину после",
            granted = uiState.readPhoneState,
            onClick = { readPhoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE) }
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Датчик шагов (необязательно)",
            description = "Чтобы задание «Шаги» считалось само, по шагомеру телефона",
            granted = uiState.activityRecognition,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Микрофон (необязательно)",
            description = "Для «Послушать вокруг» — при запросе от мамы микрофон включается сразу",
            granted = uiState.recordAudio,
            onClick = { recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Специальные возможности",
            description = "Дополнительная защита от удаления через настройки",
            granted = uiState.accessibility,
            onClick = { showAccessibilityExplainer = true }
        )
    }

    if (showAccessibilityExplainer) {
        AlertDialog(
            onDismissRequest = { showAccessibilityExplainer = false },
            title = { Text("Перед тем как продолжить") },
            text = {
                Text(
                    "Android может показать пункт «Специальные возможности» серым и заблокированным. " +
                        "Если так — на экране приложения нажмите значок ⋮ в правом верхнем углу и выберите " +
                        "«Разрешить ограниченные настройки», затем вернитесь и включите переключатель для " +
                        "«Nest Kid»."
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
private fun PermissionRow(
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
