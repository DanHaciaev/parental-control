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

private val LABEL_HOURS = listOf(0, 6, 12, 18)

/** Single-series chart — one hue for completed hours, a lighter tint for the current in-progress hour.
 *  Tapping a bar shows its exact hour range and minute count above the chart, since the bars
 *  themselves are too thin to label individually. */
@Composable
fun HourlyBarChart(hourlyMinutes: List<Int>, currentHour: Int, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val currentHourColor = barColor.copy(alpha = 0.45f)
    val emptyColor = barColor.copy(alpha = 0.15f)
    val selectedColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    val density = LocalDensity.current
    val labelSizePx = with(density) { 11.sp.toPx() }
    val cornerRadiusPx = with(density) { 3.dp.toPx() }
    val gapPx = with(density) { 3.dp.toPx() }
    val emptyBarHeightPx = with(density) { 2.dp.toPx() }
    val barCount = hourlyMinutes.size

    var selectedHour by remember(hourlyMinutes) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        Text(
            text = selectedHour?.let { hour ->
                "%02d:00–%02d:00 · %d мин".format(hour, (hour + 1) % 24, hourlyMinutes.getOrElse(hour) { 0 })
            } ?: "Нажмите на столбик, чтобы увидеть время",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (selectedHour != null) 1f else 0.4f),
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
                        selectedHour = tapped
                    }
                }
        ) {
            val barWidth = (size.width - gapPx * (barCount - 1)) / barCount
            val maxMinutes = 60f

            for (hour in 0 until barCount) {
                val minutes = hourlyMinutes[hour]
                val heightFraction = (minutes / maxMinutes).coerceIn(0f, 1f)
                val barHeight = size.height * heightFraction
                val left = hour * (barWidth + gapPx)
                val color = when {
                    hour == selectedHour -> selectedColor
                    hour == currentHour -> currentHourColor
                    else -> barColor
                }

                if (barHeight > 0f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                } else {
                    drawRoundRect(
                        color = if (hour == selectedHour) selectedColor else emptyColor,
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
            LABEL_HOURS.forEach { hour ->
                val x = hour * (barWidth + gapPx)
                drawContext.canvas.nativeCanvas.drawText("%02d:00".format(hour), x, size.height * 0.8f, paint)
            }
        }
    }
}
