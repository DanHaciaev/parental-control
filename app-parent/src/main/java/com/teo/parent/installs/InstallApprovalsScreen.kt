package com.teo.parent.installs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.InstalledApp

@Composable
fun InstallApprovalsScreen(viewModel: InstallApprovalsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            uiState.pendingInstalls.isEmpty() -> Text(
                text = "Новых приложений, ждущих разрешения, пока нет.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.pendingInstalls, key = { it.packageName }) { app ->
                    InstallApprovalCard(
                        app = app,
                        onApprove = { viewModel.approveInstall(app) },
                        onDeny = { viewModel.denyInstall(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallApprovalCard(app: InstalledApp, onApprove: () -> Unit, onDeny: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Новое приложение: ${app.appLabel}", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Ждёт вашего разрешения на использование",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = onApprove) { Text("Разрешить") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onDeny) { Text("Запретить") }
            }
        }
    }
}
