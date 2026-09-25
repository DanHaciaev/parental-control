package com.teo.child.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class ExtractedHistory(val title: String, val url: String? = null, val channelName: String? = null)

/**
 * Pure screen-scraping logic, kept out of the accessibility service for clarity. There is no
 * official Android API for reading another app's browsing/watch history — this is a best-effort
 * approximation of what's visible on screen at the moment of a window/content-changed event.
 */
object HistoryExtractor {
    private const val MAX_NODES = 400
    private val urlLikeRegex = Regex("^[\\w.-]+\\.[a-z]{2,}(/\\S*)?$", RegexOption.IGNORE_CASE)
    private val JUNK_TITLES = setOf(
        "реклама", "пропустить", "advertisement", "skip ad", "sponsored",
        "подписаться", "поделиться", "сохранить", "далее", "смотреть позже",
        "показать ещё", "показать больше", "установить", "подробнее", "на сайт рекламодателя"
    )
    // Video metadata (view/like counts, upload date, episode badges) sits right next to the title
    // in YouTube's layout — close enough that an earlier version of the fallback heuristic below
    // merged it straight into the logged title. These snippets are always short ("2 часа назад",
    // "15 тыс. просмотров"), unlike a real title, so the length cap keeps this from also rejecting
    // a legitimately long title that happens to contain one of these words.
    private val JUNK_METADATA_MARKERS = listOf(
        "просмотр", "лайк", "нравится", "подписчик", "назад", "views", "likes", "subscriber", "ago",
        "премьера", "episode", "season"
    )
    private const val METADATA_MAX_LENGTH = 40

    private fun isMetadataLike(text: String): Boolean =
        text.length <= METADATA_MAX_LENGTH && JUNK_METADATA_MARKERS.any { text.contains(it, ignoreCase = true) }
    // Chrome's omnibox elides the query string for privacy (e.g. shows "google.com/search?" with
    // the actual terms hidden), so the URL alone can only tell us we *landed* on a results page —
    // the search box echoed back on the page itself is the only place left to read the query from.
    private val SEARCH_RESULTS_URL_MARKERS = setOf(
        "google.com/search", "google.ru/search", "yandex.ru/search", "yandex.com/search", "bing.com/search"
    )

    /** Chrome's omnibox/title-bar ids are comparatively stable, with a text-heuristic fallback for OEM forks. */
    fun extractChrome(root: AccessibilityNodeInfo): ExtractedHistory? {
        val urlBarNode = root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar").firstOrNull()
        // Still being typed — logging this would flood history with one entry per keystroke instead
        // of the page/search actually landed on.
        if (urlBarNode?.isFocused == true) return null

        val url = urlBarNode?.text?.toString()?.takeIf { it.isNotBlank() }

        if (url != null && SEARCH_RESULTS_URL_MARKERS.any { url.contains(it, ignoreCase = true) }) {
            findWebPageSearchQuery(root)?.let { query ->
                return ExtractedHistory(title = "Поиск: $query", url = url)
            }
        }

        val title = root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/title_bar")
            .firstOrNull()?.text?.toString()?.takeIf { it.isNotBlank() }

        if (url != null || title != null) {
            return ExtractedHistory(title = title ?: url ?: return null, url = url)
        }

        // Resource-id drift fallback (OEM Chrome forks / version changes): scan visible text for a URL-shaped string.
        val texts = mutableListOf<String>()
        collectText(root, texts, MAX_NODES)
        val fallbackUrl = texts.firstOrNull { urlLikeRegex.matches(it.trim()) } ?: return null
        return ExtractedHistory(title = fallbackUrl, url = fallbackUrl)
    }

    // The Google app's results screen sometimes echoes the query into its own native surface (a real
    // view with "search_box" in its resource id), and sometimes renders through an embedded WebView
    // instead — in which case there's no search box at all, but the WebView's own accessibility text
    // mirrors the page's <title> (e.g. "<query> - Поиск в Google"), same as a browser tab title.
    // Both were observed on-device; the query is whatever precedes one of these known suffixes.
    private val SEARCH_TITLE_SUFFIXES = listOf(
        " - Поиск в Google", " — Поиск в Google", " - Google Search", " - Yandex", " - Яндекс"
    )

