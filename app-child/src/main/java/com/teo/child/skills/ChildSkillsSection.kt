package com.teo.child.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.child.home.EmojiIcon
import com.teo.child.monitor.StepCounterTracker
import com.teo.core.model.SkillTemplateCatalog
import com.teo.core.model.SkillUnit

@Composable
private fun categoryColor(category: String): Color = when (category) {
    SkillTemplateCatalog.CATEGORY_SKILLS -> MaterialTheme.colorScheme.primaryContainer
    SkillTemplateCatalog.CATEGORY_ACTIVITY -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
}

private fun unitLabel(unit: String): String = when (SkillUnit.valueOf(unit)) {
    SkillUnit.MINUTES -> "мин"
    SkillUnit.REPS, SkillUnit.COUNT -> "раз"
}

/** How much one tap adds — minutes tick in round 5s, reps one at a time, and a big COUNT quota
 *  (e.g. 3000 steps) in ~10 taps' worth instead of one per unit, since nobody's tapping 3000 times. */
private fun incrementAmount(row: ChildSkillRow): Int = when (SkillUnit.valueOf(row.assigned.unit)) {
    SkillUnit.MINUTES -> 5
    SkillUnit.REPS -> 1
    SkillUnit.COUNT -> if (row.target <= 1) 1 else (row.target / 10).coerceAtLeast(1)
}

@Composable
fun ChildSkillsSection(viewModel: ChildSkillsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    if (uiState.rows.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(text = "Ежедневные задания", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        uiState.rows.forEach { row ->
            SkillCard(row = row, onIncrement = { viewModel.increment(row, incrementAmount(row)) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SkillCard(row: ChildSkillRow, onIncrement: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(categoryColor(row.assigned.category)),
                contentAlignment = Alignment.Center
            ) {
                EmojiIcon(SkillIconRegistry.forTemplateId(row.assigned.id), size = 26.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.assigned.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (row.completed) {
                        "Выполнено! Ждём подтверждения • награда ${row.assigned.rewardMinutes} мин"
                    } else {
                        "${row.progress.coerceAtMost(row.target)} из ${row.target} ${unitLabel(row.assigned.unit)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                if (!row.completed) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (row.progress.toFloat() / row.target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            val isAutoTracked = row.assigned.id == StepCounterTracker.STEPS_SKILL_ID
            if (!row.completed && isAutoTracked) {
                Spacer(Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiIcon(com.teo.child.R.drawable.ic_emoji_phone_device, size = 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "сам по шагомеру",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else if (!row.completed) {
                Spacer(Modifier.width(12.dp))
                Button(onClick = onIncrement) {
                    Text(if (SkillUnit.valueOf(row.assigned.unit) == SkillUnit.MINUTES) "+5" else "+1")
                }
            }
        }
    }
}
