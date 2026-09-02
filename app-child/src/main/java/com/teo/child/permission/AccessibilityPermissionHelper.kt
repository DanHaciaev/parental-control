package com.teo.child.permission

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.teo.child.accessibility.ProtectionAccessibilityService

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
