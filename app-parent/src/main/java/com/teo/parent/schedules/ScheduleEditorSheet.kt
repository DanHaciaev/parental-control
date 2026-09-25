package com.teo.parent.schedules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.teo.core.model.InstalledApp
import com.teo.core.model.Schedule
import com.teo.parent.ui.theme.CategoryBlocked
import com.teo.parent.ui.theme.CategoryFree

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorSheet(
    schedule: Schedule,
    installedApps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(schedule.name) }
    var startMinutes by remember { mutableStateOf(schedule.startMinutes) }
    var endMinutes by remember { mutableStateOf(schedule.endMinutes) }
    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }
    var blockAllApps by remember { mutableStateOf(schedule.blockAllApps) }
    var selectedPackages by remember { mutableStateOf(schedule.blockedPackageNames.toSet()) }
    var selectedDays by remember {
        mutableStateOf(schedule.daysOfWeek.toSet().ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) })
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isNew = schedule.id.isBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                // Without this, the app-picker list below (up to 280dp) can push the Save/Delete/
                // Cancel buttons past the sheet's viewport with no way to reach them — confirmed
                // live: choosing "Выбрать приложения" made saving impossible.
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (isNew) "Новое расписание" else "Изменить расписание",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название (например, «Уроки»)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TimeField(
                    label = "Начало",
                    minutes = startMinutes,
                    onClick = { editingStart = true },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TimeField(
                    label = "Конец",
                    minutes = endMinutes,
                    onClick = { editingEnd = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Дни недели",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            DayOfWeekPicker(
                selectedDays = selectedDays,
                onToggleDay = { day ->
                    selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                }
            )

            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BlockModeOption(
                    label = "Блокировать всё",
                    description = "Телефон недоступен целиком (кроме звонков и камеры)",
                    selected = blockAllApps,
                    color = CategoryBlocked,
                    onClick = { blockAllApps = true }
                )
                BlockModeOption(
                    label = "Выбрать приложения",
                    description = "Недоступны только выбранные приложения",
                    selected = !blockAllApps,
                    color = CategoryFree,
                    onClick = { blockAllApps = false }
                )
            }

            if (!blockAllApps) {
                Spacer(Modifier.height(12.dp))
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(installedApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPackages = if (app.packageName in selectedPackages) {
                                            selectedPackages - app.packageName
                                        } else {
                                            selectedPackages + app.packageName
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = app.packageName in selectedPackages,
                                    onCheckedChange = { checked ->
                                        selectedPackages = if (checked) selectedPackages + app.packageName else selectedPackages - app.packageName
                                    }
                                )
                                Text(app.appLabel, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    onSave(
                        schedule.copy(
                            name = name.ifBlank { "Расписание" },
                            startMinutes = startMinutes,
                            endMinutes = endMinutes,
                            blockAllApps = blockAllApps,
                            blockedPackageNames = if (blockAllApps) emptyList() else selectedPackages.toList(),
                            daysOfWeek = selectedDays.sorted(),
                            enabled = true
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Сохранить") }

            if (!isNew) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Удалить расписание") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
    }

    if (editingStart) {
        TimePickerDialog(
            initialMinutes = startMinutes,
            onDismiss = { editingStart = false },
            onConfirm = { startMinutes = it; editingStart = false }
        )
    }
    if (editingEnd) {
        TimePickerDialog(
            initialMinutes = endMinutes,
            onDismiss = { editingEnd = false },
            onConfirm = { endMinutes = it; editingEnd = false }
        )
    }
}

/** A tap target showing the current time, not a text field — typing "ЧЧ:ММ" on a number-only
 *  keyboard meant deleting the ":" left no way to type it back in, since the numeric keypad has
 *  no colon key at all. A picker sidesteps that entirely. */
@Composable
private fun TimeField(label: String, minutes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(text = formatMinutesOfDay(minutes), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private val DAY_LABELS = listOf(1 to "Пн", 2 to "Вт", 3 to "Ср", 4 to "Чт", 5 to "Пт", 6 to "Сб", 7 to "Вс")

@Composable
private fun DayOfWeekPicker(selectedDays: Set<Int>, onToggleDay: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DAY_LABELS.forEach { (day, label) ->
            val selected = day in selectedDays
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) CategoryBlocked else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onToggleDay(day) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Готово") }
                }
            }
        }
    }
}

@Composable
private fun BlockModeOption(
    label: String,
    description: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(12.dp).clip(CircleShape).background(color)
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
                Text(text = "✓", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
}
