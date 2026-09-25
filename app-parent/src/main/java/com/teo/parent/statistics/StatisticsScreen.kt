package com.teo.parent.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.parent.dashboard.AppIcon
import com.teo.parent.ui.theme.CategoryFree
import com.teo.parent.ui.theme.CategoryFreeBg
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class PresetOption(val preset: StatsPreset, val label: String)

private val PRESETS = listOf(
    PresetOption(StatsPreset.TODAY, "Сегодня"),
    PresetOption(StatsPreset.THIS_WEEK, "Эта неделя"),
    PresetOption(StatsPreset.LAST_WEEK, "Прошлая неделя"),
    PresetOption(StatsPreset.THIS_MONTH, "Этот месяц"),
    PresetOption(StatsPreset.LAST_MONTH, "Прошлый месяц")
)

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedPreset by remember { mutableStateOf<StatsPreset?>(StatsPreset.TODAY) }
    var showCustomRangePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PRESETS.forEach { option ->
                FilterChip(
                    selected = selectedPreset == option.preset,
                    onClick = {
                        selectedPreset = option.preset
                        viewModel.selectPreset(option.preset)
                    },
                    label = { Text(option.label) }
                )
            }
            FilterChip(
                selected = selectedPreset == null,
                onClick = { showCustomRangePicker = true },
                label = { Text("Свой период") }
            )
        }

        if (uiState.loading) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column {
                        Text(
                            text = uiState.rangeLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formatTotal(uiState.totalMinutesToday),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        if (uiState.isSingleDay) {
                            HourlyBarChart(
                                hourlyMinutes = uiState.hourlyMinutes,
                                currentHour = uiState.currentHour,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        } else {
                            DailyBarChart(
                                dailyTotals = uiState.dailyTotals,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }
                }
                if (uiState.appBreakdown.isEmpty()) {
                    item {
                        Text(
                            text = "Пока нет данных об использовании приложений.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    item {
                        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                            Column {
                                uiState.appBreakdown.forEachIndexed { index, app ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIcon(
                                            packageName = app.packageName,
                                            label = app.appLabel,
                                            fg = CategoryFree,
                                            bg = CategoryFreeBg
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = app.appLabel,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(text = formatTotal(app.minutesUsedToday), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (index != uiState.appBreakdown.lastIndex) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .padding(start = 60.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomRangePicker) {
        CustomRangeDialog(
            onDismiss = { showCustomRangePicker = false },
            onConfirm = { from, to ->
                selectedPreset = null
                viewModel.selectCustomRange(from, to)
                showCustomRangePicker = false
            }
        )
    }
}

private val WEEKDAY_HEADERS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/**
 * A hand-rolled month-grid range picker — Material3's DateRangePicker is built for tablet-width
 * dialogs and, even in a full-width Dialog, renders its own header text ("Конечная дата") wrapped
 * one letter per line in phone portrait. Full control over layout sidesteps that entirely instead
 * of fighting the component's internal sizing.
 */
@Composable
private fun CustomRangeDialog(onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var rangeStart by remember { mutableStateOf<LocalDate?>(null) }
    var rangeEnd by remember { mutableStateOf<LocalDate?>(null) }
    val today = remember { LocalDate.now() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Выберите период", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = rangeSummaryText(rangeStart, rangeEnd),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                        Text("‹", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = displayedMonth
                            .format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru")))
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    WEEKDAY_HEADERS.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val firstOfMonth = displayedMonth.atDay(1)
                val leadingBlanks = firstOfMonth.dayOfWeek.value - 1 // Monday=1..Sunday=7 -> 0..6
                val daysInMonth = displayedMonth.lengthOfMonth()
                val totalRows = (leadingBlanks + daysInMonth + 6) / 7

                for (row in 0 until totalRows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until 7) {
                            val dayNum = row * 7 + col - leadingBlanks + 1
                            if (dayNum in 1..daysInMonth) {
                                val date = displayedMonth.atDay(dayNum)
                                DayCell(
                                    date = date,
                                    isStart = date == rangeStart,
                                    isEnd = date == rangeEnd,
                                    inRange = rangeStart != null && rangeEnd != null &&
                                        date.isAfter(rangeStart) && date.isBefore(rangeEnd),
                                    disabled = date.isAfter(today),
                                    onClick = {
                                        when {
                                            rangeStart == null || rangeEnd != null -> {
                                                rangeStart = date
                                                rangeEnd = null
                                            }
                                            date.isBefore(rangeStart) -> {
                                                rangeEnd = rangeStart
                                                rangeStart = date
                                            }
                                            else -> rangeEnd = date
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    TextButton(
                        onClick = { rangeStart?.let { from -> onConfirm(from, rangeEnd ?: from) } },
                        enabled = rangeStart != null
                    ) { Text("Готово") }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isStart: Boolean,
    isEnd: Boolean,
    inRange: Boolean,
    disabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEdge = isStart || isEnd
    val background = when {
        isEdge -> MaterialTheme.colorScheme.primary
        inRange -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val textColor = when {
        isEdge -> MaterialTheme.colorScheme.onPrimary
        disabled -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.onBackground
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(background)
            .let { if (disabled) it else it.clickable(onClick = onClick) },
        contentAlignment = Alignment.Center
    ) {
        Text(text = date.dayOfMonth.toString(), color = textColor, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun rangeSummaryText(start: LocalDate?, end: LocalDate?): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
    return when {
        start == null -> "Выберите начальную дату"
        end == null -> "${start.format(formatter)} — выберите конечную дату"
        else -> "${start.format(formatter)} – ${end.format(formatter)}"
    }
}

private fun formatTotal(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}ч ${minutes}мин" else "${minutes}мин"
}
