package com.teo.child.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.teo.child.admin.DeviceAdminHelper
import com.teo.child.data.local.RuleCacheDao
import com.teo.core.model.RuleMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Defensive backstop: watches Settings / package-installer windows for attempts to uninstall
 * this app or any currently blocked/time-limited app, and backs out immediately.
 * The real "release" path for this app itself is the remote RELEASE_PROTECTION command
 * (handled in MonitorForegroundService), which self-relinquishes admin with no UI at all —
 * this service exists purely to catch someone trying to go around that through Settings.
 */
@AndroidEntryPoint
class ProtectionAccessibilityService : AccessibilityService() {

    @Inject lateinit var ruleCacheDao: RuleCacheDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var restrictedAppLabels: Set<String> = emptySet()
    private val ownAppLabel: String by lazy { packageManager.getApplicationLabel(applicationInfo).toString().lowercase() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            ruleCacheDao.observeAll().collect { rules ->
                restrictedAppLabels = rules
                    .filter { it.mode == RuleMode.BLOCKED.name || it.mode == RuleMode.TIME_LIMIT.name }
                    .map { it.appLabel.lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventPackage = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (eventPackage !in WATCHED_PACKAGES) return
        checkAndBlockIfNeeded()
    }

    private fun checkAndBlockIfNeeded() {
        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectText(root, texts, MAX_NODES)
        val screenText = texts.joinToString(" ").lowercase()

        val protectedLabels = if (DeviceAdminHelper.isActive(this)) {
            restrictedAppLabels + ownAppLabel
        } else {
            restrictedAppLabels
        }

        // A real uninstall/disable confirmation is a small, focused dialog (app name, one question,
        // two buttons). A regular settings page — App info, Permissions, Location — has many more
        // text nodes even though it also carries an incidental "Удалить"/"Отключить" button somewhere
        // (e.g. App info always has an Uninstall button). Gating on node count keeps this from
        // locking the parent out of ordinary navigation while still catching the actual danger screen.
        val looksLikeConfirmationDialog = texts.size in 1..MAX_DIALOG_NODES
        val hasDangerAction = looksLikeConfirmationDialog && DANGER_KEYWORDS.any { screenText.contains(it) }
        if (hasDangerAction && protectedLabels.any { screenText.contains(it) }) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private val WATCHED_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )
        private const val MAX_NODES = 400
        private const val MAX_DIALOG_NODES = 12
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
