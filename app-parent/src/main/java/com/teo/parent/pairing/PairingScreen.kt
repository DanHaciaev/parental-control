package com.teo.parent.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.childPaired) {
        if (uiState.childPaired) onPaired()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Привяжите телефон ребёнка",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Установите приложение «Nest Kid» на телефон ребёнка и введите там этот код. Код действует 15 минут.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(32.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.loading && uiState.code == null) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = uiState.code?.let { it.take(3) + " " + it.drop(3) } ?: "——— ———",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                }
            }
        }

        uiState.errorMessage?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(onClick = { viewModel.generateCode() }, modifier = Modifier.fillMaxWidth()) {
            Text("Сгенерировать новый код")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Ждём подключение ребёнка…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}
