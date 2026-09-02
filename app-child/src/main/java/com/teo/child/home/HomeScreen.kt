package com.teo.child.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.teo.child.admin.DeviceAdminHelper
import com.teo.child.data.local.AppBalance
import com.teo.child.tasks.ChildTasksSection

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    var protectionReleased by remember { mutableStateOf(!DeviceAdminHelper.isActive(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                protectionReleased = !DeviceAdminHelper.isActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        if (!protectionReleased) {
            Text(text = "Привет!", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Вот что происходит сегодня",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(20.dp))

            if (uiState.balances.isNotEmpty()) {
                BalanceSection(uiState.balances)
            }
            if (uiState.blockedAppLabels.isNotEmpty()) {
                if (uiState.balances.isNotEmpty()) Spacer(Modifier.height(20.dp))
                BlockedAppsSection(uiState.blockedAppLabels)
            }
            if (uiState.balances.isEmpty() && uiState.blockedAppLabels.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Пока нет ограничений — пользуйся телефоном спокойно.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            ChildTasksSection()

            if (uiState.parentPhone != null) {
                Spacer(Modifier.height(32.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.sendSos()
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${uiState.parentPhone}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(dialIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (uiState.sosSent) "Родителю отправлено уведомление" else "SOS — позвонить родителю") }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Родитель разрешил удаление",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Можно удалить это приложение обычным способом.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Удалить приложение") }
            }
        }
    }
}

@Composable
private fun BlockedAppsSection(labels: List<String>) {
    Column {
        Text(text = "Заблокировано родителем", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = labels.joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun BalanceSection(balances: List<AppBalance>) {
    Column {
        Text(text = "Сколько времени осталось", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        balances.forEach { balance ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = balance.appLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${balance.remainingMinutes} мин осталось",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (balance.remainingMinutes == 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val total = (balance.limitMinutes + balance.bonusMinutes).coerceAtLeast(1)
                    val progress = (balance.usedMinutes.toFloat() / total).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (progress >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}
