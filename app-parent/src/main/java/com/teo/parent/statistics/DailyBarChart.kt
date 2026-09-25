package com.teo.parent.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Per-day totals bar (X-axis = days in the selected range) — the multi-day counterpart of
 *  [HourlyBarChart], used whenever the statistics range spans more than a single day. Tapping a
 *  bar shows its exact date and minute count above the chart, since with a month's worth of bars
 *  most of them are too thin to label individually. */
@Composable
fun DailyBarChart(dailyTotals: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = barColor.copy(alpha = 0.15f)
    val selectedColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    val density = LocalDensity.current
    val labelSizePx = with(density) { 11.sp.toPx() }
    val cornerRadiusPx = with(density) { 3.dp.toPx() }
    val gapPx = with(density) { 2.dp.toPx() }
    val emptyBarHeightPx = with(density) { 2.dp.toPx() }
    val dayFormatter = DateTimeFormatter.ofPattern("d")
    val fullDateFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))

    if (dailyTotals.isEmpty()) return

    // At most ~6 date labels regardless of range length — a 31-day month would otherwise overlap
    // into an unreadable smear of text.
    val labelStep = (dailyTotals.size / 6).coerceAtLeast(1)
    val maxMinutes = (dailyTotals.maxOf { it.second }).coerceAtLeast(1)
    val barCount = dailyTotals.size

    var selectedIndex by remember(dailyTotals) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        val selectedLabel = selectedIndex?.let { index ->
            val (dateKey, minutes) = dailyTotals[index]
            val dateLabel = runCatching { LocalDate.parse(dateKey).format(fullDateFormatter) }.getOrDefault(dateKey)
            "$dateLabel · $minutes мин"
        }
        Text(
            text = selectedLabel ?: "Нажмите на столбик, чтобы увидеть дату",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (selectedLabel != null) 1f else 0.4f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(barCount) {
                    detectTapGestures { offset ->
                        if (barCount == 0) return@detectTapGestures
                        val barWidth = (size.width - gapPx * (barCount - 1)) / barCount
                        val tapped = (offset.x / (barWidth + gapPx)).toInt().coerceIn(0, barCount - 1)
                        selectedIndex = tapped
                    }
                }
        ) {
            val barWidth = (size.width - gapPx * (barCount - 1)) / barCount

            dailyTotals.forEachIndexed { index, (_, minutes) ->
                val heightFraction = (minutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
                val barHeight = size.height * heightFraction
                val left = index * (barWidth + gapPx)
                val color = if (index == selectedIndex) selectedColor else barColor

                if (barHeight > 0f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                } else {
                    drawRoundRect(
                        color = if (index == selectedIndex) selectedColor else emptyColor,
                        topLeft = Offset(left, size.height - emptyBarHeightPx),
                        size = Size(barWidth, emptyBarHeightPx),
                        cornerRadius = CornerRadius(cornerRadiusPx / 2, cornerRadiusPx / 2)
                    )
                }
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val barWidth = (size.width - gapPx * (barCount - 1)) / barCount
            val paint = android.graphics.Paint().apply {
                color = labelColor.toArgb()
                textSize = labelSizePx
                isAntiAlias = true
            }
            dailyTotals.forEachIndexed { index, (dateKey, _) ->
                if (index % labelStep != 0) return@forEachIndexed
                val label = runCatching { LocalDate.parse(dateKey).format(dayFormatter) }.getOrDefault(dateKey)
                val x = index * (barWidth + gapPx)
                drawContext.canvas.nativeCanvas.drawText(label, x, size.height * 0.8f, paint)
            }
        }
    }
}
