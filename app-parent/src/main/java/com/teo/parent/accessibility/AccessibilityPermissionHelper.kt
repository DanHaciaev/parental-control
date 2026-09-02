package com.teo.parent.accessibility

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

object AccessibilityPermissionHelper {

    fun isEnabled(context: Context): Boolean {
        val expectedComponent = "${context.packageName}/${ProtectionAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
        for (component in splitter) {
            if (component.equals(expectedComponent, ignoreCase = true)) return true
        }
        return false
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
