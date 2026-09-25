package com.teo.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class HistorySource { CHROME, YOUTUBE }

/** Best-effort browsing/watch history captured via Accessibility scraping — see HistoryExtractor in app-child. */
data class HistoryEntry(
    @DocumentId val id: String = "",
    val source: HistorySource = HistorySource.CHROME,
    /** Chrome only — the omnibox display string, not guaranteed a full canonical URL. */
    val url: String? = null,
    /** Page title (Chrome) or best-effort video title (YouTube, lower reliability). */
    val title: String = "",
    val domain: String? = null,
    /** YouTube only, best-effort — see HistoryExtractor.findChannelName. Null whenever the channel
     *  name wasn't visible/found on screen, which is expected for a meaningful share of videos. */
    val channelName: String? = null,
    @ServerTimestamp val createdAt: Date? = null
)
