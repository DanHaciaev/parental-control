package com.teo.parent.events

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.EventLogEntry
import com.teo.core.model.EventType

@Composable
fun EventsScreen(viewModel: EventsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            uiState.events.isEmpty() -> Text(
                text = "Пока нет уведомлений",
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.events, key = { it.id }) { event ->
                    EventCard(event = event, onSeen = { viewModel.markRead(event) })
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: EventLogEntry, onSeen: () -> Unit) {
    LaunchedEffect(event.id) { onSeen() }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (event.read) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Text(text = eventEmoji(event.type), style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = event.message, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun eventEmoji(type: EventType): String = when (type) {
    EventType.NEW_INSTALL -> "📦"
    EventType.LIMIT_REACHED -> "⏰"
    EventType.UNINSTALL_ATTEMPT_BLOCKED -> "🛡️"
    EventType.LOW_BATTERY -> "🔋"
    EventType.SOS -> "🆘"
    EventType.GEOFENCE -> "📍"
}
