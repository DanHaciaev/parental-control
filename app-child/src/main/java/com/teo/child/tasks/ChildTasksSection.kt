package com.teo.child.tasks

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.child.home.EmojiIcon
import com.teo.core.model.Task

@Composable
fun ChildTasksSection(viewModel: ChildTasksViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var fullScreenPhoto by remember { mutableStateOf<String?>(null) }

    val hasAnything = uiState.openTasks.isNotEmpty() || uiState.waitingApprovalTasks.isNotEmpty() || uiState.approvedCount > 0
    if (!hasAnything) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Задания от родителей", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (uiState.approvedCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiIcon(com.teo.child.R.drawable.ic_emoji_fire, size = 20.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(text = "${uiState.approvedCount} выполнено", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        uiState.openTasks.forEach { task ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                    TaskAttachment(task = task, onPhotoClick = { fullScreenPhoto = task.photoBase64 })
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
                    TaskAttachment(task = task, onPhotoClick = { fullScreenPhoto = task.photoBase64 })
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

    fullScreenPhoto?.let { photoBase64 ->
        val bitmap = remember(photoBase64) { decodeTaskPhoto(photoBase64) }
        Dialog(onDismissRequest = { fullScreenPhoto = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Фото задания",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                IconButton(onClick = { fullScreenPhoto = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                }
            }
        }
    }
}

private fun decodeTaskPhoto(base64: String) =
    runCatching { Base64.decode(base64, Base64.NO_WRAP) }
        .mapCatching { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!.asImageBitmap() }
        .getOrNull()

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
