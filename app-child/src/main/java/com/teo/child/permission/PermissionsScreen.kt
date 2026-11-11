package com.teo.child.permission

import androidx.compose.material3.Button
import com.teo.core.R as CoreR
import com.teo.core.ui.theme.NestColors
import com.teo.core.ui.components.StatusText
import com.teo.core.ui.components.NestOnboardingScreen
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
import com.teo.core.ui.components.NestStyledCard as Card
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
    var pendingDisclosure by remember { mutableStateOf<SensitivePermission?>(null) }

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

    NestOnboardingScreen(
        title = "Разрешения",
        subtitle = "Это нужно, чтобы приложение действительно работало — по одному разу для каждого пункта.",
        headerIcon = CoreR.drawable.nest_ic_list_checks
    ) {
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
            description = "Откроются настройки Nest Kid: Аккумулятор → «Без ограничений»",
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
            onClick = { pendingDisclosure = SensitivePermission.LOCATION }
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
            onClick = { pendingDisclosure = SensitivePermission.DEVICE_ADMIN }
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
            description = "Для «Послушать вокруг» — по запросу родителя, с уведомлением на этом телефоне",
            granted = uiState.recordAudio,
            onClick = { pendingDisclosure = SensitivePermission.MICROPHONE }
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Специальные возможности",
            description = "Блокировка приложений, история YouTube и Chrome, защита от удаления",
            granted = uiState.accessibility,
            onClick = { pendingDisclosure = SensitivePermission.ACCESSIBILITY }
        )
    }

    pendingDisclosure?.let { permission ->
        PermissionDisclosureDialog(
            title = permission.title,
            text = permission.text,
            onAgree = {
                pendingDisclosure = null
                when (permission) {
                    SensitivePermission.LOCATION -> locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    SensitivePermission.DEVICE_ADMIN -> DeviceAdminHelper.requestActivation(context)
                    SensitivePermission.MICROPHONE -> recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    SensitivePermission.ACCESSIBILITY -> showAccessibilityExplainer = true
                }
            },
            onDismiss = { pendingDisclosure = null }
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
                StatusText("Готово", NestColors.Success, icon = CoreR.drawable.nest_ic_circle_check)
            } else {
                Button(onClick = onClick) { Text("Разрешить") }
            }
        }
    }
}

/** The permissions that get their own explanation before the system screen — see
 *  PermissionDisclosureDialog. Location wording follows Google Play's background-location rules
 *  (says "местоположение" and that it works when the app is closed). */
private enum class SensitivePermission(val title: String, val text: String) {
    LOCATION(
        "Доступ к местоположению",
        "Nest Kid собирает данные о местоположении этого телефона, чтобы родители видели на карте, где находится " +
            "ребёнок, — даже когда приложение закрыто или не используется. " +
            "Местоположение видят только родители этой семьи. На следующем шаге выберите «Разрешить в любом режиме»."
    ),
    ACCESSIBILITY(
        "Специальные возможности",
        "Nest Kid использует службу специальных возможностей, чтобы видеть, какое приложение открыто на экране, " +
            "и сразу закрывать заблокированные родителем; замечать установку новых приложений из Google Play; " +
            "читать на экране названия видео в YouTube и адреса страниц " +
            "в Chrome — их видят родители в разделе «История»; не давать удалить или отключить Nest Kid без кода " +
            "родителя; срабатывать на SOS, когда зажаты обе кнопки громкости. Другие тексты с экрана не сохраняются " +
            "и никуда не отправляются."
    ),
    DEVICE_ADMIN(
        "Защита от удаления",
        "Nest Kid станет администратором устройства. Это нужно только для того, чтобы приложение нельзя было удалить " +
            "без кода защиты родителя. Никаких других прав администратора приложение не использует: оно не стирает " +
            "данные, не меняет пароль экрана и не блокирует телефон через эту функцию."
    ),
    MICROPHONE(
        "Микрофон",
        "Родитель сможет на время включить «Послушать вокруг» и слышать звук рядом с телефоном ребёнка. Пока это " +
            "происходит, на этом телефоне видно уведомление. Звук передаётся только родителю в реальном времени и не записывается."
    )
}
