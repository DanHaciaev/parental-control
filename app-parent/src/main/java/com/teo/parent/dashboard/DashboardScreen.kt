package com.teo.parent.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.Device
import com.teo.core.model.DeviceStatus
import com.teo.core.model.RuleMode
import com.teo.parent.R
import com.teo.parent.devices.DeviceCard
import com.teo.parent.devices.DeviceDetailSheet
import com.teo.parent.devices.DevicePairingScreen
import com.teo.parent.devices.ReconnectChildScreen
import com.teo.parent.diagnostics.DeviceStatusScreen
import com.teo.parent.events.EventsScreen
import com.teo.parent.history.HistoryScreen
import com.teo.parent.installs.InstallApprovalsScreen
import com.teo.parent.listen.ListenSessionScreen
import com.teo.parent.map.BatteryBadge
import com.teo.parent.map.MapScreen
import com.teo.parent.requests.RequestsScreen
import com.teo.parent.schedules.ScheduleListScreen
import com.teo.parent.settings.FamilySettingsScreen
import com.teo.parent.statistics.StatisticsScreen
import com.teo.parent.weeklylimits.WeeklyLimitsScreen
import com.teo.parent.ui.theme.CategoryBlocked
import com.teo.parent.ui.theme.CategoryBlockedBg
import com.teo.parent.ui.theme.CategoryFree
import com.teo.parent.ui.theme.CategoryFreeBg
import com.teo.parent.ui.theme.CategoryTimed
import com.teo.parent.ui.theme.CategoryTimedBg
import com.teo.parent.ui.theme.SoftAlert
import com.teo.parent.work.WorkScheduler

private val TAB_TITLES = listOf("Главная", "История", "Задания", "Карта", "Ещё")

private enum class OverlayScreen { STATISTICS, EVENTS, SCHEDULES, FAMILY_SETTINGS, DEVICE_STATUS, WEEKLY_LIMITS, INSTALL_APPROVALS, DEVICE_PAIRING, LISTEN_SESSION, RECONNECT_CHILD }

