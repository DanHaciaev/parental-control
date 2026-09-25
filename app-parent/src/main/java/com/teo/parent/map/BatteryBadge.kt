package com.teo.parent.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teo.core.model.DeviceStatus
import com.teo.parent.ui.theme.SoftAlert
import com.teo.parent.util.timeAgo

private const val LOW_BATTERY_THRESHOLD = 15

@Composable
fun BatteryBadge(status: DeviceStatus?, modifier: Modifier = Modifier) {
    if (status == null) return
    val low = status.batteryPercent <= LOW_BATTERY_THRESHOLD && !status.isCharging

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (status.isCharging) "⚡" else if (low) "🪫" else "🔋")
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    text = "${status.batteryPercent}%",
                    fontWeight = FontWeight.SemiBold,
                    color = if (low) SoftAlert else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = timeAgo(status.lastSeenAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
