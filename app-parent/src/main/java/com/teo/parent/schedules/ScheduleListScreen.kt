package com.teo.parent.schedules

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.Schedule
import com.teo.parent.R
import com.teo.parent.dashboard.EmojiIcon

@Composable
fun ScheduleListScreen(viewModel: ScheduleViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<Schedule?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            uiState.schedules.isEmpty() -> Text(
                text = "Пока нет расписаний. Нажмите +, чтобы добавить, например, «Сон» или «Уроки».",
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.schedules, key = { it.id }) { schedule ->
                    ScheduleRow(
                        schedule = schedule,
                        onClick = { editing = schedule },
                        onToggle = { enabled -> viewModel.toggleEnabled(schedule, enabled) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { editing = Schedule() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Text("+") }
    }

    editing?.let { schedule ->
        ScheduleEditorSheet(
            schedule = schedule,
            installedApps = uiState.installedApps,
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null },
            onDelete = { viewModel.delete(schedule.id); editing = null }
        )
    }
}

@Composable
private fun ScheduleRow(schedule: Schedule, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmojiIcon(R.drawable.ic_emoji_moon, size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(schedule.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${formatMinutesOfDay(schedule.startMinutes)}–${formatMinutesOfDay(schedule.endMinutes)} · " +
                        "${formatDays(schedule.daysOfWeek)} · " +
                        if (schedule.blockAllApps) "Все приложения" else "${schedule.blockedPackageNames.size} приложений",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = schedule.enabled, onCheckedChange = onToggle)
        }
    }
}

internal fun formatMinutesOfDay(total: Int): String = "%02d:%02d".format(total / 60, total % 60)

private val SHORT_DAY_NAMES = mapOf(1 to "Пн", 2 to "Вт", 3 to "Ср", 4 to "Чт", 5 to "Пт", 6 to "Сб", 7 to "Вс")

private fun formatDays(daysOfWeek: List<Int>): String {
    val days = daysOfWeek.toSet()
    return when {
        days.isEmpty() || days == (1..7).toSet() -> "Ежедневно"
        days == (1..5).toSet() -> "По будням"
        days == setOf(6, 7) -> "По выходным"
        else -> days.sorted().mapNotNull { SHORT_DAY_NAMES[it] }.joinToString(", ")
    }
}
