package com.teo.parent.weeklylimits

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.Family
import com.teo.parent.R
import com.teo.parent.dashboard.AppIcon
import com.teo.parent.dashboard.EmojiIcon
import com.teo.parent.dashboard.AppRow
import com.teo.parent.dashboard.AppSearchField
import com.teo.parent.dashboard.filterByAppSearch
import com.teo.parent.ui.theme.CategoryTimed
import com.teo.parent.ui.theme.CategoryTimedBg

private val DAY_LABELS = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
private const val DEFAULT_MINUTES = 60

/** Day-first: pick a day of the week, then set/adjust each app's limit for that specific day —
 *  the reverse of the app-first flow, per explicit request (setting several apps for one day at a
 *  time is the more natural mental model than one app across all 7 days). The overall cap across
 *  all timed apps lives on the day-LIST screen (one stepper per day row), not inside a day, since
 *  it's a single family-wide number rather than something you pick an app for. */
/** [selectedDay]/[onSelectDayChange] are hoisted up to the Dashboard overlay instead of being
 *  local state — its top-bar back *arrow* (a separate button from the phone's back gesture/
 *  [BackHandler] below) needs to know whether a day is open so one tap steps back to the day
 *  list instead of always closing the whole section. */
@Composable
fun WeeklyLimitsScreen(
    selectedDay: Int?,
    onSelectDayChange: (Int?) -> Unit,
    viewModel: WeeklyLimitsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val day = selectedDay

    BackHandler(enabled = day != null) { onSelectDayChange(null) }

    if (day == null) {
        DayListContent(
            apps = uiState.apps,
            family = uiState.family,
            onDayClick = { onSelectDayChange(it) },
            onChangeTotalCap = { d, minutes -> viewModel.setDayTotalCap(d, minutes) },
            onClearTotalCap = { d -> viewModel.clearDayTotalCap(d) }
        )
    } else {
        DayAppsContent(
            isoDayOfWeek = day,
            apps = uiState.apps,
            loading = uiState.loading,
            onChange = { app, minutes -> viewModel.setDayLimit(app, day, minutes) },
            onClear = { app -> viewModel.clearDayLimit(app, day) }
        )
    }
}

@Composable
private fun DayListContent(
    apps: List<AppRow>,
    family: Family?,
    onDayClick: (Int) -> Unit,
    onChangeTotalCap: (Int, Int) -> Unit,
    onClearTotalCap: (Int) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(7) { index ->
            val day = index + 1
            val configuredCount = apps.count { it.rule?.weeklyLimitMinutes?.containsKey(day.toString()) == true }
            DayRow(
                label = DAY_LABELS[index],
                subtitle = if (configuredCount > 0) {
                    "Лимиты заданы для $configuredCount из ${apps.size} приложений"
                } else {
                    "Лимиты не заданы"
                },
                totalCap = family?.weeklyTotalCapMinutes?.get(day.toString()),
                totalCapFallback = family?.totalScreenTimeCapMinutes,
                onNavigate = { onDayClick(day) },
                onChangeTotalCap = { minutes -> onChangeTotalCap(day, minutes) },
                onClearTotalCap = { onClearTotalCap(day) }
            )
        }
    }
}

@Composable
private fun DayRow(
    label: String,
    subtitle: String,
    totalCap: Int?,
    totalCapFallback: Int?,
    onNavigate: () -> Unit,
    onChangeTotalCap: (Int) -> Unit,
    onClearTotalCap: () -> Unit
) {
    val currentCap = totalCap ?: totalCapFallback ?: DEFAULT_MINUTES
    val hasExplicitCap = totalCap != null
    var showCapEditor by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigate)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            }
            Text("$currentCap мин", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { showCapEditor = true }) {
                EmojiIcon(R.drawable.ic_emoji_pencil, size = 20.dp)
            }
        }
    }

    if (showCapEditor) {
        TotalCapEditorSheet(
            dayLabel = label,
            currentCap = currentCap,
            hasExplicitCap = hasExplicitCap,
            onDismiss = { showCapEditor = false },
            onChange = onChangeTotalCap,
            onClear = { onClearTotalCap(); showCapEditor = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotalCapEditorSheet(
    dayLabel: String,
    currentCap: Int,
    hasExplicitCap: Boolean,
    onDismiss: () -> Unit,
    onChange: (Int) -> Unit,
    onClear: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Общий лимит на отвлекающие приложения", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$dayLabel · ${if (hasExplicitCap) "задано для этого дня" else "как в остальные дни (по умолчанию)"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(16.dp))
            MinutesStepper(minutes = currentCap, showClear = hasExplicitCap, onChange = onChange, onClear = onClear, expanded = true)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
        }
    }
}

@Composable
private fun DayAppsContent(
    isoDayOfWeek: Int,
    apps: List<AppRow>,
    loading: Boolean,
    onChange: (AppRow, Int) -> Unit,
    onClear: (AppRow) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = apps.filterByAppSearch(query)

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = DAY_LABELS[isoDayOfWeek - 1],
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        if (!loading && apps.isNotEmpty()) {
            AppSearchField(query = query, onQueryChange = { query = it })
        }

        when {
            loading -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            apps.isEmpty() -> Box(Modifier.fillMaxSize()) {
                Text(
                    text = "Пока нет данных об установленных приложениях.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize()) {
                Text(
                    text = "Ничего не найдено",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    DayAppRow(
                        app = app,
                        isoDayOfWeek = isoDayOfWeek,
                        onChange = { minutes -> onChange(app, minutes) },
                        onClear = { onClear(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayAppRow(app: AppRow, isoDayOfWeek: Int, onChange: (Int) -> Unit, onClear: () -> Unit) {
    val explicitMinutes = app.rule?.weeklyLimitMinutes?.get(isoDayOfWeek.toString())
    val fallbackMinutes = app.rule?.dailyLimitMinutes
    val currentMinutes = explicitMinutes ?: fallbackMinutes ?: DEFAULT_MINUTES
    val hasExplicitOverride = explicitMinutes != null

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = app.packageName, label = app.appLabel, fg = CategoryTimed, bg = CategoryTimedBg, iconBase64 = app.iconBase64)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.appLabel, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = when {
                            hasExplicitOverride -> "Задано для этого дня"
                            fallbackMinutes != null -> "Как в остальные дни (по умолчанию)"
                            else -> "Пока без ограничений"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            MinutesStepper(
                minutes = currentMinutes,
                showClear = hasExplicitOverride,
                onChange = onChange,
                onClear = onClear
            )
        }
    }
}

@Composable
private fun MinutesStepper(
    minutes: Int,
    showClear: Boolean,
    onChange: (Int) -> Unit,
    onClear: () -> Unit,
    expanded: Boolean = false
) {
    if (expanded) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showClear) {
                TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) { Text("Сбросить") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { onChange((minutes - 5).coerceAtLeast(5)) }) {
                    Text("–", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    text = "$minutes мин",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { onChange((minutes + 5).coerceAtMost(600)) }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (showClear) {
            TextButton(onClick = onClear) { Text("Сбросить") }
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = { onChange((minutes - 5).coerceAtLeast(5)) }) {
            Text("–", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = "$minutes мин",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(76.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = { onChange((minutes + 5).coerceAtMost(600)) }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
