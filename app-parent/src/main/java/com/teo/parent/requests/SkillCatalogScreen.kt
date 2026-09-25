package com.teo.parent.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teo.core.model.AppRule
import com.teo.core.model.SkillTemplate
import com.teo.core.model.SkillTemplateCatalog
import com.teo.core.model.SkillUnit
import com.teo.parent.dashboard.EmojiIcon

fun unitLabel(unit: SkillUnit): String = when (unit) {
    SkillUnit.MINUTES -> "мин"
    SkillUnit.REPS -> "раз"
    SkillUnit.COUNT -> "раз"
}

@Composable
fun categoryColor(category: String): Color = when (category) {
    SkillTemplateCatalog.CATEGORY_SKILLS -> MaterialTheme.colorScheme.primaryContainer
    SkillTemplateCatalog.CATEGORY_ACTIVITY -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
}

/** One category's templates as a horizontally scrolling row of blocks — matches the reference
 *  design (colorful cards you swipe through per category) instead of a plain top-to-bottom list. */
@Composable
fun SkillCategoryCarousel(
    category: String,
    templates: List<SkillTemplate>,
    assignedTemplateIds: Set<String>,
    onTemplateClick: (SkillTemplate) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(templates, key = { it.id }) { template ->
                SkillTemplateCard(
                    template = template,
                    assigned = template.id in assignedTemplateIds,
                    onClick = { onTemplateClick(template) }
                )
            }
        }
    }
}

@Composable
private fun SkillTemplateCard(template: SkillTemplate, assigned: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = categoryColor(template.category)),
        modifier = Modifier.width(128.dp).clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmojiIcon(SkillIconRegistry.forTemplateId(template.id), size = 36.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = template.title,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (assigned) "✓ Добавлено" else "${template.defaultTargetPerDay} ${unitLabel(template.unit)}/день",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (assigned) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Compact row form used for search results, where matches can span categories so a carousel
 *  grouping no longer makes sense. */
@Composable
fun SkillTemplateListRow(template: SkillTemplate, assigned: Boolean, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(categoryColor(template.category)),
                    contentAlignment = Alignment.Center
                ) {
                    EmojiIcon(SkillIconRegistry.forTemplateId(template.id), size = 26.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(template.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${template.defaultTargetPerDay} ${unitLabel(template.unit)}/день · награда ${template.defaultRewardMinutes} мин",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }
            TextButton(onClick = onClick, modifier = Modifier.padding(end = 12.dp)) {
                Text(if (assigned) "Добавлено" else "Добавить")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignSkillSheet(
    template: SkillTemplate,
    alreadyAssigned: Boolean,
    apps: List<AppRule>,
    onDismiss: () -> Unit,
    onAssign: (Int, Int, String?) -> Unit,
    onUnassign: () -> Unit
) {
    var target by remember(template.id) { mutableIntStateOf(template.defaultTargetPerDay) }
    var reward by remember(template.id) { mutableIntStateOf(template.defaultRewardMinutes) }
    var selectedApp by remember(template.id) { mutableStateOf(apps.firstOrNull()) }
    var appMenuExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // A "1 раз" chore (target=1) is a single daily completion, not a countable quota — no point
    // letting the parent step it up/down from 1.
    val isSingleCompletion = template.unit == SkillUnit.COUNT && template.defaultTargetPerDay == 1

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(categoryColor(template.category)),
                    contentAlignment = Alignment.Center
                ) {
                    EmojiIcon(SkillIconRegistry.forTemplateId(template.id), size = 26.dp)
                }
                Spacer(Modifier.width(12.dp))
                Text(template.title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(20.dp))

            if (isSingleCompletion) {
                Text(
                    "Считается выполненным одним нажатием",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            } else {
                Text("Норма в день", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                val step = if (template.unit == SkillUnit.COUNT) 500 else 5
                Stepper(value = target, step = step, minValue = step, suffix = unitLabel(template.unit), onChange = { target = it })
            }

            Spacer(Modifier.height(16.dp))
            Text("Награда за выполнение", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Stepper(value = reward, step = 5, minValue = 5, suffix = "мин", onChange = { reward = it })

            Spacer(Modifier.height(16.dp))
            if (apps.isEmpty()) {
                Text(
                    "Сначала установите лимит времени хотя бы на одно приложение — награда добавляется к нему.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text("Награда добавится к приложению", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(onClick = { appMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedApp?.appLabel ?: "Выберите приложение")
                    }
                    DropdownMenu(expanded = appMenuExpanded, onDismissRequest = { appMenuExpanded = false }) {
                        apps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.appLabel) },
                                onClick = {
                                    selectedApp = app
                                    appMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onAssign(target, reward, selectedApp?.packageName) },
                enabled = apps.isEmpty() || selectedApp != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (alreadyAssigned) "Сохранить" else "Добавить ребёнку") }

            if (alreadyAssigned) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onUnassign, modifier = Modifier.fillMaxWidth()) { Text("Убрать задание") }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
    }
}

@Composable
private fun Stepper(value: Int, step: Int, minValue: Int, suffix: String, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onChange((value - step).coerceAtLeast(minValue)) }) {
            Text("–", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            text = "$value $suffix",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(120.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = { onChange(value + step) }) {
            Text("+", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
