package com.teo.child.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ChildTasksSection(viewModel: ChildTasksViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    val hasAnything = uiState.openTasks.isNotEmpty() || uiState.waitingApprovalTasks.isNotEmpty() || uiState.approvedCount > 0
    if (!hasAnything) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Задания от родителей", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (uiState.approvedCount > 0) {
                Text(text = "🔥 ${uiState.approvedCount}", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(8.dp))

        uiState.openTasks.forEach { task ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = task.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Награда: ${task.rewardMinutes} мин",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    Button(onClick = { viewModel.markDone(task) }) { Text("Готово") }
                }
            }
        }

        uiState.waitingApprovalTasks.forEach { task ->
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = task.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Ждём подтверждения от родителя",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }

        if (uiState.openTasks.isEmpty() && uiState.waitingApprovalTasks.isEmpty()) {
            Text(
                text = "Новых заданий пока нет — загляни попозже",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}
