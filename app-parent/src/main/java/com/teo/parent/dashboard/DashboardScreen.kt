package com.teo.parent.dashboard

import com.teo.core.ui.theme.NestColors
import com.teo.core.ui.components.StatusBarOnNavy
import com.teo.core.ui.components.RowDivider
import com.teo.core.ui.components.NestScreenHeader
import com.teo.core.ui.components.NestNavItem
import com.teo.core.ui.components.NestListRow
import com.teo.core.ui.components.NestCard
import com.teo.core.ui.components.NestBottomBar
import androidx.compose.foundation.layout.WindowInsets
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
import com.teo.core.ui.components.NestStyledCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import com.teo.core.R as CoreR
import com.teo.core.model.Device
import com.teo.core.model.DeviceStatus
import com.teo.core.model.RuleMode
import com.teo.parent.R
import com.teo.parent.auth.ChildSummary
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
import com.teo.parent.notifications.SosNotifications
import com.teo.parent.pin.SecuritySettingsScreen
import com.teo.parent.requests.RequestsScreen
import com.teo.parent.schedules.ScheduleListScreen
import com.teo.parent.settings.DeleteAccountDialog
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

private val TAB_TITLES = listOf("Главная", "История", "Задания и запросы", "Карта", "Ещё")

private enum class OverlayScreen { STATISTICS, EVENTS, SCHEDULES, FAMILY_SETTINGS, DEVICE_STATUS, WEEKLY_LIMITS, INSTALL_APPROVALS, DEVICE_PAIRING, LISTEN_SESSION, RECONNECT_CHILD, SECURITY }

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
    children: List<ChildSummary> = emptyList(),
    selectedFamilyId: String? = null,
    onSelectChild: (String) -> Unit = {},
    onAddChild: () -> Unit = {},
    parentInviteCode: String? = null,
    parentInviteError: String? = null,
    onCreateParentInvite: () -> Unit = {},
    onDismissParentInvite: () -> Unit = {},
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
    var childMenuOpen by remember { mutableStateOf(false) }
    var showParentInvite by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }

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
    var showFullScreenSosPrompt by remember { mutableStateOf(SosNotifications.shouldAskForFullScreen(context)) }

    val switcher: @Composable () -> Unit = {
        ChildSwitcher(
            childName = uiState.childName ?: children.firstOrNull { it.familyId == selectedFamilyId }?.name ?: "Ребёнок",
            children = children,
            selectedFamilyId = selectedFamilyId,
            onSelectChild = onSelectChild,
            onAddChild = onAddChild
        )
    }
    // Every screen here starts with the navy header — white status-bar icons on it.
    StatusBarOnNavy()

    Scaffold(
        containerColor = NestColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NestBottomBar(
                items = NAV_ITEMS,
                selectedIndex = if (activeOverlay == null && expandedCategory == null) selectedTab else -1,
                onSelect = { index ->
                    selectedTab = index
                    activeOverlay = null
                    expandedCategory = null
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val overlay = activeOverlay
            val category = expandedCategory
            when {
                category != null -> NestScreenHeader(categoryTitle(category), onBack = { expandedCategory = null })
                overlay != null -> NestScreenHeader(
                    title = overlayTitle(overlay),
                    onBack = {
                        if (overlay == OverlayScreen.WEEKLY_LIMITS && weeklyLimitsSelectedDay != null) weeklyLimitsSelectedDay = null
                        else activeOverlay = null
                    }
                )
                selectedTab == 0 -> Unit // the home content draws its own, larger header
                else -> TabHeader(title = TAB_TITLES[selectedTab], switcher = switcher)
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    category != null -> CategoryAppListScreen(
                        apps = when (category) {
                            AppCategory.BLOCKED -> uiState.blocked
                            AppCategory.TIMED -> uiState.timed
                            AppCategory.UNRESTRICTED -> uiState.unrestricted
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
                    activeOverlay == OverlayScreen.SECURITY -> SecuritySettingsScreen(
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
                        onOpenSecurity = { activeOverlay = OverlayScreen.SECURITY },
                        onRingDevice = { showRingConfirm = true },
                        onListenAround = { activeOverlay = OverlayScreen.LISTEN_SESSION },
                        ringActive = ringActive,
                        onStopRinging = {
                            ringActive = false
                            viewModel.stopRinging()
                        },
                        onSetRingerNormal = viewModel::setRingerNormal,
                        onAddChild = onAddChild,
                        onInviteParent = {
                            onCreateParentInvite()
                            showParentInvite = true
                        },
                        onDeleteAccount = { showDeleteAccount = true },
                        onSignOut = onSignOut
                    )
                    uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    else -> HomeContent(
                        uiState = uiState,
                        switcher = switcher,
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

    if (showDeleteAccount) {
        DeleteAccountDialog(
            // The Auth account is already gone — signing out just resets the app to the login screen.
            onDeleted = {
                showDeleteAccount = false
                onSignOut()
            },
            onDismiss = { showDeleteAccount = false }
        )
    }

    if (showFullScreenSosPrompt) {
        AlertDialog(
            onDismissRequest = {
                SosNotifications.markFullScreenAsked(context)
                showFullScreenSosPrompt = false
            },
            title = { Text("SOS на весь экран") },
            text = {
                Text(
                    "Чтобы сигнал SOS от ребёнка появлялся на весь экран даже на заблокированном телефоне, " +
                        "разрешите это в настройках: включите переключатель для Nest Parent. " +
                        "Без этого SOS придёт обычным срочным уведомлением."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SosNotifications.markFullScreenAsked(context)
                    showFullScreenSosPrompt = false
                    SosNotifications.openFullScreenSettings(context)
                }) { Text("Открыть настройки") }
            },
            dismissButton = {
                TextButton(onClick = {
                    SosNotifications.markFullScreenAsked(context)
                    showFullScreenSosPrompt = false
                }) { Text("Не сейчас") }
            }
        )
    }

    if (showParentInvite) {
        AlertDialog(
            onDismissRequest = { showParentInvite = false; onDismissParentInvite() },
            title = { Text("Пригласить второго родителя") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Второй родитель устанавливает «Nest Parent», регистрируется и на экране привязки нажимает «Я второй родитель». Код действует 15 минут.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(20.dp))
                    when {
                        parentInviteError != null -> Text(parentInviteError, color = MaterialTheme.colorScheme.error)
                        parentInviteCode == null -> CircularProgressIndicator()
                        else -> Text(
                            text = parentInviteCode.take(3) + " " + parentInviteCode.drop(3),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParentInvite = false; onDismissParentInvite() }) { Text("Готово") }
            }
        )
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

private fun categoryTitle(category: AppCategory): String = when (category) {
    AppCategory.BLOCKED -> "Заблокировано"
    AppCategory.TIMED -> "По времени"
    AppCategory.UNRESTRICTED -> "Без ограничений"
}

private fun overlayTitle(overlay: OverlayScreen): String = when (overlay) {
    OverlayScreen.STATISTICS -> "Статистика"
    OverlayScreen.EVENTS -> "Журнал событий"
    OverlayScreen.SCHEDULES -> "Расписания"
    OverlayScreen.FAMILY_SETTINGS -> "Настройки семьи"
    OverlayScreen.DEVICE_STATUS -> "Диагностика"
    OverlayScreen.WEEKLY_LIMITS -> "Лимиты по дням"
    OverlayScreen.INSTALL_APPROVALS -> "Новые приложения"
    OverlayScreen.DEVICE_PAIRING -> "Добавить устройство"
    OverlayScreen.LISTEN_SESSION -> "Послушать вокруг"
    OverlayScreen.RECONNECT_CHILD -> "Переподключить телефон"
    OverlayScreen.SECURITY -> "Код защиты и вход"
}

private val NAV_ITEMS = listOf(
    NestNavItem("Главная", CoreR.drawable.nest_ic_house),
    NestNavItem("История", CoreR.drawable.nest_ic_history),
    NestNavItem("Задания", CoreR.drawable.nest_ic_square_check),
    NestNavItem("Карта", CoreR.drawable.nest_ic_map_pin),
    NestNavItem("Ещё", CoreR.drawable.nest_ic_ellipsis)
)

@Composable
private fun CategoryAppListScreen(apps: List<AppRow>, onAppClick: (AppRow) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = apps.filterByAppSearch(query)

    Column(modifier = Modifier.fillMaxSize()) {
        AppSearchField(query = query, onQueryChange = { query = it })
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NestColors.InkSecondary,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                item {
                    NestCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        filtered.forEachIndexed { index, app ->
                            if (index > 0) RowDivider()
                            AppListRow(app = app, onClick = { onAppClick(app) })
                        }
                    }
                }
            }
        }
    }
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
    onOpenSecurity: () -> Unit,
    onRingDevice: () -> Unit,
    onListenAround: () -> Unit,
    ringActive: Boolean,
    onStopRinging: () -> Unit,
    onSetRingerNormal: () -> Unit,
    onAddChild: () -> Unit,
    onInviteParent: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_smartphone,
                title = "Добавить ребёнка",
                subtitle = "Привязать телефон ещё одного ребёнка",
                onClick = onAddChild
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_users,
                title = "Пригласить второго родителя",
                subtitle = "Код, чтобы второй родитель видел всех детей в своём приложении",
                onClick = onInviteParent
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_users,
                title = "Настройки семьи",
                subtitle = "Имя ребёнка, телефон для SOS, общий лимит",
                onClick = onOpenFamilySettings
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_shield,
                title = "Код защиты и вход",
                subtitle = "Сменить код, вход по отпечатку",
                onClick = onOpenSecurity
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_moon,
                title = "Расписания блокировок",
                subtitle = "Сон, уроки и другие периоды недоступности",
                onClick = onOpenSchedules
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_calendar,
                title = "Лимиты по дням недели",
                subtitle = "Разное время на приложение в разные дни",
                onClick = onOpenWeeklyLimits
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_bell,
                title = "Журнал событий",
                subtitle = "Установки, лимиты, SOS, попытки удаления",
                onClick = onOpenEvents
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_package,
                title = "Новые приложения",
                subtitle = "Разрешить или запретить недавно установленные",
                onClick = onOpenInstallApprovals
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_laptop,
                title = "Добавить устройство",
                subtitle = "Привязать ноутбук ребёнка и ставить лимит времени с телефона",
                onClick = onOpenDevicePairing
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_smartphone,
                title = "Переподключить телефон ребёнка",
                subtitle = "Если ребёнок удалил приложение — подключить заново",
                onClick = onOpenReconnectChild
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_stethoscope,
                title = "Диагностика устройства ребёнка",
                subtitle = "Проверить, всё ли настроено правильно",
                onClick = onOpenDeviceStatus
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_volume_2,
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
                iconRes = CoreR.drawable.nest_ic_bell,
                title = "Включить звук на телефоне",
                subtitle = "Выйти из беззвучного/вибро режима (нужно доп. разрешение у ребёнка)",
                onClick = onSetRingerNormal
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_trash_2,
                title = "Удалить аккаунт",
                subtitle = "Навсегда удалить аккаунт и данные детей",
                onClick = onDeleteAccount
            )
        }
        item {
            SettingsRow(
                iconRes = CoreR.drawable.nest_ic_log_out,
                title = "Выйти",
                subtitle = null,
                onClick = onSignOut
            )
        }
    }
}

@Composable
private fun SettingsRow(@DrawableRes iconRes: Int, title: String, subtitle: String?, onClick: () -> Unit) {
    NestCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        NestListRow(title = title, subtitle = subtitle, icon = iconRes)
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

@Composable
private fun categoryColors(category: AppCategory): Pair<Color, Color> = when (category) {
    AppCategory.BLOCKED -> CategoryBlocked to CategoryBlockedBg
    AppCategory.TIMED -> CategoryTimed to CategoryTimedBg
    AppCategory.UNRESTRICTED -> CategoryFree to CategoryFreeBg
}
