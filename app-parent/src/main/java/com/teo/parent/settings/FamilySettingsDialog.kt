package com.teo.parent.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun FamilySettingsDialog(
    onDismiss: () -> Unit,
    viewModel: FamilySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var phone by remember { mutableStateOf("") }
    var bedtimeEnabled by remember { mutableStateOf(false) }
    var bedtimeStart by remember { mutableStateOf("22:00") }
    var bedtimeEnd by remember { mutableStateOf("07:00") }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.loading) {
        if (!uiState.loading && !initialized) {
            phone = uiState.phone
            bedtimeEnabled = uiState.bedtimeEnabled
            bedtimeStart = uiState.bedtimeStart
            bedtimeEnd = uiState.bedtimeEnd
            initialized = true
        }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Настройки семьи", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Ваш телефон (для кнопки SOS у ребёнка)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Ночной режим", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = bedtimeEnabled, onCheckedChange = { bedtimeEnabled = it })
                }

                if (bedtimeEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = bedtimeStart,
                            onValueChange = { bedtimeStart = it },
                            label = { Text("Начало (ЧЧ:ММ)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = bedtimeEnd,
                            onValueChange = { bedtimeEnd = it },
                            label = { Text("Конец (ЧЧ:ММ)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "В это время телефон ребёнка будет недоступен, кроме звонков.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.save(phone, bedtimeEnabled, bedtimeStart, bedtimeEnd) }) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}
