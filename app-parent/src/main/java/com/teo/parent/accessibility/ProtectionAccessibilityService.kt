package com.teo.parent.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.teo.parent.admin.DeviceAdminHelper
import com.teo.parent.pin.PinChallengeActivity
import com.teo.parent.work.ParentAlertService

/**
 * Two jobs, same reasoning as app-child's copy of this service for bundling them into one:
 *
 * 1. Watches for attempts to disable this app's Device Admin or uninstall it via system Settings,
 *    backs out immediately, and redirects to the in-app PIN challenge instead.
 * 2. Keeps [ParentAlertService] alive — confirmed live (dumpsys showed the process running but
 *    that specific service absent from "active services") that it has no restart path of its own:
 *    it's only ever started once, when the app UI navigates to the dashboard
 *    (see ParentNavHost.kt), so once Android (or a manufacturer's own aggressive battery manager —
 *    confirmed on this Huawei device) kills it in the background, it just stays dead until the
 *    parent happens to reopen the app. This accessibility service is bound by the system and far
 *    harder to kill, so it's a natural place to notice and restart it — same self-healing pattern
 *    already used by app-child's MonitorForegroundService/ensureMonitorServiceRunning.
 */
class ProtectionAccessibilityService : AccessibilityService() {

    private val ownAppLabel: String by lazy {
        packageManager.getApplicationLabel(applicationInfo).toString().lowercase()
    }
    @Volatile private var lastServiceCheckAtMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        ensureAlertServiceRunning()

        val eventPackage = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (eventPackage !in WATCHED_PACKAGES) return
        if (!DeviceAdminHelper.isActive(this)) return

        val root = rootInActiveWindow ?: return
        // Confirmed live (app-child's copy of this service hit the same issue): rootInActiveWindow
        // can transiently point at a different window than the one whose event just fired — e.g. a
        // launcher window-state-change firing right as accessibility focus briefly passed through
        // the notification shade, which always carries this app's own "Nest Parent следит за
        // уведомлениями" persistent notification (ParentAlertService). Without this check, pulling
        // down the shade for something unrelated (confirmed live: switching the USB connection mode
        // after plugging into a PC) read that stale notification-shade content instead, found
        // "Nest Parent" sitting near an unrelated "отключить"-type word on the same panel, and
        // false-triggered the PIN challenge and uninstall offer for a screen that was never about
        // this app at all.
        if (root.packageName?.toString() != eventPackage) return
        val texts = mutableListOf<String>()
        collectText(root, texts, MAX_NODES)
        val screenText = texts.joinToString(" ").lowercase()
        if (!screenText.contains(ownAppLabel)) return

        // Scoped to just the row/card containing our own app's label, not the whole screen's text —
        // same fix already applied to app-child's copy of this service: checking the whole screen
        // let an unrelated danger word elsewhere on a busy Settings page (or, as above, an unrelated
        // toggle sharing the notification shade) false-trigger this for a screen that was never
        // actually about our app.
        if (hasDangerActionInOwnRow(root)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            startActivity(
                Intent(this, PinChallengeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun ensureAlertServiceRunning() {
        val now = System.currentTimeMillis()
        if (now - lastServiceCheckAtMs < SERVICE_CHECK_INTERVAL_MS) return
        lastServiceCheckAtMs = now

        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val running = manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == ParentAlertService::class.java.name }
        if (!running) ParentAlertService.start(this)
    }

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

    override fun onInterrupt() {}

    companion object {
        private val WATCHED_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.vending",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.huawei.android.launcher",
            "com.miui.home"
        )
        private const val MAX_NODES = 400
        private const val SERVICE_CHECK_INTERVAL_MS = 30_000L
        private val DANGER_KEYWORDS = setOf(
            "удалить", "удаление", "деактивировать", "деактивация", "отключить", "отключение",
            "uninstall", "deactivate", "disable", "remove"
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
