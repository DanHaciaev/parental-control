package com.teo.parent.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun FamilySettingsScreen(
    onSaved: () -> Unit,
    viewModel: FamilySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var childName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var totalCapEnabled by remember { mutableStateOf(false) }
    var totalCapMinutes by remember { mutableStateOf("60") }
    var fullBlockAllowedPackages by remember { mutableStateOf(emptySet<String>()) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.loading) {
        if (!uiState.loading && !initialized) {
            childName = uiState.childName
            phone = uiState.phone
            totalCapEnabled = uiState.totalCapEnabled
            totalCapMinutes = uiState.totalCapMinutes
            fullBlockAllowedPackages = uiState.fullBlockAllowedPackages
            initialized = true
        }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            onSaved()
            viewModel.consumeSaved()
        }
    }

    if (uiState.loading) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        OutlinedTextField(
            value = childName,
            onValueChange = { childName = it },
            label = { Text("Имя ребёнка") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Ваш телефон (для кнопки SOS у ребёнка)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Общий лимит на отвлекающие приложения",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = totalCapEnabled, onCheckedChange = { totalCapEnabled = it })
        }

        if (totalCapEnabled) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = totalCapMinutes,
                onValueChange = { if (it.all(Char::isDigit) && it.length <= 4) totalCapMinutes = it },
                label = { Text("Лимит в минутах") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Считает только приложения, у которых стоит режим «По времени». Как только их суммарное " +
                    "время за день исчерпано, телефон блокируется целиком — кроме приложений, выбранных ниже.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Что остаётся доступно при полной блокировке телефона",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Действует и когда заканчивается общий лимит времени, и во время расписания «Блокировать всё». " +
                "Звонки и наше приложение всегда доступны — остальное выбирайте сами.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(8.dp))
        if (uiState.installedApps.isEmpty()) {
            Text(
                "Список приложений появится, как только телефон ребёнка синхронизируется.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        } else {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(uiState.installedApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    fullBlockAllowedPackages = if (app.packageName in fullBlockAllowedPackages) {
                                        fullBlockAllowedPackages - app.packageName
                                    } else {
                                        fullBlockAllowedPackages + app.packageName
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = app.packageName in fullBlockAllowedPackages,
                                onCheckedChange = { checked ->
                                    fullBlockAllowedPackages = if (checked) {
                                        fullBlockAllowedPackages + app.packageName
                                    } else {
                                        fullBlockAllowedPackages - app.packageName
                                    }
                                }
                            )
                            Text(app.appLabel, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        uiState.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                viewModel.save(childName, phone, totalCapEnabled, totalCapMinutes, fullBlockAllowedPackages)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сохранить") }
    }
}