    /** See [SEARCH_TITLE_SUFFIXES]: tries the native search-box surface first, then the WebView-title
     *  fallback for the embedded-browser variant of the results screen. */
    fun extractGoogleApp(root: AccessibilityNodeInfo): ExtractedHistory? {
        (findSearchQuery(root) ?: findWebPageSearchQuery(root))?.let { query ->
            return ExtractedHistory(title = "Поиск: $query")
        }

        val webViewTitle = findWebViewTitle(root) ?: return null
        val suffix = SEARCH_TITLE_SUFFIXES.firstOrNull { webViewTitle.endsWith(it, ignoreCase = true) }
        val title = if (suffix != null) "Поиск: ${webViewTitle.removeSuffix(suffix)}" else webViewTitle
        return ExtractedHistory(title = title)
    }

    /**
     * YouTube has no stable, documented resource-id for the video title across versions/layouts —
     * materially less reliable than Chrome. Skips ad screens entirely, prefers a completed search
     * query when one is visible, then tries a known-ish title id, then falls back to a bounds-based
     * heuristic scoped to the video's own metadata area (falling further back to the whole screen).
     */
    fun extractYoutube(root: AccessibilityNodeInfo): ExtractedHistory? {
        if (isShowingAd(root)) return null
        // Tapping an ad's "Открыть сайт"/click-through opens a landing page (login prompt, store
        // listing, etc.) in a WebView layered on top of YouTube's own package — confirmed on-device
        // to otherwise get scraped as if it were a video title ("Войдите в аккаунт и..."). YouTube's
        // actual watch/home/search screens are all native views with no WebView in the tree at all.
        if (hasWebView(root, intArrayOf(MAX_NODES))) return null

        // Bottom-sheet "engagement panels" (episodes/chapters, transcript, comments, live chat)
        // cover video_metadata_layout while open, so neither the id-based lookup nor the text-scan
        // fallback below can find the real title and both fall back to scanning the whole screen —
        // which for the chapters/episodes panel means picking up its auto-generated per-chapter
        // caption text (confirmed live: "молодой хиленький рак обманул иммунитет" logged as if it
        // were a video title) as a fresh "watched video" every time playback crossed into a new
        // chapter, even though the actual video never changed. Skip extraction entirely while any
        // such panel is open rather than try to scope around each one's own internal layout.
        // isVisibleToUser matters here: YouTube keeps this node in the tree even when the panel is
        // closed (confirmed live — with flagReportViewIds actually on, the id-only check below was
        // matching it at all times, silently suppressing every single history entry).
        if (root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/engagement_panel")
                .any { it.isVisibleToUser }
        ) {
            return null
        }

        findSearchQuery(root)?.let { query -> return ExtractedHistory(title = "Поиск: $query") }

        // Scope to the video's own metadata area when present — far more reliable than scanning the
        // whole screen, which also picks up thumbnails, channel names, and recommendation rows.
        val metadataContainer = root
            .findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/video_metadata_layout")
            .firstOrNull()
        val channelName = findChannelName(metadataContainer ?: root)

        // Scoped to metadataContainer, not root: YouTube's "Episodes" panel reuses this same id for
        // every episode card, so searching the whole screen could match whichever card happened to
        // render first instead of the actual playing video — and since each episode has a genuinely
        // different real title, the dedup in logHistoryEntry doesn't catch it, so scrolling that
        // panel alone (no real navigation) was logging a fresh "watched video" per episode scrolled past.
        val tierOne = (metadataContainer ?: root).findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/title")
            .firstOrNull()?.text?.toString()?.takeIf { it.isNotBlank() && !isJunk(it) && !isMetadataLike(it) }
        if (tierOne != null) return ExtractedHistory(title = tierOne, channelName = channelName)

        // Deliberately takes only the single topmost qualifying text block instead of merging
        // everything within a vertical band — merging used to pull in the view-count/like-count/
        // upload-date line right below the title (and episode badges above it), producing a
        // title string that was really "actual title + likes + episode info" all mashed together.
        val searchRoot = metadataContainer ?: root
        val candidates = mutableListOf<Pair<String, Int>>()
        collectTextWithTop(searchRoot, candidates, MAX_NODES)
        val title = candidates
            .filter { (text, _) -> text.length > 8 && !text.all { c -> c.isDigit() || c == ':' } && !isJunk(text) && !isMetadataLike(text) }
            .minByOrNull { (_, top) -> top }
            ?.first
            ?: return null
        return ExtractedHistory(title = title, channelName = channelName)
    }

