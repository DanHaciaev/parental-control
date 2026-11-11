package com.teo.parent.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.teo.core.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeleteAccountUiState(
    val deleting: Boolean = false,
    val errorMessage: String? = null,
    val deleted: Boolean = false
)

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    fun delete(password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true, errorMessage = null) }
            try {
                accountRepository.deleteAccount(password)
                _uiState.update { it.copy(deleting = false, deleted = true) }
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _uiState.update { it.copy(deleting = false, errorMessage = "Неверный пароль от аккаунта") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(deleting = false, errorMessage = "Не удалось удалить — проверьте интернет и попробуйте ещё раз")
                }
            }
        }
    }
}

/** Confirmation for "Удалить аккаунт" — see AccountRepository for exactly what gets deleted. */
@Composable
fun DeleteAccountDialog(
    onDeleted: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: DeleteAccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var password by remember { mutableStateOf("") }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onDeleted()
    }

    AlertDialog(
        onDismissRequest = { if (!uiState.deleting) onDismiss() },
        title = { Text("Удалить аккаунт?") },
        text = {
            Column {
                Text(
                    "Аккаунт удалится навсегда. Дети, у которых нет второго родителя в Nest, удалятся вместе со всеми " +
                        "данными: местоположением, статистикой, историей, заданиями и настройками. Если у ребёнка есть " +
                        "второй родитель, у него всё останется.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nest Kid на телефоне ребёнка удалите сами: при удалении он попросит ваш текущий код защиты — " +
                        "запомните его.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль от аккаунта") },
                    singleLine = true,
                    enabled = !uiState.deleting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                uiState.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (uiState.deleting) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.delete(password) },
                enabled = password.isNotEmpty() && !uiState.deleting
            ) { Text("Удалить навсегда", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uiState.deleting) { Text("Отмена") }
        }
    )
}
