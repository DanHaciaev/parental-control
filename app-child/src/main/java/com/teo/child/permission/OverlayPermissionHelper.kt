package com.teo.child.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object OverlayPermissionHelper {

    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}
