package com.teo.child.permission

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Optional permission — only needed to flip ringer mode away from silent; the alarm-stream ring works without it. */
object NotificationPolicyPermissionHelper {

    fun isGranted(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.isNotificationPolicyAccessGranted
    }

    fun openSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }
}