    /** Captures a video's title the instant its row is tapped in a feed/search/recommendation
     *  list — confirmed live that waiting for the watch screen's own title (extractYoutube above)
     *  can miss it entirely: an ad, or the title simply not having rendered yet, can eat the whole
     *  debounce window before a real title ever gets a chance. The feed row itself has no such
     *  problem — it's fully rendered, ad-free content the instant it's tappable. [container] is the
     *  tapped item's own row/card, not the whole screen — the caller (the click-event source node)
     *  walks up a few ancestors to find it, since the exact node clicked is often just an inner
     *  thumbnail or label, not the row containing the title. */
    fun extractClickedVideoTitle(container: AccessibilityNodeInfo): ExtractedHistory? {
        val tierOne = container.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/title")
            .firstOrNull()?.text?.toString()?.takeIf { it.isNotBlank() && !isJunk(it) && !isMetadataLike(it) }
        if (tierOne != null) return ExtractedHistory(title = tierOne)

        val candidates = mutableListOf<Pair<String, Int>>()
        collectTextWithTop(container, candidates, MAX_NODES)
        val title = candidates
            .filter { (text, _) -> text.length > 8 && !text.all { c -> c.isDigit() || c == ':' } && !isJunk(text) && !isMetadataLike(text) }
            .minByOrNull { (_, top) -> top }
            ?.first
            ?: return null
        return ExtractedHistory(title = title)
    }

    /** Same reliability caveat as the title itself (no documented/stable id) — null just means the
     *  channel name wasn't found, not that extraction failed outright. */
    private fun findChannelName(node: AccessibilityNodeInfo?): String? =
        node?.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/channel_name")
            ?.firstOrNull()?.text?.toString()?.takeIf { it.isNotBlank() }

    fun extractDomain(url: String): String =
        url.substringAfter("://").substringBefore("/").removePrefix("www.")

    private fun isJunk(text: String): Boolean = text.trim().lowercase() in JUNK_TITLES

    /** Pre-roll/mid-roll ads carry their own distinct controls — skip logging anything while one is
     *  showing. Deliberately substring-matched, not exact-equals: confirmed live that the exact-match
     *  version missed the countdown variant of the skip button ("Пропустить через 5"), letting the
     *  ad's own promotional text get logged as if it were the video title while the real title never
     *  got a chance (the debounce window was already spent on the misdetected ad screen). */
    private fun isShowingAd(root: AccessibilityNodeInfo): Boolean {
        if (hasResourceIdContaining(root, "ad_", intArrayOf(MAX_NODES))) return true
        val texts = mutableListOf<String>()
        collectText(root, texts, MAX_NODES)
        return texts.any {
            val t = it.trim()
            t.contains("реклама", ignoreCase = true) || t.contains("пропустить", ignoreCase = true) ||
                t.contains("skip ad", ignoreCase = true) || t.equals("Skip", ignoreCase = true)
        }
    }

    private fun hasResourceIdContaining(node: AccessibilityNodeInfo?, needle: String, budget: IntArray): Boolean {
        if (node == null || budget[0] <= 0) return false
        budget[0]--
        if (node.viewIdResourceName?.contains(needle, ignoreCase = true) == true) return true
        for (i in 0 until node.childCount) {
            if (hasResourceIdContaining(node.getChild(i), needle, budget)) return true
        }
        return false
    }

