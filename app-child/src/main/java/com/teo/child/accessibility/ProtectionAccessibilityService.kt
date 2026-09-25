package com.teo.child.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.teo.child.data.ChildPreferences
import com.teo.child.data.local.RuleCacheDao
import com.teo.child.data.local.RuleCacheEntity
import com.teo.child.monitor.MonitorForegroundService
import com.teo.child.monitor.OverlayBlockerController
import com.teo.child.pin.PinChallengeActivity
import com.teo.child.sos.SosTrigger
import com.teo.core.model.EventLogEntry
import com.teo.core.model.EventType
import com.teo.core.model.HistoryEntry
import com.teo.core.model.HistorySource
import com.teo.core.model.RuleMode
import com.teo.core.repository.EventRepository
import com.teo.core.repository.HistoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Two unrelated jobs live in this one service (extending it rather than adding a second service,
 * since only one accessibility service is needed and they never watch the same packages):
 *
 * 1. Defensive backstop: watches Settings / package-installer windows for attempts to uninstall
 *    this app or any currently blocked/time-limited app, and backs out immediately. The real
 *    "release" path for this app itself is the remote RELEASE_PROTECTION command (handled in
 *    MonitorForegroundService), which self-relinquishes admin with no UI at all — this exists
 *    purely to catch someone trying to go around that through Settings.
 * 2. История: best-effort Chrome/YouTube history capture — see HistoryExtractor for why this is
 *    the only way to get this data at all without an official Android API for it.
 */
@AndroidEntryPoint
class ProtectionAccessibilityService : AccessibilityService() {

