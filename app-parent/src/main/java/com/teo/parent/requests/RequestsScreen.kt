package com.teo.parent.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.AppRule
import com.teo.core.model.MoreTimeRequest
import com.teo.core.model.Task

@Composable
fun RequestsScreen(viewModel: RequestsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateTask by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateTask = true }) { Text("+") }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.moreTimeRequests.isEmpty() && uiState.tasksAwaitingApproval.isEmpty() && uiState.openTasks.isEmpty()) {
                Text(
                    text = "Пока нет запросов и заданий. Можно создать задание кнопкой «+».",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.moreTimeRequests, key = { "req_${it.id}" }) { request ->
                        MoreTimeRequestCard(
                            request = request,
                            onApprove = { minutes -> viewModel.approveRequest(request, minutes) },
                            onDeny = { viewModel.denyRequest(request) }
                        )
                    }
                    items(uiState.tasksAwaitingApproval, key = { "task_${it.id}" }) { task ->
                        TaskApprovalCard(
                            task = task,
                            onApprove = { viewModel.approveTask(task) },
                            onReject = { viewModel.rejectTask(task) }
                        )
                    }
                    items(uiState.openTasks, key = { "open_${it.id}" }) { task ->
                        OpenTaskCard(task = task)
                    }
                }
            }
        }
    }

    if (showCreateTask) {
        CreateTaskDialog(
            apps = uiState.timeLimitedApps,
            onDismiss = { showCreateTask = false },
            onCreate = { title, minutes, packageName ->
                viewModel.createTask(title, minutes, packageName)
                showCreateTask = false
            }
        )
    }
}

@Composable
private fun MoreTimeRequestCard(request: MoreTimeRequest, onApprove: (Int) -> Unit, onDeny: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Просит больше времени: ${request.packageName}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = { onApprove(15) }) { Text("+15 мин") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onDeny) { Text("Отказать") }
            }
        }
    }
}

@Composable
private fun TaskApprovalCard(task: Task, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = task.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Отметил(а) выполненным • награда ${task.rewardMinutes} мин",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = onApprove) { Text("Подтвердить") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onReject) { Text("Отклонить") }
            }
        }
    }
}

@Composable
private fun OpenTaskCard(task: Task) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = task.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Ждём выполнения • награда ${task.rewardMinutes} мин",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun CreateTaskDialog(
    apps: List<AppRule>,
    onDismiss: () -> Unit,
    onCreate: (String, Int, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("15") }
    var selectedApp by remember { mutableStateOf(apps.firstOrNull()) }
    var appMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Новое задание", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Например: убрать комнату") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { if (it.all(Char::isDigit) && it.length <= 4) minutesText = it },
                    label = { Text("Награда, минут") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                if (apps.isEmpty()) {
                    Text(
                        "Сначала установите лимит времени хотя бы на одно приложение — награда добавляется к нему.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Box {
                        OutlinedButton(onClick = { appMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedApp?.appLabel ?: "Выберите приложение")
                        }
                        DropdownMenu(expanded = appMenuExpanded, onDismissRequest = { appMenuExpanded = false }) {
                            apps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text(app.appLabel) },
                                    onClick = {
                                        selectedApp = app
                                        appMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onCreate(title, minutesText.toIntOrNull() ?: 15, selectedApp?.packageName) },
                        enabled = title.isNotBlank() && (apps.isEmpty() || selectedApp != null)
                    ) { Text("Создать") }
                }
            }
        }
    }
}
