package com.teo.child.permission

import com.teo.core.R as CoreR
import com.teo.core.ui.components.NestOnboardingScreen
import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * First screen on the child's phone, before pairing and before any permission prompt: what this
 * app collects and who sees it (Google Play's prominent-disclosure requirement for monitoring
 * apps). Written to the parent, who sets the phone up. Declining closes the app — without consent
 * there is nothing it can do.
 */
@Composable
fun DisclosureScreen(onAccept: () -> Unit) {
    val activity = LocalContext.current as? Activity
    NestOnboardingScreen(
        title = "Nest Kid",
        subtitle = "Родительский контроль",
        headerIcon = CoreR.drawable.nest_ic_shield
    ) {
        Text(
            "Это приложение ставится на телефон ребёнка. Родители, подключённые к нему в Nest Parent, будут видеть:",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        DisclosureItem("Местоположение", "где находится телефон, в том числе когда приложение закрыто")
        DisclosureItem("Приложения", "какие приложения установлены и сколько времени в них проведено")
        DisclosureItem("Историю YouTube и Chrome", "названия просмотренных видео и каналов, адреса открытых страниц")
        DisclosureItem("Состояние телефона", "заряд батареи, включены ли геолокация и авиарежим")
        DisclosureItem("Шаги", "если разрешить датчик шагов — для заданий")
        DisclosureItem(
            "Звук вокруг",
            "если разрешить микрофон — родитель может включить его на время; на телефоне ребёнка в это время видно уведомление. Звук не записывается"
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Родители также смогут ограничивать время в приложениях, блокировать их и ставить расписания. " +
                "Пока приложение работает, в шторке всегда видно уведомление «Nest Kid: родительский контроль включён». " +
                "Данные передаются только родителям этой семьи.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("Согласен") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { activity?.finish() }, modifier = Modifier.fillMaxWidth()) {
            Text("Не сейчас — закрыть приложение")
        }
    }
}

@Composable
private fun DisclosureItem(title: String, details: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
    }
}

/**
 * Explanation shown right before one specific system permission screen — each sensitive
 * permission gets its own, separate from the others (Play's rule for Accessibility in particular).
 * "Согласен" continues to the system screen, "Не сейчас" leaves it off; the rest of the app keeps
 * working without it.
 */
@Composable
fun PermissionDisclosureDialog(
    title: String,
    text: String,
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onAgree) { Text("Согласен") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Не сейчас") } }
    )
}
