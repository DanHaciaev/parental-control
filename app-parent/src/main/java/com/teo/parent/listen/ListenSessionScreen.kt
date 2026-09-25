package com.teo.parent.listen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.ListenSessionStatus

/**
 * Shown right after the parent confirms "Послушать вокруг" — walks through connecting to the
 * child device, then the live call, all driven by [ListenSessionViewModel]. The child device
 * answers automatically (see [com.teo.child.listen.ListenConsentActivity]); DECLINED here only
 * means the child device couldn't answer (e.g. microphone permission not granted).
 */
@Composable
fun ListenSessionScreen(onClose: () -> Unit, viewModel: ListenSessionViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }
    DisposableEffect(Unit) { onDispose { viewModel.onScreenClosed() } }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        when {
            uiState.errorMessage != null -> {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
            }
            uiState.status == null || uiState.status == ListenSessionStatus.PENDING_CONSENT -> {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 24.dp))
                Text(
                    text = "Подключаемся к телефону ребёнка…",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Прослушивание начнётся автоматически, как только телефон ребёнка ответит.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
            }
            uiState.status == ListenSessionStatus.ACTIVE -> {
                Text(
                    text = "Идёт прослушивание",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatElapsed(uiState.elapsedSeconds),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Завершить") }
            }
            uiState.status == ListenSessionStatus.DECLINED -> {
                Text(
                    text = "Не удалось подключиться к телефону ребёнка",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
            }
            uiState.status == ListenSessionStatus.ENDED -> {
                Text(
                    text = "Сессия завершена",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