    private fun hasWebView(node: AccessibilityNodeInfo?, budget: IntArray): Boolean {
        if (node == null || budget[0] <= 0) return false
        budget[0]--
        if (node.className?.toString() == "android.webkit.WebView") return true
        for (i in 0 until node.childCount) {
            if (hasWebView(node.getChild(i), budget)) return true
        }
        return false
    }

    /** A non-focused (i.e. submitted, not mid-typing) search box with text is treated as a completed search. */
    private fun findSearchQuery(node: AccessibilityNodeInfo?, budget: IntArray = intArrayOf(MAX_NODES)): String? {
        if (node == null || budget[0] <= 0) return null
        budget[0]--
        val idName = node.viewIdResourceName.orEmpty()
        // isVisibleToUser guards against a collapsed/off-screen search box elsewhere in the tree still
        // holding its last query text, which would otherwise misattribute it to whatever screen is
        // actually showing right now.
        if (idName.contains("search", ignoreCase = true) && !node.isFocused && node.isVisibleToUser) {
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        for (i in 0 until node.childCount) {
            findSearchQuery(node.getChild(i), budget)?.let { return it }
        }
        return null
    }

    /** Web/app search boxes render as plain page content with no Android resource id, so this
     *  matches by widget class instead of by id — unlike [findSearchQuery], which is id-based and
     *  only works for native views (e.g. YouTube's own search field). */
    private fun findWebPageSearchQuery(node: AccessibilityNodeInfo?, budget: IntArray = intArrayOf(MAX_NODES)): String? {
        if (node == null || budget[0] <= 0) return null
        budget[0]--
        if (node.className?.toString() == "android.widget.EditText" && !node.isFocused && node.isVisibleToUser) {
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        for (i in 0 until node.childCount) {
            findWebPageSearchQuery(node.getChild(i), budget)?.let { return it }
        }
        return null
    }

    /** A WebView's own accessibility text mirrors the loaded page's <title> (confirmed on-device) —
     *  the outer WebView wrapping a cross-origin frame reports blank text, so this walks until it
     *  finds the innermost one that actually carries it. */
    private fun findWebViewTitle(node: AccessibilityNodeInfo?, budget: IntArray = intArrayOf(MAX_NODES)): String? {
        if (node == null || budget[0] <= 0) return null
        budget[0]--
        if (node.className?.toString() == "android.webkit.WebView") {
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        for (i in 0 until node.childCount) {
            findWebViewTitle(node.getChild(i), budget)?.let { return it }
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>, budget: Int) {
        if (node == null || out.size >= budget) return
        node.text?.let { out.add(it.toString()) }
        node.contentDescription?.let { out.add(it.toString()) }
        for (i in 0 until node.childCount) {
            if (out.size >= budget) break
            collectText(node.getChild(i), out, budget)
        }
    }

    // In fullscreen playback video_metadata_layout isn't in the tree at all (confirmed live), so
    // the fallback below ends up scanning the whole screen — which otherwise picks up the live
    // closed-caption text and the chapter-name label that pops up next to the seek bar (confirmed
    // live: "Рак бывает заразным?" from time_bar_chapter_title, caption lines from
    // subtitle_window_identifier) as if either were a brand-new video's title, logging a fresh
    // "watched video" every time a caption line or chapter changed.
    private val EXCLUDED_TEXT_ID_MARKERS = listOf("subtitle_window", "chapter_title")

    private fun collectTextWithTop(node: AccessibilityNodeInfo?, out: MutableList<Pair<String, Int>>, budget: Int) {
        if (node == null || out.size >= budget) return
        val idName = node.viewIdResourceName.orEmpty()
        if (EXCLUDED_TEXT_ID_MARKERS.any { idName.contains(it, ignoreCase = true) }) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            out.add(it to bounds.top)
        }
        for (i in 0 until node.childCount) {
            if (out.size >= budget) break
            collectTextWithTop(node.getChild(i), out, budget)
        }
    }
}
