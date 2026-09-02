package com.teo.parent.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.teo.core.model.RuleMode
import com.teo.parent.diagnostics.DeviceStatusDialog
import com.teo.parent.events.EventsScreen
import com.teo.parent.map.MapScreen
import com.teo.parent.requests.RequestsScreen
import com.teo.parent.settings.FamilySettingsDialog
import com.teo.parent.ui.theme.CategoryBlocked
import com.teo.parent.ui.theme.CategoryBlockedBg
import com.teo.parent.ui.theme.CategoryFree
import com.teo.parent.ui.theme.CategoryFreeBg
import com.teo.parent.ui.theme.CategoryTimed
import com.teo.parent.ui.theme.CategoryTimedBg
import com.teo.parent.work.WorkScheduler

private val TAB_TITLES = listOf("Мой ребёнок", "Карта", "Журнал", "Настройки")

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
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var editingApp by remember { mutableStateOf<AppRow?>(null) }
    var showReleaseConfirm by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDeviceStatus by remember { mutableStateOf(false) }
    var showTasks by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

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
                    Text(
                        text = if (showTasks) "Задания и запросы" else TAB_TITLES[selectedTab],
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    if (showTasks) {
                        IconButton(onClick = { showTasks = false }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0 && !showTasks,
                    onClick = { selectedTab = 0; showTasks = false },
                    icon = { Text("📱") },
                    label = { NavLabel("Ребёнок") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1 && !showTasks,
                    onClick = { selectedTab = 1; showTasks = false },
                    icon = { Text("📍") },
                    label = { NavLabel("Карта") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2 && !showTasks,
                    onClick = { selectedTab = 2; showTasks = false },
                    icon = { Text("🔔") },
                    label = { NavLabel("Журнал") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3 && !showTasks,
                    onClick = { selectedTab = 3; showTasks = false },
                    icon = { Text("⚙️") },
                    label = { NavLabel("Настройки") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                showTasks -> RequestsScreen()
                selectedTab == 1 -> MapScreen()
                selectedTab == 2 -> EventsScreen()
                selectedTab == 3 -> SettingsTabContent(
                    onOpenFamilySettings = { showSettings = true },
                    onOpenDeviceStatus = { showDeviceStatus = true },
                    onReleaseProtection = { showReleaseConfirm = true },
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
                        onOpenTasks = { showTasks = true }
                    )
                }
            }
        }
    }

    if (showSettings) {
        FamilySettingsDialog(onDismiss = { showSettings = false })
    }

    if (showDeviceStatus) {
        DeviceStatusDialog(onDismiss = { showDeviceStatus = false })
    }

    if (showReleaseConfirm) {
        AlertDialog(
            onDismissRequest = { showReleaseConfirm = false },
            title = { Text("Разрешить удаление?") },
            text = {
                Text(
                    "Ребёнок сможет удалить приложение «Семейный помощник» со своего телефона. " +
                        "Слежение и ограничения перестанут работать, пока вы не установите приложение заново."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReleaseConfirm = false
                    viewModel.releaseChildProtection()
                }) { Text("Разрешить") }
            },
            dismissButton = {
                TextButton(onClick = { showReleaseConfirm = false }) { Text("Отмена") }
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
}

@Composable
private fun HomeContent(uiState: DashboardUiState, onAppClick: (AppRow) -> Unit, onOpenTasks: () -> Unit) {
    var selectedCategory by remember {
        mutableStateOf(
            when {
                uiState.blocked.isNotEmpty() -> AppCategory.BLOCKED
                uiState.timed.isNotEmpty() -> AppCategory.TIMED
                else -> AppCategory.UNRESTRICTED
            }
        )
    }
    val visibleApps = when (selectedCategory) {
        AppCategory.BLOCKED -> uiState.blocked
        AppCategory.TIMED -> uiState.timed
        AppCategory.UNRESTRICTED -> uiState.unrestricted
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (uiState.pendingTasksCount > 0) {
            item { TasksSummaryCard(count = uiState.pendingTasksCount, onClick = onOpenTasks) }
        }
        item {
            CategoryRibbon(
                blockedCount = uiState.blocked.size,
                timedCount = uiState.timed.size,
                freeCount = uiState.unrestricted.size,
                selected = selectedCategory,
                onSelect = { selectedCategory = it }
            )
        }
        if (visibleApps.isEmpty()) {
            item {
                Text(
                    text = "Список пуст.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        visibleApps.forEachIndexed { index, app ->
                            AppRow(app = app, onClick = { onAppClick(app) })
                            if (index != visibleApps.lastIndex) RowDivider()
                        }
                    }
                }
            }
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
            Text("✅", style = MaterialTheme.typography.titleLarge)
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
private fun CategoryRibbon(
    blockedCount: Int,
    timedCount: Int,
    freeCount: Int,
    selected: AppCategory,
    onSelect: (AppCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RibbonPill(
            label = "Заблокировано",
            count = blockedCount,
            fg = CategoryBlocked,
            bg = CategoryBlockedBg,
            selected = selected == AppCategory.BLOCKED,
            onClick = { onSelect(AppCategory.BLOCKED) }
        )
        RibbonPill(
            label = "По времени",
            count = timedCount,
            fg = CategoryTimed,
            bg = CategoryTimedBg,
            selected = selected == AppCategory.TIMED,
            onClick = { onSelect(AppCategory.TIMED) }
        )
        RibbonPill(
            label = "Без ограничений",
            count = freeCount,
            fg = CategoryFree,
            bg = CategoryFreeBg,
            selected = selected == AppCategory.UNRESTRICTED,
            onClick = { onSelect(AppCategory.UNRESTRICTED) }
        )
    }
}

@Composable
private fun RibbonPill(
    label: String,
    count: Int,
    fg: Color,
    bg: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) fg else bg),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
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
            AppIcon(packageName = app.packageName, label = app.appLabel, fg = fg, bg = bg)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.appLabel, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                if (app.category == AppCategory.TIMED) {
                    Text(
                        text = "${app.usedMinutesToday} из ${app.rule?.dailyLimitMinutes ?: 0} мин",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusIndicator(app = app, fg = fg)
        }
        val dailyLimit = app.rule?.dailyLimitMinutes
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
        AppCategory.TIMED -> "${app.rule?.dailyLimitMinutes ?: 0} мин"
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
private fun AppIcon(packageName: String, label: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    val spec = AppIconRegistry.forPackage(packageName)
    Box(
        modifier = modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(spec?.bg ?: bg),
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
            Text(
                text = label.trim().take(1).uppercase().ifBlank { "?" },
                color = fg,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun SettingsTabContent(
    onOpenFamilySettings: () -> Unit,
    onOpenDeviceStatus: () -> Unit,
    onReleaseProtection: () -> Unit,
    onSignOut: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SettingsRow(
                emoji = "👪",
                title = "Настройки семьи",
                subtitle = "Телефон для SOS, ночной режим",
                onClick = onOpenFamilySettings
            )
        }
        item {
            SettingsRow(
                emoji = "🩺",
                title = "Диагностика устройства ребёнка",
                subtitle = "Проверить, всё ли настроено правильно",
                onClick = onOpenDeviceStatus
            )
        }
        item {
            SettingsRow(
                emoji = "🔓",
                title = "Разрешить удаление приложения у ребёнка",
                subtitle = "Одноразовое разрешение на снятие защиты",
                onClick = onReleaseProtection
            )
        }
        item {
            SettingsRow(
                emoji = "🚪",
                title = "Выйти",
                subtitle = null,
                onClick = onSignOut
            )
        }
    }
}

@Composable
private fun SettingsRow(emoji: String, title: String, subtitle: String?, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
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
    val sheetState = rememberModalBottomSheetState()
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
                AppIcon(packageName = app.packageName, label = app.appLabel, fg = fg, bg = bg)
                Spacer(Modifier.width(12.dp))
                Text(text = app.appLabel, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ModeChip(
                    label = "Свободно",
                    selected = selectedMode == null,
                    fg = CategoryFree,
                    bg = CategoryFreeBg,
                    onClick = { selectedMode = null },
                    modifier = Modifier.weight(1f)
                )
                ModeChip(
                    label = "По времени",
                    selected = selectedMode == RuleMode.TIME_LIMIT,
                    fg = CategoryTimed,
                    bg = CategoryTimedBg,
                    onClick = { selectedMode = RuleMode.TIME_LIMIT },
                    modifier = Modifier.weight(1f)
                )
                ModeChip(
                    label = "Заблокировать",
                    selected = selectedMode == RuleMode.BLOCKED,
                    fg = CategoryBlocked,
                    bg = CategoryBlockedBg,
                    onClick = { selectedMode = RuleMode.BLOCKED },
                    modifier = Modifier.weight(1f)
                )
            }

            if (selectedMode == RuleMode.TIME_LIMIT) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { minutes = (minutes - 15).coerceAtLeast(15) }) {
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
                    IconButton(onClick = { minutes = (minutes + 15).coerceAtMost(600) }) {
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
private fun ModeChip(
    label: String,
    selected: Boolean,
    fg: Color,
    bg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) fg else bg),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) Color.White else fg,
                textAlign = TextAlign.Center
            )
        }
    }
}
