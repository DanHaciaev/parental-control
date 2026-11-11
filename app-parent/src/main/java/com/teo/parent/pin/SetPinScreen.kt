package com.teo.parent.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.SecretType
import com.teo.core.ui.SecretInput
import com.teo.core.ui.SecretTypeSelector

/**
 * Choose the kind of protection code and enter it twice. Used both for first-time setup and for
 * changing the code later ([onCancel] non-null shows a way back). The code applies to every
 * child's phone too — it's what unlocks uninstall/airplane-mode blocks there.
 */
@Composable
fun SetPinScreen(
    onSaved: () -> Unit,
    title: String = "Придумайте код защиты",
    onCancel: (() -> Unit)? = null,
    viewModel: SetPinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var type by remember { mutableStateOf(SecretType.PASSWORD) }
    var first by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var second by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    fun restart() {
        first = ""
        second = ""
        confirming = false
    }

    val firstError = if (first.isEmpty()) null else type.validate(first)

    fun goToConfirm() {
        if (type.validate(first) == null) {
            confirming = true
            mismatch = false
        }
    }

    fun confirm() {
        if (second == first) viewModel.savePin(first, type)
        else {
            mismatch = true
            second = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Код нужен, чтобы открыть это приложение и чтобы удалить приложение у вас или у ребёнка. Не говорите его детям.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))

        if (!confirming) {
            Text("Вид кода", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SecretTypeSelector(selected = type, onSelect = { type = it; restart() })
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (type) {
                    SecretType.PIN -> "Только цифры, не меньше ${SecretType.MIN_PIN_LENGTH}."
                    SecretType.PASSWORD -> "Буквы, цифры и символы, не меньше ${SecretType.MIN_PASSWORD_LENGTH}. Самый надёжный вариант."
                    SecretType.PATTERN -> "Соедините не меньше ${SecretType.MIN_PATTERN_DOTS} точек. Узор легко подсмотреть — вводите его, когда никто не видит."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(20.dp))
            SecretInput(
                type = type,
                value = first,
                onValueChange = { first = it },
                label = "Новый код",
                isError = firstError != null && type == SecretType.PATTERN,
                onSubmit = { goToConfirm() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            firstError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (type != SecretType.PATTERN) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { goToConfirm() },
                    enabled = first.isNotEmpty() && firstError == null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Далее") }
            }
        } else {
            Text(
                text = if (type == SecretType.PATTERN) "Нарисуйте ключ ещё раз" else "Повторите код",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(20.dp))
            SecretInput(
                type = type,
                value = second,
                onValueChange = { second = it; mismatch = false },
                label = "Повторите код",
                isError = mismatch,
                onSubmit = { confirm() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            if (mismatch) {
                Spacer(Modifier.height(8.dp))
                Text("Не совпадает — попробуйте ещё раз", color = MaterialTheme.colorScheme.error)
            }
            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            if (type != SecretType.PATTERN) {
                Button(
                    onClick = { confirm() },
                    enabled = !uiState.loading && second.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.loading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Сохранить")
                    }
                }
            } else if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            TextButton(onClick = { restart() }, modifier = Modifier.fillMaxWidth()) { Text("Начать заново") }
        }

        if (onCancel != null) {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
    }
}
