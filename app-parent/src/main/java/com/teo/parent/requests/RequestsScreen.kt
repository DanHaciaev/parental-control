package com.teo.parent.requests

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.AppRule
import com.teo.core.model.MoreTimeRequest
import com.teo.core.model.SkillTemplate
import com.teo.core.model.SkillTemplateCatalog
import com.teo.core.model.Task
import com.teo.parent.dashboard.AppSearchField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CATEGORY_FILTERS = listOf(null, SkillTemplateCatalog.CATEGORY_SKILLS, SkillTemplateCatalog.CATEGORY_ACTIVITY, SkillTemplateCatalog.CATEGORY_CHORES)

private fun categoryFilterLabel(category: String?): String = category ?: "Все"

@Composable
fun RequestsScreen(viewModel: RequestsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateTask by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<SkillTemplate?>(null) }
    var skillSearchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var fullScreenPhoto by remember { mutableStateOf<String?>(null) }

    if (showCreateTask) {
        CreateTaskScreen(
            apps = uiState.timeLimitedApps,
            onClose = { showCreateTask = false },
            onCreate = { title, minutes, packageName, photoBytes, linkUrl ->
                viewModel.createTask(title, minutes, packageName, photoBytes, linkUrl)
                showCreateTask = false
            }
        )
        return
    }

    Scaffold(
        // This Scaffold is nested inside DashboardScreen's own Scaffold, which already reserves
        // space for the status bar and bottom nav bar — without this, Material3's default here
        // reserves system-bar insets a second time, showing up as blank white bands above and
        // below the content (confirmed live on this screen).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateTask = true }) { Text("+") }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                    items(uiState.skillsAwaitingApproval, key = { "skill_approve_${it.assigned.id}" }) { row ->
                        SkillApprovalCard(row = row, onApprove = { viewModel.confirmSkill(row) })
                    }
                    items(uiState.tasksAwaitingApproval, key = { "task_${it.id}" }) { task ->
                        TaskApprovalCard(
                            task = task,
                            onApprove = { viewModel.approveTask(task) },
                            onReject = { viewModel.rejectTask(task) },
                            onPhotoClick = { fullScreenPhoto = task.photoBase64 }
                        )
                    }

                    item {
                        Text(
                            text = "Готовые задания",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    item {
                        AppSearchField(
                            query = skillSearchQuery,
                            onQueryChange = { skillSearchQuery = it },
                            placeholder = "Поиск задания",
                            modifier = Modifier.padding(horizontal = 0.dp)
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CATEGORY_FILTERS.forEach { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = { Text(categoryFilterLabel(category)) }
                                )
                            }
                        }
                    }

                    if (skillSearchQuery.isBlank()) {
                        val categoriesToShow = if (selectedCategory != null) listOf(selectedCategory) else CATEGORY_FILTERS.drop(1)
                        categoriesToShow.forEach { category ->
                            val templates = SkillTemplateCatalog.all.filter { it.category == category }
                            item {
                                SkillCategoryCarousel(
                                    category = category!!,
                                    templates = templates,
                                    assignedTemplateIds = uiState.assignedTemplateIds,
                                    onTemplateClick = { editingTemplate = it }
                                )
                            }
                        }
                    } else {
                        val filtered = SkillTemplateCatalog.all.filter {
                            (selectedCategory == null || it.category == selectedCategory) &&
                                it.title.contains(skillSearchQuery, ignoreCase = true)
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    text = "Ничего не найдено",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            items(filtered, key = { "tpl_${it.id}" }) { template ->
                                SkillTemplateListRow(
                                    template = template,
                                    assigned = template.id in uiState.assignedTemplateIds,
                                    onClick = { editingTemplate = template }
                                )
                            }
                        }
                    }

                    items(uiState.openTasks, key = { "open_${it.id}" }) { task ->
                        OpenTaskCard(task = task, onPhotoClick = { fullScreenPhoto = task.photoBase64 })
                    }
                    items(uiState.skillsInProgress, key = { "skill_progress_${it.assigned.id}" }) { row ->
                        SkillProgressCard(row = row)
                    }
                }
            }
        }
    }

    editingTemplate?.let { template ->
        AssignSkillSheet(
            template = template,
            alreadyAssigned = template.id in uiState.assignedTemplateIds,
            apps = uiState.timeLimitedApps,
            onDismiss = { editingTemplate = null },
            onAssign = { target, reward, packageName ->
                viewModel.assignSkill(template, target, reward, packageName)
                editingTemplate = null
            },
            onUnassign = {
                viewModel.unassignSkill(template.id)
                editingTemplate = null
            }
        )
    }

    fullScreenPhoto?.let { photoBase64 ->
        FullScreenPhotoDialog(photoBase64 = photoBase64, onDismiss = { fullScreenPhoto = null })
    }
}

