package com.teo.parent.dashboard

import com.teo.core.ui.components.CompassWatermark
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teo.core.R as CoreR
import com.teo.core.model.Device
import com.teo.core.model.DeviceStatus
import com.teo.core.ui.components.HeaderIconButton
import com.teo.core.ui.components.HeaderPill
import com.teo.core.ui.components.IconTile
import com.teo.core.ui.components.InitialAvatar
import com.teo.core.ui.components.NestCard
import com.teo.core.ui.components.NestHeader
import com.teo.core.ui.components.NestIcon
import com.teo.core.ui.components.ProgressRing
import com.teo.core.ui.components.RowDivider
import com.teo.core.ui.components.SectionTitle
import com.teo.core.ui.components.StatusText
import com.teo.core.ui.components.overlapAbove
import com.teo.core.ui.theme.NestColors
import com.teo.parent.auth.ChildSummary
import com.teo.parent.devices.DeviceCard
import com.teo.parent.util.timeAgo

/** The child switcher on the navy header — pill with the selected child, menu of the others. */
@Composable
fun ChildSwitcher(
    childName: String,
    children: List<ChildSummary>,
    selectedFamilyId: String?,
    onSelectChild: (String) -> Unit,
    onAddChild: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderPill(onClick = { open = true }) {
            InitialAvatar(childName)
            Text(childName, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            NestIcon(CoreR.drawable.nest_ic_chevron_down, tint = Color.White, size = 16.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            children.forEach { child ->
                val selected = child.familyId == selectedFamilyId
                DropdownMenuItem(
                    leadingIcon = { InitialAvatar(child.name, size = 28.dp, background = NestColors.NavyTint, color = NestColors.Navy) },
                    text = { Text(child.name, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium) },
                    trailingIcon = if (selected) {
                        { NestIcon(CoreR.drawable.nest_ic_circle_check, tint = NestColors.Navy, size = 18.dp) }
                    } else null,
                    onClick = {
                        open = false
                        onSelectChild(child.familyId)
                    }
                )
            }
            HorizontalDivider(color = NestColors.Divider)
            DropdownMenuItem(
                leadingIcon = { NestIcon(CoreR.drawable.nest_ic_user_plus, tint = NestColors.Navy, size = 20.dp) },
                text = { Text("Добавить ребёнка") },
                onClick = {
                    open = false
                    onAddChild()
                }
            )
        }
    }
}

/** Header for the tabs other than Главная: the switcher plus the tab's name. */
@Composable
fun TabHeader(title: String, switcher: @Composable () -> Unit) {
    NestHeader(bottomPadding = 18.dp, compact = true) {
        switcher()
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
    }
}

@Composable
fun HomeContent(
    uiState: DashboardUiState,
    switcher: @Composable () -> Unit,
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                HomeHeader(uiState, switcher, onOpenEvents, onOpenStatistics, onOpenFamilySettings)
                StatusCard(
                    status = uiState.deviceStatus,
                    modifier = Modifier.padding(horizontal = 20.dp).overlapAbove(40.dp)
                )
            }
        }

        if (uiState.tamperAlert) {
            item { TamperAlertCard(onClick = onOpenEvents, modifier = Modifier.padding(horizontal = 20.dp)) }
        }

        item {
            Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShortcutCard(
                    icon = CoreR.drawable.nest_ic_square_check,
                    title = "Задания",
                    subtitle = if (uiState.pendingTasksCount > 0) "${uiState.pendingTasksCount} на проверке" else "Нет новых",
                    onClick = onOpenTasks,
                    modifier = Modifier.weight(1f)
                )
                ShortcutCard(
                    icon = CoreR.drawable.nest_ic_moon,
                    title = "Расписания",
                    subtitle = uiState.activeScheduleName?.let { "Сейчас: $it" } ?: "Сон, уроки",
                    onClick = onOpenSchedules,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        items(uiState.devices, key = { it.id }) { device ->
            Box(Modifier.padding(horizontal = 20.dp)) { DeviceCard(device = device, onClick = { onDeviceClick(device) }) }
        }

        appSection("Заблокировано", uiState.blocked, onAppClick) { onShowAllCategory(AppCategory.BLOCKED) }
        appSection("По времени", uiState.timed, onAppClick) { onShowAllCategory(AppCategory.TIMED) }
        appSection("Без ограничений", uiState.unrestricted, onAppClick) { onShowAllCategory(AppCategory.UNRESTRICTED) }
    }
}

/** Navy header: switcher, bell, and the ring — today's time against the total cap when one is
 *  set, otherwise just today's total. */