    @Inject lateinit var ruleCacheDao: RuleCacheDao
    @Inject lateinit var childPreferences: ChildPreferences
    @Inject lateinit var historyRepository: HistoryRepository
    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var sosTrigger: SosTrigger
    @Inject lateinit var overlayController: OverlayBlockerController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var restrictedAppLabels: Set<String> = emptySet()
    @Volatile private var ruleCacheByPackage: Map<String, RuleCacheEntity> = emptyMap()
    private val ownAppLabel: String by lazy { packageManager.getApplicationLabel(applicationInfo).toString().lowercase() }
    @Volatile private var currentFamilyId: String? = null
    @Volatile private var onboardingComplete: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            ruleCacheDao.observeAll().collect { rules ->
                restrictedAppLabels = rules
                    .filter { it.mode == RuleMode.BLOCKED.name || it.mode == RuleMode.TIME_LIMIT.name }
                    .map { it.appLabel.lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet()
                ruleCacheByPackage = rules.associateBy { it.packageName }
            }
        }
        serviceScope.launch {
            childPreferences.familyId.collect { currentFamilyId = it }
        }
        serviceScope.launch {
            childPreferences.onboardingComplete.collect { onboardingComplete = it }
        }
    }

    @Volatile private var lastCheckAtMs = 0L
    @Volatile private var lastHistoryCheckAtMs = 0L
    @Volatile private var lastLoggedSignature: String? = null
    @Volatile private var lastServiceCheckAtMs = 0L
    @Volatile private var lastPipCheckAtMs = 0L
    private var historySettleJob: Job? = null
    private var tamperSettleJob: Job? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events fire on essentially every window change, i.e. constantly during any real
        // phone use — a far tighter recovery window than the 15-minute WorkManager watchdog floor for
        // Samsung's aggressive background kills of the plain foreground service.
        ensureMonitorServiceRunning()
        updatePipState()

        val eventPackage = event?.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && eventPackage == "com.google.android.youtube") {
            handleYoutubeVideoClick(event)
        }
        // Play Store's "Установить" button turns out not to dispatch TYPE_VIEW_CLICKED at all
        // (confirmed live: zero such events observed from com.android.vending around a real tap,
        // only content/window-state-changed events) — so instead of catching the click itself,
        // this watches for the "Отмена" button that replaces "Установить" the instant a download
        // starts, which content-changed events do reliably carry.
        if (eventPackage == "com.android.vending" &&
            (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        ) {
            checkPlayStoreInstallStarted()
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }
        // A genuine window switch (not just content redrawing within the same screen) — checked
        // here instead of waiting for MonitorForegroundService's next ~3s poll tick, which used to
        // give a blocked app's window a few seconds to actually be usable (enough time to open
        // Telegram and send a message) before the block caught up with it.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkInstantBlock(eventPackage)
        }
        if (eventPackage in PROTECTION_WATCHED_PACKAGES) checkAndBlockIfNeeded(eventPackage)
        if (eventPackage in HISTORY_WATCHED_PACKAGES) checkAndLogHistory(eventPackage)
    }

    /** Only handles the always-blocked case (RuleMode.BLOCKED) — a TIME_LIMIT app whose balance
     *  just ran out is still caught by MonitorForegroundService's poll loop, which needs the
     *  ticked-usage numbers this service doesn't track. Instant coverage matters most for BLOCKED
     *  apps precisely because there's no "used up the last few seconds" grace period to reason
     *  about — the answer is always just no. */
    private fun checkInstantBlock(pkg: String) {
        if (pkg == packageName) return
        val rule = ruleCacheByPackage[pkg] ?: return
        if (rule.mode != RuleMode.BLOCKED.name) return
        if (overlayController.isShowing() && overlayController.blockedPackage == pkg) return

        goHome()
        overlayController.show(
            reason = "«${rule.appLabel}» заблокировано родителем",
            allowRequestMore = false,
            onGoHome = ::goHome,
            onRequestMore = {},
            blockedPackage = pkg
        )
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
    }

    // Holding both volume buttons together for a few seconds is not something a child does by accident
    // during normal use (unlike a single volume press) — a deliberate, hardware-level SOS trigger that
    // works even when the app itself isn't open, without needing the power button (which most OEMs and
    // Android itself intercept before it ever reaches an app or accessibility service).
    @Volatile private var volumeUpHeldSinceMs = 0L
    @Volatile private var volumeDownHeldSinceMs = 0L
    @Volatile private var sosComboFired = false
    private var sosComboJob: Job? = null

    override fun onKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> handleVolumeKey(event, isUp = true)
            KeyEvent.KEYCODE_VOLUME_DOWN -> handleVolumeKey(event, isUp = false)
        }
        return false // never consume — volume must keep working normally regardless of this watcher
    }

    private fun handleVolumeKey(event: KeyEvent, isUp: Boolean) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (isUp) {
                    if (volumeUpHeldSinceMs == 0L) volumeUpHeldSinceMs = System.currentTimeMillis()
                } else {
                    if (volumeDownHeldSinceMs == 0L) volumeDownHeldSinceMs = System.currentTimeMillis()
                }
                scheduleComboCheck()
            }
            KeyEvent.ACTION_UP -> {
                if (isUp) volumeUpHeldSinceMs = 0L else volumeDownHeldSinceMs = 0L
                sosComboFired = false
                sosComboJob?.cancel()
            }
        }
    }

    private fun scheduleComboCheck() {
        if (volumeUpHeldSinceMs == 0L || volumeDownHeldSinceMs == 0L || sosComboFired) return
        sosComboJob?.cancel()
        sosComboJob = serviceScope.launch {
            delay(SOS_COMBO_HOLD_MS)
            if (volumeUpHeldSinceMs != 0L && volumeDownHeldSinceMs != 0L && !sosComboFired) {
                sosComboFired = true
                sosTrigger.trigger(
                    this@ProtectionAccessibilityService,
                    message = "Ребёнок удержал обе кнопки громкости — сигнал SOS"
                )
            }
        }
    }

    /**
     * A picture-in-picture window (e.g. YouTube's mini-player) keeps playing on top of the home
     * screen after the child leaves its app "normally," completely outside MonitorForegroundService's
     * UsageStatsManager-based foreground tracking — its usage was silently uncounted, and even once
     * counted separately, showing our block overlay on top left the PiP window still visibly playing
     * underneath/alongside it (confirmed live: overlaying it is not the same as stopping it). Rather
     * than track a second, parallel "foreground app" and hope the dismiss-on-block races out in time,
     * PiP is simply never allowed to exist at all: the moment any window reports
     * isInPictureInPictureMode, it's dismissed immediately, before it could accrue any real watch
     * time unaccounted for. ACTION_DISMISS is the standard accessibility action PiP windows
     * implement so a TalkBack user can close them — confirmed live this actually closes the window
     * on stock/AOSP-close PiP implementations, but confirmed live on a second device that some OEM
     * PiP implementations (their own floating-video variant, not necessarily Android's own PIP API)
     * don't honor ACTION_DISMISS at all — hence the fallback below, tapping whatever close button
     * the window's own controls expose instead of relying on the generic action.
     */
    private fun updatePipState() {
        val now = System.currentTimeMillis()
        if (now - lastPipCheckAtMs < PIP_CHECK_DEBOUNCE_MS) return
        lastPipCheckAtMs = now
        val allWindows = runCatching { windows }.getOrNull().orEmpty()
        val pipWindow = allWindows.firstOrNull { it.isInPictureInPictureMode }
        val root = pipWindow?.root ?: return
        val dismissed = runCatching { root.performAction(AccessibilityNodeInfo.ACTION_DISMISS) }.getOrDefault(false)
        if (!dismissed) {
            runCatching { findAndClickCloseButton(root) }
        }
    }

    /** Fallback for OEM PiP windows that don't honor ACTION_DISMISS: PiP controls universally
     *  expose *some* close affordance for the user to tap, so this looks for one by the usual
     *  content-description/text wording instead of a specific (OEM-varying) resource id. */
    private fun findAndClickCloseButton(node: AccessibilityNodeInfo, budget: IntArray = intArrayOf(200)): Boolean {
        if (budget[0] <= 0) return false
        budget[0]--
        val label = (node.contentDescription?.toString() ?: node.text?.toString())?.lowercase().orEmpty()
        if (node.isClickable &&
            (label.contains("close") || label.contains("закрыть") || label == "×" || label == "✕")
        ) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickCloseButton(child, budget)) return true
        }
        return false
    }

    private fun ensureMonitorServiceRunning() {
        val now = System.currentTimeMillis()
        if (now - lastServiceCheckAtMs < SERVICE_CHECK_INTERVAL_MS) return
        lastServiceCheckAtMs = now

        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val running = manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == MonitorForegroundService::class.java.name }
        if (!running) MonitorForegroundService.start(this)
    }

    private fun checkAndBlockIfNeeded(eventPackage: String) {
        val now = System.currentTimeMillis()
        if (now - lastCheckAtMs < CHECK_DEBOUNCE_MS) return
        lastCheckAtMs = now
        doCheckAndBlock(eventPackage)

        // Some OEM Settings screens (confirmed live: Samsung's Device Admin apps list) still have
        // an empty/still-loading RecyclerView in the accessibility tree at the exact moment this
        // event fires — the list items attach a beat later with no further accessibility event to
        // prompt a re-check, since nothing else about the screen changes once they do. Same
        // debounce-with-settle shape as the YouTube history extractor for the same reason: don't
        // drop a check that landed inside content that hadn't finished loading yet.
        tamperSettleJob?.cancel()
        tamperSettleJob = serviceScope.launch {
            delay(TAMPER_SETTLE_DELAY_MS)
            doCheckAndBlock(eventPackage)
        }
    }

    /** [eventPackage] is the package whose window-state/content-change actually triggered this check
     *  — confirmed live (once, via diagnostic logging, not reproduced again in 9 further toggle
     *  cycles) that [rootInActiveWindow] can transiently point at a completely different window by
     *  the time this runs: a launcher window-state-change event fired right as accessibility focus
     *  briefly passed through the notification shade (which always carries this app's own "Nest Kid
     *  активен" persistent notification text), so the check read stale content unrelated to the
     *  event that scheduled it. Skipping when the two disagree costs nothing — whatever window
     *  actually is active will fire its own event and get checked correctly on its own. */
    private fun doCheckAndBlock(eventPackage: String) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != eventPackage) return
        val texts = mutableListOf<String>()
        collectText(root, texts, MAX_NODES)
        val screenText = texts.joinToString(" ").lowercase()

        // Samsung's own "device admin app is parental-control-protected" warning — shown as its own
        // step, before the app-details deactivation screen below, when deactivating a device admin
        // app Samsung itself recognizes as parental control. Confirmed live this dialog's text never
        // mentions the app by name at all ("Это приложение защищено родительским контролем и не
        // должно отключаться без разрешения родителя." — no "Nest Kid" anywhere), so every other
        // check here, all scoped to ownAppLabel being present, can't ever catch it. Matched on its
        // own distinctive wording instead — happens to arrive first, closing the gap before the
        // child ever reaches the (already-caught) admin-details screen after it.
        if (eventPackage == "com.android.settings" && screenText.contains("защищено родительским контролем")) {
            startActivity(Intent(this, PinChallengeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }

        // The system uninstall-confirmation dialog (long-press icon → Uninstall skips the launcher's
        // own popup on some OEM launchers/versions and goes straight here) folds our app's label into
        // one sentence ("Удалить приложение Nest Kid?"), not its own standalone text node — confirmed
        // live that this made hasDangerActionInOwnRow's exact-match row lookup below miss it entirely,
        // letting this path skip the PIN challenge completely. Safe to match loosely (app label +
        // danger word anywhere on screen, no row-scoping) because this dialog is only ever about the
        // one app it's asking to remove, unlike a multi-app Settings list.
        if (eventPackage in UNINSTALLER_PACKAGES &&
            screenText.contains(ownAppLabel) &&
            DANGER_KEYWORDS.any { screenText.contains(it) }
        ) {
            startActivity(Intent(this, PinChallengeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }

        // Our own app: a currently-ON protection switch (Accessibility/Device Admin/Usage Access/
        // Overlay all render this way once granted) is an unambiguous revoke attempt regardless of
        // the OEM/language's exact wording — confirmed live that Samsung's own accessibility-toggle
        // dialog reads "Выключить", not "Отключить", so a fixed keyword list alone missed it
        // entirely. During initial setup the same switch is still OFF, so this never blocks the
        // parent actually granting the permission in the first place. The keyword list remains as
        // a fallback for button-only screens with no switch at all, like Device Admin's
        // "Деактивировать" confirmation.
        //
        // Both signals are deliberately scoped to PROTECTION_SCREEN_MARKERS, not "any screen that
        // mentions our app" — confirmed live that scanning unconditionally false-triggered on the
        // Location settings screen (which lists recent per-app location access, ours included) and
        // even the Samsung app-drawer grid itself (just our icon+label, no toggle at all), both
        // launching the PIN challenge — and worse, PIN success there led to "Удалить приложение"
        // (uninstall), a completely wrong resolution for a screen that was never about uninstalling
        // anything. Restricting to screens that also carry one of these markers keeps the switch
        // check meaningful only where a real protection toggle can plausibly exist.
        if (screenText.contains(ownAppLabel)) {
            val isProtectionScreen = PROTECTION_SCREEN_MARKERS.any { screenText.contains(it) }
            val switchIsOn = if (isProtectionScreen) findOwnRowToggleChecked(root) else null
            val hasDangerAction = hasDangerActionInOwnRow(root)
            if (switchIsOn == true || hasDangerAction || (isProtectionScreen && switchIsOn == null && onboardingComplete)) {
                // No performGlobalAction(BACK) here: it's asynchronous and, confirmed live, can land
                // on the PinChallengeActivity we just launched instead of the Settings screen behind
                // it — dismissing our own PIN prompt a moment after it appears. Launching the PIN
                // screen already fully covers the toggle underneath, which is all "interrupt this"
                // actually requires.
                startActivity(
                    Intent(this, PinChallengeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }

        // A currently blocked/time-limited app: just deny, no PIN bypass — uninstalling it to
        // dodge its limit isn't something the family PIN should unlock. Deliberately no dialog-size
        // cap here: the real "Deactivate device admin app" screen is a full Settings activity
        // (title + description + buttons), easily exceeding a small node-count heuristic on many
        // OEM skins. Worst case without the cap is a harmless extra back-press on an unrelated
        // screen that happens to mention both a danger word and a protected app's name.
        val hasDangerAction = DANGER_KEYWORDS.any { screenText.contains(it) }
        if (hasDangerAction && restrictedAppLabels.any { screenText.contains(it) }) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    /** Neither signal alone is reliable across every OEM/Android permission screen — confirmed live
     *  that Samsung's accessibility-service toggle exposes isCheckable=true but a plain
     *  "Разрешить показ поверх других приложений" Switch (a standard AOSP PermissionController
     *  screen, not Samsung's own) exposes neither isCheckable nor a matching className at all, so
     *  even this combined check returns null there — see the onboardingComplete fallback above. */
    private fun findToggleChecked(node: AccessibilityNodeInfo?, budget: IntArray): Boolean? {
        if (node == null || budget[0] <= 0) return null
        budget[0]--
        val className = node.className?.toString().orEmpty()
        if (node.isCheckable || className.contains("Switch", ignoreCase = true) ||
            className.contains("ToggleButton", ignoreCase = true) || className.contains("CompoundButton", ignoreCase = true)
        ) {
            return node.isChecked
        }
        for (i in 0 until node.childCount) {
            findToggleChecked(node.getChild(i), budget)?.let { return it }
        }
        return null
    }

    /** Finds our own app's label node, then walks up through its ancestors looking for a toggle
     *  within just that row/card — a multi-app list (e.g. Device Admin apps) can list several apps'
     *  switches on one screen, and a global first-match search would just as happily return a
     *  different app's OFF switch instead of our own ON one. */
    private fun findOwnRowToggleChecked(root: AccessibilityNodeInfo): Boolean? {
        val labelNode = findNodeByText(root, ownAppLabel, intArrayOf(MAX_NODES)) ?: return null
        var ancestor: AccessibilityNodeInfo? = labelNode
        repeat(5) {
            ancestor = ancestor?.parent ?: return null
            findToggleChecked(ancestor, intArrayOf(MAX_NODES))?.let { return it }
        }
        return null
    }

    /** Same row/card scoping as [findOwnRowToggleChecked], applied to the danger-keyword check
     *  instead of a toggle — confirmed live that checking the *whole* screen's text let an
     *  unrelated word elsewhere on a busy Settings page (e.g. a generic "Отключить Wi-Fi" toggle
     *  on the same Location/Connections screen that separately, harmlessly lists "Nest Kid" under
     *  "recent app activity") false-trigger the PIN challenge for a screen that was never actually
     *  about our app at all — confirmed live twice, once toggling location and once toggling
     *  airplane mode, neither anywhere near uninstalling anything. */
    private fun hasDangerActionInOwnRow(root: AccessibilityNodeInfo): Boolean {
        val labelNode = findNodeByText(root, ownAppLabel, intArrayOf(MAX_NODES)) ?: return false
        var ancestor: AccessibilityNodeInfo? = labelNode
        repeat(5) {
            ancestor = ancestor?.parent ?: return false
            val texts = mutableListOf<String>()
            collectText(ancestor, texts, MAX_NODES)
            val rowText = texts.joinToString(" ").lowercase()
            if (DANGER_KEYWORDS.any { rowText.contains(it) }) return true
        }
        return false
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, target: String, budget: IntArray): AccessibilityNodeInfo? {
        if (node == null || budget[0] <= 0) return null
        budget[0]--
        if (node.text?.toString()?.trim()?.equals(target, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            findNodeByText(node.getChild(i), target, budget)?.let { return it }
        }
        return null
    }

    /** Own debounce + dedup, deliberately separate from checkAndBlockIfNeeded's 250ms — content-changed
     *  events fire on every scroll/redraw, so only an actual title/URL change should hit Firestore.
     *  An event landing inside the debounce window still schedules a delayed settle-check instead of
     *  being dropped outright — tapping a video from the "up next" list under the current one fires
     *  this event before the new title has actually loaded, and with a hard debounce alone that
     *  transition could be missed entirely if nothing else touches the screen afterward to trigger
     *  a later check. */
    private fun checkAndLogHistory(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastHistoryCheckAtMs < HISTORY_DEBOUNCE_MS) {
            scheduleHistorySettleCheck(pkg)
            return
        }
        lastHistoryCheckAtMs = now
        extractAndLogHistory(pkg)
    }

    private fun scheduleHistorySettleCheck(pkg: String) {
        historySettleJob?.cancel()
        historySettleJob = serviceScope.launch {
            delay(HISTORY_SETTLE_DELAY_MS)
            lastHistoryCheckAtMs = System.currentTimeMillis()
            extractAndLogHistory(pkg)
        }
    }

    private fun extractAndLogHistory(pkg: String) {
        val root = rootInActiveWindow ?: return
        val extracted = when (pkg) {
            "com.google.android.youtube" -> HistoryExtractor.extractYoutube(root)
            "com.google.android.googlequicksearchbox" -> HistoryExtractor.extractGoogleApp(root)
            else -> HistoryExtractor.extractChrome(root)
        } ?: return
        logHistoryEntry(pkg, extracted)
    }

    /** Captures a video's title the instant its row is tapped in a feed/search/recommendation list
     *  — confirmed live that the watch-screen-only extraction above sometimes missed a video
     *  entirely (an ad, or the title just not having rendered yet, ate the whole debounce window).
     *  The clicked row itself is fully rendered, ad-free content the moment it's tappable, so this
     *  runs independently of (and in addition to) the watch-screen path — see checkAndLogHistory's
     *  dedup, which already keeps an unchanged title from being logged twice. */
    private fun handleYoutubeVideoClick(event: AccessibilityEvent) {
        val source = event.source ?: return
        if (isWithinEngagementPanel(source)) return
        // The exact node clicked is usually just an inner thumbnail or label, not the row/card that
        // actually carries the title — walk up to the nearest clickable ancestor to reach it. A
        // fixed-depth walk (previously always 4 levels) used to routinely overshoot straight past
        // the row into the shared list container holding every row in the feed, at which point
        // extraction just grabbed whichever title happened to be topmost/first in that container —
        // confirmed live as "tap one video, a completely different (usually higher-up) one gets
        // logged." The row's own container is reliably the single clickable node between the
        // tapped element and that shared list, since individual rows are clickable but the list
        // itself isn't.
        val container = nearestClickableAncestor(source) ?: source
        val extracted = HistoryExtractor.extractClickedVideoTitle(container) ?: return
        logHistoryEntry("com.google.android.youtube", extracted)
    }

    private fun nearestClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 10) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    /** Taps inside an open episodes/chapters/transcript/comments panel (same "engagement panel"
     *  extractYoutube's passive-scan path already skips — see HistoryExtractor) are navigation
     *  within the video currently playing, not a switch to a different one. Confirmed live that
     *  tapping an episode card in the "Episodes" panel while fullscreen was getting logged as if
     *  it were a brand-new video through this click path, which doesn't share that guard. */
    private fun isWithinEngagementPanel(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 15) {
            // isVisibleToUser matters here too — YouTube keeps this node around even when the panel
            // is closed (see HistoryExtractor.extractYoutube's own version of this check).
            if (current.viewIdResourceName == "com.google.android.youtube:id/engagement_panel" && current.isVisibleToUser) return true
            current = current.parent
            depth++
        }
        return false
    }

    private fun logHistoryEntry(pkg: String, extracted: ExtractedHistory) {
        val familyId = currentFamilyId ?: return
        val signature = "$pkg|${extracted.title}|${extracted.url}"
        if (signature == lastLoggedSignature) return
        lastLoggedSignature = signature

        serviceScope.launch {
            runCatching {
                historyRepository.logEntry(
                    familyId,
                    HistoryEntry(
                        source = if (pkg == "com.google.android.youtube") HistorySource.YOUTUBE else HistorySource.CHROME,
                        url = extracted.url,
                        title = extracted.title,
                        domain = extracted.url?.let(HistoryExtractor::extractDomain),
                        channelName = extracted.channelName
                    )
                )
            }
        }
    }

    @Volatile private var lastVendingScanAtMs = 0L
    @Volatile private var pendingInstallAppName: String? = null
    @Volatile private var pendingInstallSeenAtMs = 0L
    @Volatile private var lastFiredInstallAppName: String? = null

    /** We can't stop the download itself (no Device Owner/MDM to block Play Store with), but the
     *  parent should find out the instant it starts, not only once ACTION_PACKAGE_ADDED fires after
     *  it's already fully installed. Best-effort: the app's name isn't known from a package-manager
     *  API yet (nothing's installed), so it's read straight off the Play Store listing screen.
     *
     *  Detection is a small state machine rather than catching one specific button text: remember
     *  the app name while its listing shows "Установить", then fire the instant that same app's
     *  button changes to anything else (Отмена/Открыть/Удалить). A one-shot "was it just showing
     *  Install, and is it now not" check — rather than trying to catch Play Store's transient
     *  in-progress state directly — because for small/fast-installing apps that state can be gone
     *  in under a second (confirmed live: Google Keep went Install -> Открыть in under the delay of
     *  a single follow-up screenshot), too brief to reliably land a scan on. This way it doesn't
     *  matter how long the transition takes, only that the button text actually changed. (Play
     *  Store's Install tap itself never reaches us as a TYPE_VIEW_CLICKED event at all — confirmed
     *  live, zero such events observed — so there's nothing to catch the click itself with.) */
    private fun checkPlayStoreInstallStarted() {
        val now = System.currentTimeMillis()
        if (now - lastVendingScanAtMs < 150L) return
        lastVendingScanAtMs = now
        val root = rootInActiveWindow ?: return
        val appName = findPlayStoreAppName(root)
        val showingInstallButton = containsAnyText(root, INSTALL_BUTTON_TEXTS, MAX_NODES)

        if (showingInstallButton) {
            if (appName != null) {
                pendingInstallAppName = appName
                pendingInstallSeenAtMs = now
            }
            return
        }

        val pending = pendingInstallAppName
        if (pending == null || pending != appName || now - pendingInstallSeenAtMs > PENDING_INSTALL_TIMEOUT_MS) {
            return
        }
        pendingInstallAppName = null
        if (pending == lastFiredInstallAppName) return
        lastFiredInstallAppName = pending
        val familyId = currentFamilyId ?: return

        serviceScope.launch {
            runCatching {
                eventRepository.logEvent(
                    familyId,
                    EventLogEntry(
                        type = EventType.INSTALL_ATTEMPT,
                        message = "Ребёнок пытается установить: $pending"
                    )
                )
            }
        }
    }

    private fun containsAnyText(root: AccessibilityNodeInfo, targets: Set<String>, budget: Int): Boolean {
        var count = 0
        fun walk(node: AccessibilityNodeInfo?): Boolean {
            if (node == null || count >= budget) return false
            count++
            val text = node.text?.toString()?.trim()
            if (text != null && targets.any { text.equals(it, ignoreCase = true) }) return true
            for (i in 0 until node.childCount) {
                if (walk(node.getChild(i))) return true
            }
            return false
        }
        return walk(root)
    }

    /** No stable resource id to rely on (Play Store is a Google-internal app, ids drift across
     *  versions) — the listing's title is reliably the topmost sizable text block on the page, so
     *  this takes the topmost candidate that isn't obvious UI chrome (button labels, star ratings,
     *  install-count text). Best-effort, same spirit as HistoryExtractor's heuristics. */
    private fun findPlayStoreAppName(root: AccessibilityNodeInfo): String? {
        val candidates = mutableListOf<Pair<String, Int>>()
        collectAppNameCandidates(root, candidates, 200)
        return candidates
            .filter { (text, _) ->
                text.length in 3..60 &&
                    PLAY_STORE_CHROME_WORDS.none { text.equals(it, ignoreCase = true) } &&
                    !text.all { c -> c.isDigit() || c == '.' || c == ',' || c == '+' || c == ' ' }
            }
            .minByOrNull { (_, top) -> top }
            ?.first
    }

    private fun collectAppNameCandidates(node: AccessibilityNodeInfo?, out: MutableList<Pair<String, Int>>, budget: Int) {
        if (node == null || out.size >= budget) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            out.add(it to bounds.top)
        }
        for (i in 0 until node.childCount) {
            if (out.size >= budget) break
            collectAppNameCandidates(node.getChild(i), out, budget)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private val UNINSTALLER_PACKAGES = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )
        private val PROTECTION_WATCHED_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.miui.securitycenter",
            "com.huawei.systemmanager",
            "com.samsung.android.lool",
            // Uninstalling straight from an app's own Play Store listing is a very common path
            // that isn't a Settings/installer screen at all — easy to miss without this.
            "com.android.vending",
            // Long-pressing a home-screen icon opens an "Удалить" popup rendered by the launcher
            // itself, never touching Settings/PackageInstaller at all — confirmed missed without
            // this on a real Samsung device (com.sec.android.app.launcher). Covering the other
            // common OEM launchers too since the child's device could change.
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.huawei.android.launcher",
            "com.miui.home"
        )
        private val HISTORY_WATCHED_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.google.android.youtube",
            "com.google.android.googlequicksearchbox"
        )
        private val INSTALL_BUTTON_TEXTS = setOf("установить", "install")
        private const val PENDING_INSTALL_TIMEOUT_MS = 15_000L
        private val PLAY_STORE_CHROME_WORDS = setOf(
            "установить", "install", "открыть", "open", "удалить", "uninstall", "обновить", "update",
            "оценить приложение", "рекомендуем", "лучшее", "детям", "категории", "игры", "приложения",
            "поиск", "search", "у вас", "google play", "отзывы", "о разработчике"
        )
        private const val MAX_NODES = 400
        private const val CHECK_DEBOUNCE_MS = 250L
        private const val PIP_CHECK_DEBOUNCE_MS = 1_000L
        private const val SERVICE_CHECK_INTERVAL_MS = 30_000L
        private const val SOS_COMBO_HOLD_MS = 3_000L
        private const val HISTORY_DEBOUNCE_MS = 3000L
        private const val HISTORY_SETTLE_DELAY_MS = 1200L
        private const val TAMPER_SETTLE_DELAY_MS = 600L
        private val DANGER_KEYWORDS = setOf(
            "удалить", "удаление", "деактивировать", "деактивация", "отключить", "отключение",
            "выключить", "выключение", "остановить", "запретить", "ограничить",
            "uninstall", "deactivate", "disable", "remove", "turn off", "stop"
        )
        // Restricts the switch-state check to screens that plausibly carry a real protection
        // toggle — an unscoped check false-triggered on the Location settings screen (recent
        // per-app location access, ours included) and even the bare app-drawer grid (just an
        // icon+label, no toggle at all). Substrings are chosen to match every observed inflection
        // (e.g. "администратор" covers "администратор/администраторы/администратора").
        private val PROTECTION_SCREEN_MARKERS = setOf(
            "специальные возможности", "accessibility",
            "администратор", "device admin",
            "данных об использовании", "usage access",
            "поверх других", "display over other apps"
        )

        private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>, budget: Int) {
            if (node == null || out.size >= budget) return
            node.text?.let { out.add(it.toString()) }
            node.contentDescription?.let { out.add(it.toString()) }
            for (i in 0 until node.childCount) {
                if (out.size >= budget) break
                collectText(node.getChild(i), out, budget)
            }
        }
    }
}