private fun decodeTaskPhoto(base64: String) =
    runCatching { Base64.decode(base64, Base64.NO_WRAP) }
        .mapCatching { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!.asImageBitmap() }
        .getOrNull()

@Composable
private fun FullScreenPhotoDialog(photoBase64: String, onDismiss: () -> Unit) {
    val bitmap = remember(photoBase64) { decodeTaskPhoto(photoBase64) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Фото задания",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
            }
        }
    }
}

@Composable
private fun TaskAttachment(task: Task, onPhotoClick: () -> Unit) {
    val context = LocalContext.current
    val photoBase64 = task.photoBase64
    if (photoBase64 != null) {
        val bitmap = remember(photoBase64) { decodeTaskPhoto(photoBase64) }
        if (bitmap != null) {
            Spacer(Modifier.height(8.dp))
            Image(
                bitmap = bitmap,
                contentDescription = "Фото задания",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onPhotoClick),
                contentScale = ContentScale.Crop
            )
        }
    }
    task.linkUrl?.let { url ->
        Spacer(Modifier.height(8.dp))
        AssistChip(
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
            label = { Text(url, maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) }
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
private fun TaskApprovalCard(task: Task, onApprove: () -> Unit, onReject: () -> Unit, onPhotoClick: () -> Unit) {
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
            TaskAttachment(task = task, onPhotoClick = onPhotoClick)
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
private fun SkillApprovalCard(row: SkillRow, onApprove: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "${row.assigned.emoji} ${row.assigned.title}", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Выполнено сегодня (${row.progress}/${row.target}) • награда ${row.assigned.rewardMinutes} мин",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onApprove) { Text("Подтвердить") }
        }
    }
}

@Composable
private fun SkillProgressCard(row: SkillRow) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "${row.assigned.emoji} ${row.assigned.title}", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${row.progress} из ${row.target} • награда ${row.assigned.rewardMinutes} мин",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (row.progress.toFloat() / row.target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OpenTaskCard(task: Task, onPhotoClick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = task.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Ждём выполнения • награда ${task.rewardMinutes} мин",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            TaskAttachment(task = task, onPhotoClick = onPhotoClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskScreen(
    apps: List<AppRule>,
    onClose: () -> Unit,
    onCreate: (String, Int, String?, ByteArray?, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("15") }
    var selectedApp by remember { mutableStateOf(apps.firstOrNull()) }
    var appMenuExpanded by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("") }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var compressing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        compressing = true
        coroutineScope.launch {
            photoBytes = withContext(Dispatchers.IO) { compressImageForUpload(context, uri) }
            compressing = false
        }
    }

    Scaffold(
        // Same nested-Scaffold double-inset issue as the list screen above — this is still shown
        // inside DashboardScreen's own Scaffold, not as a standalone screen.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Новое задание") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
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

            Spacer(Modifier.height(16.dp))
            Text(
                "Что именно нужно решить — необязательно",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Text(
                "Фото задания (например, страница из тетради) или ссылка — ребёнок увидит это на карточке задания.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(8.dp))

            val bytes = photoBytes
            if (bytes != null) {
                val bitmap = remember(bytes) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Фото задания",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = { photoBytes = null }) { Text("Убрать фото") }
            } else {
                OutlinedButton(
                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !compressing,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (compressing) "Обрабатываю фото…" else "Прикрепить фото") }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = linkUrl,
                onValueChange = { linkUrl = it },
                label = { Text("Или ссылка на задание") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
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
            Button(
                onClick = {
                    onCreate(title, minutesText.toIntOrNull() ?: 15, selectedApp?.packageName, photoBytes, linkUrl.takeIf { it.isNotBlank() })
                },
                enabled = title.isNotBlank() && (apps.isEmpty() || selectedApp != null) && !compressing,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Создать") }
        }
    }
}
