package com.teo.parent.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teo.parent.R

/** Reused by every screen that lists apps (category lists, weekly-limits day view) — long lists of
 *  installed apps are otherwise a lot of scrolling to find one specific app. Also reused (with a
 *  different [placeholder]) for the ready-made task/skill catalog on the Задания screen. */
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Поиск приложения"
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = { EmojiIcon(R.drawable.ic_emoji_search, size = 20.dp) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Text("✕") }
            }
        }
    )
}

fun List<AppRow>.filterByAppSearch(query: String): List<AppRow> =
    if (query.isBlank()) this else filter { it.appLabel.contains(query, ignoreCase = true) }
