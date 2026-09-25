package com.teo.child.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.DrawableRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.teo.child.R
import com.teo.child.admin.DeviceAdminHelper
import com.teo.child.data.local.AppBalance
import com.teo.child.skills.ChildSkillsSection
import com.teo.child.tasks.ChildTasksSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    var deviceAdminActive by remember { mutableStateOf(DeviceAdminHelper.isActive(context)) }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val phone = uiState.parentPhone
        if (granted && phone != null) {
            callDirectly(context, phone)
        } else if (phone != null) {
            openDialer(context, phone)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowActive = DeviceAdminHelper.isActive(context)
                if (!nowActive && deviceAdminActive) {
                    // Admin just went inactive — whether from the PIN-gated uninstall flow (parent
                    // decided not to go through with it after all) or a genuine bypass, either way
                    // protection now has a gap worth surfacing rather than silently allowing it.
                    viewModel.onProtectionTamperedLocally()
                }
                deviceAdminActive = nowActive
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        !deviceAdminActive -> TamperedScreen(context)
        else -> HomeTabs(uiState = uiState, onCallParent = { phone ->
            val hasCallPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
            if (hasCallPermission) {
                callDirectly(context, phone)
            } else {
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        }, onSos = viewModel::sendSos)
    }
}

private enum class HomeTab(@DrawableRes val iconRes: Int, val label: String) {
    HOME(R.drawable.ic_emoji_home, "Главная"),
    TASKS(R.drawable.ic_emoji_check, "Задания"),
    TIME(R.drawable.ic_emoji_alarm_clock, "Время")
}

@Composable
private fun HomeTabs(uiState: HomeUiState, onCallParent: (String) -> Unit, onSos: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { EmojiIcon(tab.iconRes) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (HomeTab.entries[selectedTab]) {
                HomeTab.HOME -> HomeTabContent(uiState, onCallParent, onSos)
                HomeTab.TASKS -> TasksTabContent()
                HomeTab.TIME -> TimeTabContent(uiState)
            }
        }
    }
}

@Composable
private fun HomeTabContent(uiState: HomeUiState, onCallParent: (String) -> Unit, onSos: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Text(text = "Привет!", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Вот что происходит сегодня",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SosButton(
                sosSent = uiState.sosSent,
                onTriggered = {
                    onSos()
                    uiState.parentPhone?.let(onCallParent)
                }
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Удерживайте в экстренной ситуации",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun TimeTabContent(uiState: HomeUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(text = "Время в приложениях", style = MaterialTheme.typography.headlineMedium)
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
    }
}

@Composable
private fun TasksTabContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        ChildTasksSection()
        ChildSkillsSection()
    }
}

private const val SOS_HOLD_DURATION_MS = 3000L
private const val SOS_HOLD_STEPS = 40

/** Big, red, and centered on purpose — a quick-to-find panic button, not something to hunt for
 *  in a menu. Requires holding it down for [SOS_HOLD_DURATION_MS] (not a plain tap) so a stray
 *  touch in a pocket or a curious younger sibling can't fire it off by accident — the fill ring
 *  gives feedback on how much longer to hold. */
@Composable
private fun SosButton(sosSent: Boolean, onTriggered: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var holdProgress by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .size(280.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val job = scope.launch {
                            val stepDelay = SOS_HOLD_DURATION_MS / SOS_HOLD_STEPS
                            for (step in 1..SOS_HOLD_STEPS) {
                                delay(stepDelay)
                                holdProgress = step / SOS_HOLD_STEPS.toFloat()
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTriggered()
                        }
                        tryAwaitRelease()
                        job.cancel()
                        holdProgress = 0f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { holdProgress },
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
            trackColor = Color.Transparent,
            strokeWidth = 9.dp
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (sosSent) "Отправлено" else "SOS",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun TamperedScreen(context: android.content.Context) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Защита была отключена",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Родитель ещё не разрешал удаление. Пожалуйста, включите защиту снова.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { DeviceAdminHelper.requestActivation(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Включить защиту") }
    }
}

private fun callDirectly(context: android.content.Context, phone: String) {
    val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(callIntent)
}

private fun openDialer(context: android.content.Context, phone: String) {
    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(dialIntent)
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