@Composable
private fun HomeHeader(
    uiState: DashboardUiState,
    switcher: @Composable () -> Unit,
    onOpenEvents: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenFamilySettings: () -> Unit
) {
    val cap = uiState.totalScreenTimeCapMinutes
    val counted = if (cap != null) uiState.timed.sumOf { it.usedMinutesToday } else uiState.apps.sumOf { it.usedMinutesToday }
    // Watermark drawn behind the ring (same centre) rather than across the whole header — two
    // sets of circles with different centres made the ring look off-centre.
    NestHeader(showWatermark = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { switcher() }
            HeaderIconButton(CoreR.drawable.nest_ic_bell, contentDescription = "Журнал событий", onClick = onOpenEvents)
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CompassWatermark(Modifier.requiredSize(300.dp))
        ProgressRing(
            progress = if (cap != null && cap > 0) counted.toFloat() / cap else 0f,
            color = if (cap != null && counted >= cap) Color(0xFFF0A79E) else NestColors.SilverLight,
            modifier = Modifier
                .clip(RoundedCornerShape(105.dp))
                .clickable(onClickLabel = "Статистика", onClick = onOpenStatistics)
        ) {
            Text("СЕГОДНЯ", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 2), color = NestColors.OnNavyMuted)
            Text(formatHm(counted), style = MaterialTheme.typography.displayMedium, color = Color.White)
            Text(
                if (cap != null) "из ${formatHm(cap)}" else "без общего лимита",
                style = MaterialTheme.typography.bodyMedium,
                color = NestColors.Silver
            )
        }
        }
        if (cap == null) {
            TextButton(onClick = onOpenFamilySettings, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Задать общий лимит", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** White card overlapping the header: battery, connection, what's open right now. */
@Composable
private fun StatusCard(status: DeviceStatus?, modifier: Modifier = Modifier) {
    NestCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp)) {
        Row {
            StatusCell(
                icon = CoreR.drawable.nest_ic_battery_medium,
                value = status?.let { "${it.batteryPercent}%" } ?: "—",
                label = if (status?.isCharging == true) "заряжается" else "батарея",
                tint = if (status != null && status.batteryPercent <= 15 && !status.isCharging) NestColors.Danger else NestColors.Navy,
                modifier = Modifier.weight(1f)
            )
            CellDivider()
            StatusCell(
                icon = CoreR.drawable.nest_ic_wifi,
                value = if (status?.lastSeenAt != null) "на связи" else "—",
                label = timeAgo(status?.lastSeenAt),
                tint = NestColors.Success,
                modifier = Modifier.weight(1f)
            )
            CellDivider()
            StatusCell(
                icon = CoreR.drawable.nest_ic_smartphone,
                value = status?.currentForegroundAppLabel ?: "—",
                label = "открыто сейчас",
                tint = NestColors.Navy,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusCell(icon: Int, value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        NestIcon(icon, tint = tint, size = 22.dp)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing), color = NestColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CellDivider() {
    Box(Modifier.width(1.dp).height(56.dp).background(NestColors.Divider))
}

@Composable
private fun ShortcutCard(icon: Int, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    NestCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NestColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TamperAlertCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    NestCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(CoreR.drawable.nest_ic_triangle_alert, tint = NestColors.Danger, background = NestColors.DangerTint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Защита была отключена", style = MaterialTheme.typography.titleSmall, color = NestColors.Danger)
                Text("На телефоне ребёнка выключили защиту от удаления", style = MaterialTheme.typography.bodySmall, color = NestColors.InkSecondary)
            }
            NestIcon(CoreR.drawable.nest_ic_chevron_right, tint = NestColors.InkSecondary, size = 18.dp)
        }
    }
}

private const val APP_SECTION_PREVIEW_LIMIT = 5

private fun LazyListScope.appSection(title: String, apps: List<AppRow>, onAppClick: (AppRow) -> Unit, onShowAll: () -> Unit) {
    if (apps.isEmpty()) return
    item {
        SectionTitle(
            "$title · ${apps.size}",
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 6.dp),
            actionLabel = if (apps.size > APP_SECTION_PREVIEW_LIMIT) "Все" else null,
            onAction = onShowAll
        )
    }
    item {
        val preview = apps.take(APP_SECTION_PREVIEW_LIMIT)
        NestCard(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            preview.forEachIndexed { index, app ->
                if (index > 0) RowDivider()
                AppListRow(app = app, onClick = { onAppClick(app) })
            }
        }
    }
}

/** One app: icon, name, today's use against the limit, status as icon + coloured text. */
@Composable
fun AppListRow(app: AppRow, onClick: () -> Unit) {
    val (fg, bg) = when (app.category) {
        AppCategory.BLOCKED -> NestColors.Danger to NestColors.DangerTint
        AppCategory.TIMED -> NestColors.Navy to NestColors.NavyTint
        AppCategory.UNRESTRICTED -> NestColors.Success to NestColors.SuccessTint
    }
    val limit = app.effectiveDailyLimitMinutes
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = app.packageName, label = app.appLabel, fg = fg, bg = bg, iconBase64 = app.iconBase64)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(app.appLabel, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (app.category == AppCategory.TIMED && limit != null && limit > 0) {
                val progress = (app.usedMinutesToday.toFloat() / limit).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (progress >= 1f) NestColors.Danger else NestColors.Navy,
                    trackColor = NestColors.Divider,
                    drawStopIndicator = {}
                )
            } else if (app.usedMinutesToday > 0) {
                Text("сегодня ${app.usedMinutesToday} мин", style = MaterialTheme.typography.bodySmall, color = NestColors.InkSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        when (app.category) {
            AppCategory.BLOCKED -> StatusText("Закрыто", NestColors.Danger, icon = CoreR.drawable.nest_ic_lock)
            AppCategory.TIMED -> Text(
                "${app.usedMinutesToday} / ${limit ?: 0} мин" + if (app.bonusMinutesToday > 0) " · +${app.bonusMinutesToday}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = NestColors.Ink
            )
            AppCategory.UNRESTRICTED -> StatusText("Свободно", NestColors.Success)
        }
    }
}

private fun formatHm(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)
