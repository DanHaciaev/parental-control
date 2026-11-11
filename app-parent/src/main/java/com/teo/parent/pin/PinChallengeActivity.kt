package com.teo.parent.pin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.parent.admin.DeviceAdminHelper
import com.teo.parent.ui.theme.ParentalControlTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PinChallengeActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParentalControlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PinChallengeScreen(onDone = { finish() })
                }
            }
        }
    }
}

@Composable
private fun PinChallengeScreen(
    onDone: () -> Unit,
    viewModel: PinChallengeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var protectionRemoved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (!uiState.unlocked) {
            Text(
                text = "Введите код защиты",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            CodeEntry(
                secretType = uiState.secretType,
                loading = uiState.loading,
                errorMessage = uiState.errorMessage,
                confirmLabel = "Подтвердить",
                onVerify = viewModel::verify,
                onBiometricSuccess = viewModel::unlockWithBiometric
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        } else if (!protectionRemoved) {
            Text(
                text = "Код верный",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Защита от удаления будет снята, и откроется системное окно удаления приложения.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    DeviceAdminHelper.relinquish(context)
                    protectionRemoved = true
                    val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(uninstallIntent)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Удалить приложение") }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Не сейчас") }
        }
    }
}
