package com.teo.parent.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teo.core.model.HistoryEntry
import com.teo.core.model.HistorySource
import com.teo.parent.dashboard.AppIconRegistry
import com.teo.parent.util.timeAgo
import java.util.Date

private val TABS = listOf("Браузер" to HistorySource.CHROME, "YouTube" to HistorySource.YOUTUBE)
private const val SEARCH_PREFIX = "Поиск: "
private const val PREVIEW_COUNT = 3

private data class ChannelSummary(val name: String, val videoCount: Int, val lastWatchedAt: Date?)

private enum class ExpandedSection { VIDEOS, SITES, YT_QUERIES, BROWSER_QUERIES, CHANNELS }

private fun isSearchEntry(entry: HistoryEntry) = entry.title.startsWith(SEARCH_PREFIX)

private fun groupChannels(videos: List<HistoryEntry>): List<ChannelSummary> =
    videos.mapNotNull { it.channelName }.distinct().map { name ->
        val forChannel = videos.filter { it.channelName == name }
        ChannelSummary(
            name = name,
            videoCount = forChannel.size,
            lastWatchedAt = forChannel.maxByOrNull { it.createdAt?.time ?: 0 }?.createdAt
        )
    }.sortedByDescending { it.lastWatchedAt?.time ?: 0 }

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var expandedSection by remember { mutableStateOf<ExpandedSection?>(null) }
    val selectedSource = TABS[selectedTab].second

    val sourceEntries = uiState.entries.filter { it.source == selectedSource }
    val queries = sourceEntries.filter(::isSearchEntry)
    val nonQueries = sourceEntries.filterNot(::isSearchEntry)
    val channels = remember(nonQueries) { groupChannels(nonQueries) }

    expandedSection?.let { section ->
        ExpandedSectionScreen(
            section = section,
            nonQueries = nonQueries,
            queries = queries,
            channels = channels,
            onBack = { expandedSection = null }
        )
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(selectedTab, sourceEntries.firstOrNull()?.id) {
        if (sourceEntries.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            TABS.forEachIndexed { index, (label, _) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(label) }
                )
            }
        }

        when {
            uiState.loading -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            sourceEntries.isEmpty() -> Box(Modifier.fillMaxSize()) {
                Text(
                    text = if (selectedSource == HistorySource.YOUTUBE) {
                        "Пока нет истории YouTube. Она появится, как только ребёнок откроет видео."
                    } else {
                        "Пока нет истории браузера. Она появится, как только ребёнок откроет страницу."
                    },
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            }
            else -> LazyColumn(state = listState, contentPadding = PaddingValues(16.dp)) {
                if (selectedSource == HistorySource.YOUTUBE) {
                    section(
                        title = "Последние видео",
                        rows = nonQueries.take(PREVIEW_COUNT),
                        showAllLabel = "Показать все просмотры",
                        onShowAll = { expandedSection = ExpandedSection.VIDEOS }
                    )
                    section(
                        title = "Запросы",
                        rows = queries.take(PREVIEW_COUNT).map(::stripSearchPrefix),
                        showAllLabel = "Показать все запросы",
                        onShowAll = { expandedSection = ExpandedSection.YT_QUERIES }
                    )
                    if (channels.isNotEmpty()) {
                        item { SectionHeader("Каналы и блогеры") }
                        items(channels.take(PREVIEW_COUNT), key = { "ch_${it.name}" }) { ChannelRow(it) }
                        item { ShowAllButton("Показать все каналы") { expandedSection = ExpandedSection.CHANNELS } }
                    }
                } else {
                    section(
                        title = "Последние запросы",
                        rows = queries.take(PREVIEW_COUNT).map(::stripSearchPrefix),
                        showAllLabel = "Показать все запросы",
                        onShowAll = { expandedSection = ExpandedSection.BROWSER_QUERIES }
                    )
                    section(
                        title = "Последние сайты",
                        rows = nonQueries.take(PREVIEW_COUNT),
                        showAllLabel = "Показать все сайты",
                        onShowAll = { expandedSection = ExpandedSection.SITES }
                    )
                }
            }
        }
    }
}

private fun stripSearchPrefix(entry: HistoryEntry) = entry.copy(title = entry.title.removePrefix(SEARCH_PREFIX))

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    rows: List<HistoryEntry>,
    showAllLabel: String,
    onShowAll: () -> Unit
) {
    if (rows.isEmpty()) return
    item { SectionHeader(title) }
    items(rows, key = { it.id }) { HistoryRow(it) }
    item { ShowAllButton(showAllLabel, onShowAll) }
}

@Composable
private fun ExpandedSectionScreen(
    section: ExpandedSection,
    nonQueries: List<HistoryEntry>,
    queries: List<HistoryEntry>,
    channels: List<ChannelSummary>,
    onBack: () -> Unit
) {
    val title = when (section) {
        ExpandedSection.VIDEOS -> "Все просмотры"
        ExpandedSection.SITES -> "Все сайты"
        ExpandedSection.YT_QUERIES, ExpandedSection.BROWSER_QUERIES -> "Все запросы"
        ExpandedSection.CHANNELS -> "Все каналы"
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            when (section) {
                ExpandedSection.VIDEOS, ExpandedSection.SITES ->
                    items(nonQueries, key = { it.id }) { HistoryRow(it) }
                ExpandedSection.YT_QUERIES, ExpandedSection.BROWSER_QUERIES ->
                    items(queries.map(::stripSearchPrefix), key = { it.id }) { HistoryRow(it) }
                ExpandedSection.CHANNELS ->
                    items(channels, key = { "ch_${it.name}" }) { ChannelRow(it) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun ShowAllButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
private fun ChannelRow(channel: ChannelSummary) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(channel.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = channel.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${channel.videoCount} просмотренных видео",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val pkg = if (entry.source == HistorySource.YOUTUBE) "com.google.android.youtube" else "com.android.chrome"
    val spec = AppIconRegistry.forPackage(pkg)

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (spec != null) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(spec.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(spec.glyph),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(spec.glyphTint),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2
                )
                val subtitle = entry.domain ?: entry.url
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        maxLines = 1
                    )
                }
                if (entry.source == HistorySource.YOUTUBE) {
                    Text(
                        text = "видео (приблизительно)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = timeAgo(entry.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}
