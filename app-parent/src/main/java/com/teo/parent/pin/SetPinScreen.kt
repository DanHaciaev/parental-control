package com.teo.parent.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SetPinScreen(
    onSaved: () -> Unit,
    viewModel: SetPinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    val pinsMatch = pin.isNotEmpty() && pin == confirmPin
    val pinValid = pin.length in 4..6 && pin.all { it.isDigit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Придумайте код защиты",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Этот код нужно будет ввести, если вы захотите удалить приложение с вашего телефона — так его не удалит случайно кто-то другой.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            label = { Text("Код (4–6 цифр)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
            label = { Text("Повторите код") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )

        if (confirmPin.isNotEmpty() && !pinsMatch) {
            Spacer(Modifier.height(12.dp))
            Text(text = "Коды не совпадают", color = MaterialTheme.colorScheme.error)
        }
        uiState.errorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.savePin(pin) },
            enabled = !uiState.loading && pinValid && pinsMatch,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Сохранить")
            }
        }
    }
}
