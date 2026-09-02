package com.teo.parent.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.teo.parent.admin.DeviceAdminHelper
import com.teo.parent.pin.PinChallengeActivity

/**
 * Watches for attempts to disable this app's Device Admin or uninstall it via system Settings,
 * backs out immediately, and redirects to the in-app PIN challenge instead.
 */
class ProtectionAccessibilityService : AccessibilityService() {

    private val ownAppLabel: String by lazy {
        packageManager.getApplicationLabel(applicationInfo).toString().lowercase()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventPackage = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (eventPackage !in WATCHED_PACKAGES) return
        if (!DeviceAdminHelper.isActive(this)) return

        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectText(root, texts, MAX_NODES)
        val screenText = texts.joinToString(" ").lowercase()

        if (screenText.contains(ownAppLabel)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            startActivity(
                Intent(this, PinChallengeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onInterrupt() {}

    companion object {
        private val WATCHED_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )
        private const val MAX_NODES = 400

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