@Composable
private fun NavLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        maxLines = 1,
        softWrap = false
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSignOut: () -> Unit,
    openInstallApprovals: MutableState<Boolean> = mutableStateOf(false),
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var editingApp by remember { mutableStateOf<AppRow?>(null) }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var showRingConfirm by remember { mutableStateOf(false) }
    var ringActive by remember { mutableStateOf(false) }
    var activeOverlay by remember { mutableStateOf<OverlayScreen?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var expandedCategory by remember { mutableStateOf<AppCategory?>(null) }
    // Hoisted out of WeeklyLimitsScreen so the top-bar back arrow below can pop one level (day ->
    // day list) instead of always closing the whole section — see WeeklyLimitsScreen's kdoc.
    var weeklyLimitsSelectedDay by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = activeOverlay == OverlayScreen.WEEKLY_LIMITS && weeklyLimitsSelectedDay != null) {
        weeklyLimitsSelectedDay = null
    }

    // Tapping an install-related notification jumps straight to this screen — consumed once so
    // navigating away afterward (or a later plain app-icon open) doesn't keep re-triggering it.
    LaunchedEffect(openInstallApprovals.value) {
        if (openInstallApprovals.value) {
            activeOverlay = OverlayScreen.INSTALL_APPROVALS
            openInstallApprovals.value = false
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        WorkScheduler.scheduleEventPoll(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when {
                        expandedCategory != null -> categoryTitle(expandedCategory!!)
                        activeOverlay == OverlayScreen.STATISTICS -> "Статистика"
                        activeOverlay == OverlayScreen.EVENTS -> "Журнал событий"
                        activeOverlay == OverlayScreen.SCHEDULES -> "Расписания блокировок"
                        activeOverlay == OverlayScreen.FAMILY_SETTINGS -> "Настройки семьи"
                        activeOverlay == OverlayScreen.DEVICE_STATUS -> "Диагностика устройства ребёнка"
                        activeOverlay == OverlayScreen.WEEKLY_LIMITS -> "Лимиты по дням недели"
                        activeOverlay == OverlayScreen.INSTALL_APPROVALS -> "Новые приложения"
                        activeOverlay == OverlayScreen.DEVICE_PAIRING -> "Добавить устройство"
                        activeOverlay == OverlayScreen.LISTEN_SESSION -> "Послушать вокруг"
                        activeOverlay == OverlayScreen.RECONNECT_CHILD -> "Переподключить телефон ребёнка"
                        selectedTab == 0 -> uiState.childName ?: "Главная"
                        else -> TAB_TITLES[selectedTab]
                    }
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    if (expandedCategory != null) {
                        IconButton(onClick = { expandedCategory = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    } else if (activeOverlay == OverlayScreen.WEEKLY_LIMITS && weeklyLimitsSelectedDay != null) {
                        IconButton(onClick = { weeklyLimitsSelectedDay = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    } else if (activeOverlay != null) {
                        IconButton(onClick = { activeOverlay = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0 && activeOverlay == null,
                    onClick = { selectedTab = 0; activeOverlay = null; expandedCategory = null },
                    icon = { EmojiIcon(R.drawable.ic_emoji_home) },
                    label = { NavLabel("Главная") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1 && activeOverlay == null,
                    onClick = { selectedTab = 1; activeOverlay = null; expandedCategory = null },
                    icon = { EmojiIcon(R.drawable.ic_emoji_history) },
                    label = { NavLabel("История") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2 && activeOverlay == null,
                    onClick = { selectedTab = 2; activeOverlay = null; expandedCategory = null },
                    icon = { EmojiIcon(R.drawable.ic_emoji_check) },
                    label = { NavLabel("Задания") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3 && activeOverlay == null,
                    onClick = { selectedTab = 3; activeOverlay = null; expandedCategory = null },
                    icon = { EmojiIcon(R.drawable.ic_emoji_map_pin) },
                    label = { NavLabel("Карта") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4 && activeOverlay == null,
                    onClick = { selectedTab = 4; activeOverlay = null; expandedCategory = null },
                    icon = { Text("⋯") },
                    label = { NavLabel("Ещё") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                expandedCategory != null -> CategoryAppListScreen(
                    apps = when (expandedCategory) {
                        AppCategory.BLOCKED -> uiState.blocked
                        AppCategory.TIMED -> uiState.timed
                        AppCategory.UNRESTRICTED -> uiState.unrestricted
                        null -> emptyList()
                    },
                    onAppClick = { editingApp = it }
                )
                activeOverlay == OverlayScreen.STATISTICS -> StatisticsScreen()
                activeOverlay == OverlayScreen.EVENTS -> EventsScreen()
                activeOverlay == OverlayScreen.SCHEDULES -> ScheduleListScreen()
                activeOverlay == OverlayScreen.FAMILY_SETTINGS -> FamilySettingsScreen(
                    onSaved = { activeOverlay = null }
                )
                activeOverlay == OverlayScreen.DEVICE_STATUS -> DeviceStatusScreen()
                activeOverlay == OverlayScreen.WEEKLY_LIMITS -> WeeklyLimitsScreen(
                    selectedDay = weeklyLimitsSelectedDay,
                    onSelectDayChange = { weeklyLimitsSelectedDay = it }
                )
                activeOverlay == OverlayScreen.INSTALL_APPROVALS -> InstallApprovalsScreen()
                activeOverlay == OverlayScreen.DEVICE_PAIRING -> DevicePairingScreen(
                    onPaired = { activeOverlay = null }
                )
                activeOverlay == OverlayScreen.LISTEN_SESSION -> ListenSessionScreen(
                    onClose = { activeOverlay = null }
                )
                activeOverlay == OverlayScreen.RECONNECT_CHILD -> ReconnectChildScreen(
                    onClose = { activeOverlay = null }
                )
                selectedTab == 1 -> HistoryScreen()
                selectedTab == 2 -> RequestsScreen()
                selectedTab == 3 -> MapScreen()
                selectedTab == 4 -> MoreTabContent(
                    onOpenFamilySettings = { activeOverlay = OverlayScreen.FAMILY_SETTINGS },
                    onOpenDeviceStatus = { activeOverlay = OverlayScreen.DEVICE_STATUS },
                    onOpenSchedules = { activeOverlay = OverlayScreen.SCHEDULES },
                    onOpenWeeklyLimits = { weeklyLimitsSelectedDay = null; activeOverlay = OverlayScreen.WEEKLY_LIMITS },
                    onOpenEvents = { activeOverlay = OverlayScreen.EVENTS },
                    onOpenInstallApprovals = { activeOverlay = OverlayScreen.INSTALL_APPROVALS },
                    onOpenDevicePairing = { activeOverlay = OverlayScreen.DEVICE_PAIRING },
                    onOpenReconnectChild = { activeOverlay = OverlayScreen.RECONNECT_CHILD },
                    onRingDevice = { showRingConfirm = true },
                    onListenAround = { activeOverlay = OverlayScreen.LISTEN_SESSION },
                    ringActive = ringActive,
                    onStopRinging = {
                        ringActive = false
                        viewModel.stopRinging()
                    },
                    onSetRingerNormal = viewModel::setRingerNormal,
                    onEnableLocation = viewModel::enableLocation,
                    onSignOut = onSignOut
                )
                else -> when {
                    uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    uiState.apps.isEmpty() -> Text(
                        text = "Пока нет данных об установленных приложениях. Список появится, как только на телефоне ребёнка всё настроится.",
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                    else -> HomeContent(
                        uiState = uiState,
                        onAppClick = { editingApp = it },
                        onOpenTasks = { selectedTab = 2 },
                        onOpenEvents = { activeOverlay = OverlayScreen.EVENTS },
                        onOpenStatistics = { activeOverlay = OverlayScreen.STATISTICS },
                        onOpenSchedules = { activeOverlay = OverlayScreen.SCHEDULES },
                        onOpenFamilySettings = { activeOverlay = OverlayScreen.FAMILY_SETTINGS },
                        onShowAllCategory = { expandedCategory = it },
                        onDeviceClick = { selectedDevice = it }
                    )
                }
            }
        }
    }

    if (showRingConfirm) {
        AlertDialog(
            onDismissRequest = { showRingConfirm = false },
            title = { Text("Найти телефон?") },
            text = {
                Text("На телефоне ребёнка на ~45 секунд включится громкий сигнал, даже если стоит беззвучный режим.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRingConfirm = false
                    ringActive = true
                    viewModel.ringDevice()
                }) { Text("Позвонить") }
            },
            dismissButton = {
                TextButton(onClick = { showRingConfirm = false }) { Text("Отмена") }
            }
        )
    }

    editingApp?.let { app ->
        RuleEditorSheet(
            app = app,
            onDismiss = { editingApp = null },
            onClearRule = {
                viewModel.clearRule(app.packageName)
                editingApp = null
            },
            onBlock = {
                viewModel.blockApp(app.packageName, app.appLabel)
                editingApp = null
            },
            onSetLimit = { minutes ->
                viewModel.setTimeLimit(app.packageName, app.appLabel, minutes)
                editingApp = null
            }
        )
    }

    selectedDevice?.let { device ->
        DeviceDetailSheet(
            device = device,
            onDismiss = { selectedDevice = null },
            onSetLimit = { minutes ->
                viewModel.setDeviceDailyLimit(device.id, minutes)
                selectedDevice = null
            },
            onLockNow = {
                viewModel.lockDeviceNow(device.id)
                selectedDevice = null
            },
            onRemove = {
                viewModel.removeDevice(device.id)
                selectedDevice = null
            }
        )
    }
}

@Composable
private fun HomeContent(
    uiState: DashboardUiState,
    onAppClick: (AppRow) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenEvents: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenFamilySettings: () -> Unit,
    onShowAllCategory: (AppCategory) -> Unit,
    onDeviceClick: (Device) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TopStatusCard(status = uiState.deviceStatus, onOpenStatistics = onOpenStatistics) }

        if (uiState.tamperAlert) {
            item { TamperAlertCard(onClick = onOpenEvents) }
        }
        if (uiState.pendingTasksCount > 0) {
            item { TasksSummaryCard(count = uiState.pendingTasksCount, onClick = onOpenTasks) }
        }

        item {
            TotalCapCard(
                usedMinutes = uiState.timed.sumOf { it.usedMinutesToday },
                capMinutes = uiState.totalScreenTimeCapMinutes,
                onClick = onOpenFamilySettings
            )
        }

        items(uiState.devices, key = { it.id }) { device ->
            DeviceCard(device = device, onClick = { onDeviceClick(device) })
        }

        uiState.activeScheduleName?.let { name ->
            item { ScheduleCard(activeScheduleName = name, onClick = onOpenSchedules) }
        }

        appSection(
            title = "Заблокировано",
            apps = uiState.blocked,
            onAppClick = onAppClick,
            onShowAll = { onShowAllCategory(AppCategory.BLOCKED) }
        )
        appSection(
            title = "По времени",
            apps = uiState.timed,
            onAppClick = onAppClick,
            onShowAll = { onShowAllCategory(AppCategory.TIMED) }
        )
        appSection(
            title = "Без ограничений",
            apps = uiState.unrestricted,
            onAppClick = onAppClick,
            onShowAll = { onShowAllCategory(AppCategory.UNRESTRICTED) }
        )
    }
}

private fun categoryTitle(category: AppCategory): String = when (category) {
    AppCategory.BLOCKED -> "Заблокировано"
    AppCategory.TIMED -> "По времени"
    AppCategory.UNRESTRICTED -> "Без ограничений"
}

@Composable
private fun CategoryAppListScreen(apps: List<AppRow>, onAppClick: (AppRow) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = apps.filterByAppSearch(query)

    Column(modifier = Modifier.fillMaxSize()) {
        AppSearchField(query = query, onQueryChange = { query = it })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                item {
                    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            filtered.forEachIndexed { index, app ->
                                AppRow(app = app, onClick = { onAppClick(app) })
                                if (index != filtered.lastIndex) RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val APP_SECTION_PREVIEW_LIMIT = 5

private fun LazyListScope.appSection(
    title: String,
    apps: List<AppRow>,
    onAppClick: (AppRow) -> Unit,
    onShowAll: () -> Unit
) {
    if (apps.isEmpty()) return
    item {
        Text(
            text = "$title (${apps.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
    }
    item {
        val preview = apps.take(APP_SECTION_PREVIEW_LIMIT)
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                preview.forEachIndexed { index, app ->
                    AppRow(app = app, onClick = { onAppClick(app) })
                    if (index != preview.lastIndex || apps.size > APP_SECTION_PREVIEW_LIMIT) RowDivider()
                }
                if (apps.size > APP_SECTION_PREVIEW_LIMIT) {
                    TextButton(onClick = onShowAll, modifier = Modifier.fillMaxWidth()) {
                        Text("Показать все (${apps.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun TopStatusCard(status: DeviceStatus?, onOpenStatistics: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val spec = status?.currentForegroundApp?.let { AppIconRegistry.forPackage(it) }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(spec?.bg ?: MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (spec != null) {
                        Image(
                            painter = painterResource(spec.glyph),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(spec.glyphTint),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        EmojiIcon(R.drawable.ic_emoji_phone_device, size = 22.dp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status?.currentForegroundAppLabel ?: "Нет данных",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                    Text(
                        text = "Сейчас на телефоне",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                BatteryBadge(status = status)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenStatistics, modifier = Modifier.fillMaxWidth()) {
                EmojiIcon(R.drawable.ic_emoji_bar_chart, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("Статистика")
            }
        }
    }
}

@Composable
private fun TotalCapCard(usedMinutes: Int, capMinutes: Int?, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiIcon(R.drawable.ic_emoji_stopwatch, size = 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Общий лимит на отвлекающие приложения",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (capMinutes != null) "$usedMinutes из $capMinutes мин" else "Не настроен — нажмите, чтобы включить",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
            }
            if (capMinutes != null && capMinutes > 0) {
                Spacer(Modifier.height(10.dp))
                val progress = (usedMinutes.toFloat() / capMinutes).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (progress >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(activeScheduleName: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmojiIcon(R.drawable.ic_emoji_moon, size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Сейчас действует расписание", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "«$activeScheduleName»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun TamperAlertCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SoftAlert),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmojiIcon(R.drawable.ic_emoji_warning, size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Защита была отключена", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text = "На телефоне ребёнка кто-то выключил защиту от удаления",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
    }
}

@Composable
private fun TasksSummaryCard(count: Int, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmojiIcon(R.drawable.ic_emoji_check, size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Задания и запросы", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "$count ждут вашего решения",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 60.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    )
}

@Composable
private fun AppRow(app: AppRow, onClick: () -> Unit) {
    val (fg, bg) = categoryColors(app.category)
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = app.packageName, label = app.appLabel, fg = fg, bg = bg, iconBase64 = app.iconBase64)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.appLabel, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                if (app.category == AppCategory.TIMED) {
                    Text(
                        text = "${app.usedMinutesToday} из ${app.effectiveDailyLimitMinutes ?: 0} мин" +
                            if (app.bonusMinutesToday > 0) " (+${app.bonusMinutesToday} за задание)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusIndicator(app = app, fg = fg)
        }
        val dailyLimit = app.effectiveDailyLimitMinutes
        if (app.category == AppCategory.TIMED && dailyLimit != null && dailyLimit > 0) {
            val progress = (app.usedMinutesToday.toFloat() / dailyLimit).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp, end = 16.dp, bottom = 8.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (progress >= 1f) MaterialTheme.colorScheme.error else fg,
                trackColor = bg
            )
        }
    }
}

@Composable
private fun StatusIndicator(app: AppRow, fg: Color) {
    val text = when (app.category) {
        AppCategory.BLOCKED -> "🔒"
        AppCategory.TIMED -> "${app.effectiveDailyLimitMinutes ?: 0} мин"
        AppCategory.UNRESTRICTED -> "∞"
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.Bold)
}

@Composable
private fun categoryColors(category: AppCategory): Pair<Color, Color> = when (category) {
    AppCategory.BLOCKED -> CategoryBlocked to CategoryBlockedBg
    AppCategory.TIMED -> CategoryTimed to CategoryTimedBg
    AppCategory.UNRESTRICTED -> CategoryFree to CategoryFreeBg
}

@Composable
private fun MoreTabContent(
    onOpenFamilySettings: () -> Unit,
    onOpenDeviceStatus: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenWeeklyLimits: () -> Unit,
    onOpenEvents: () -> Unit,
    onOpenInstallApprovals: () -> Unit,
    onOpenDevicePairing: () -> Unit,
    onOpenReconnectChild: () -> Unit,
    onRingDevice: () -> Unit,
    onListenAround: () -> Unit,
    ringActive: Boolean,
    onStopRinging: () -> Unit,
    onSetRingerNormal: () -> Unit,
    onEnableLocation: () -> Unit,
    onSignOut: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_family,
                title = "Настройки семьи",
                subtitle = "Имя ребёнка, телефон для SOS, общий лимит",
                onClick = onOpenFamilySettings
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_moon,
                title = "Расписания блокировок",
                subtitle = "Сон, уроки и другие периоды недоступности",
                onClick = onOpenSchedules
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_calendar,
                title = "Лимиты по дням недели",
                subtitle = "Разное время на приложение в разные дни",
                onClick = onOpenWeeklyLimits
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_bell,
                title = "Журнал событий",
                subtitle = "Установки, лимиты, SOS, попытки удаления",
                onClick = onOpenEvents
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_package,
                title = "Новые приложения",
                subtitle = "Разрешить или запретить недавно установленные",
                onClick = onOpenInstallApprovals
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_laptop,
                title = "Добавить устройство",
                subtitle = "Привязать ноутбук ребёнка и ставить лимит времени с телефона",
                onClick = onOpenDevicePairing
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_phone_device,
                title = "Переподключить телефон ребёнка",
                subtitle = "Если ребёнок удалил приложение — подключить заново",
                onClick = onOpenReconnectChild
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_stethoscope,
                title = "Диагностика устройства ребёнка",
                subtitle = "Проверить, всё ли настроено правильно",
                onClick = onOpenDeviceStatus
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_speaker,
                title = if (ringActive) "Остановить сигнал" else "Найти телефон",
                subtitle = "Громкий сигнал на ~45 секунд, даже в беззвучном режиме",
                onClick = if (ringActive) onStopRinging else onRingDevice
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_listen_signal,
                title = "Послушать вокруг",
                subtitle = "Звук вокруг ребёнка начнёт передаваться сразу",
                onClick = onListenAround
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_bell,
                title = "Включить звук на телефоне",
                subtitle = "Выйти из беззвучного/вибро режима (нужно доп. разрешение у ребёнка)",
                onClick = onSetRingerNormal
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_map_pin,
                title = "Включить геолокацию",
                subtitle = "Если ребёнок её отключил",
                onClick = onEnableLocation
            )
        }
        item {
            SettingsRow(
                iconRes = R.drawable.ic_emoji_door,
                title = "Выйти",
                subtitle = null,
                onClick = onSignOut
            )
        }
    }
}

@Composable
private fun SettingsRow(@DrawableRes iconRes: Int, title: String, subtitle: String?, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmojiIcon(iconRes, size = 28.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorSheet(
    app: AppRow,
    onDismiss: () -> Unit,
    onClearRule: () -> Unit,
    onBlock: () -> Unit,
    onSetLimit: (Int) -> Unit
) {
    var selectedMode by remember { mutableStateOf(app.rule?.mode) }
    var minutes by remember { mutableIntStateOf(app.rule?.dailyLimitMinutes ?: 60) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val (fg, bg) = categoryColors(
        when (selectedMode) {
            RuleMode.BLOCKED -> AppCategory.BLOCKED
            RuleMode.TIME_LIMIT -> AppCategory.TIMED
            null -> AppCategory.UNRESTRICTED
        }
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = app.packageName, label = app.appLabel, fg = fg, bg = bg, iconBase64 = app.iconBase64)
                Spacer(Modifier.width(12.dp))
                Text(text = app.appLabel, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeOption(
                    label = "Свободно",
                    description = "Без ограничений",
                    selected = selectedMode == null,
                    dotColor = CategoryFree,
                    onClick = { selectedMode = null }
                )
                ModeOption(
                    label = "По времени",
                    description = "Дневной лимит в минутах",
                    selected = selectedMode == RuleMode.TIME_LIMIT,
                    dotColor = CategoryTimed,
                    onClick = { selectedMode = RuleMode.TIME_LIMIT }
                )
                ModeOption(
                    label = "Заблокировать",
                    description = "Приложение недоступно",
                    selected = selectedMode == RuleMode.BLOCKED,
                    dotColor = CategoryBlocked,
                    onClick = { selectedMode = RuleMode.BLOCKED }
                )
            }

            if (selectedMode == RuleMode.TIME_LIMIT) {
                Spacer(Modifier.height(24.dp))
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

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    when (selectedMode) {
                        null -> onClearRule()
                        RuleMode.BLOCKED -> onBlock()
                        RuleMode.TIME_LIMIT -> onSetLimit(minutes)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Сохранить") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
    }
}

@Composable
private fun ModeOption(
    label: String,
    description: String,
    selected: Boolean,
    dotColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) dotColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) dotColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            }
            if (selected) {
                Text(text = "✓", style = MaterialTheme.typography.titleLarge, color = dotColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}
