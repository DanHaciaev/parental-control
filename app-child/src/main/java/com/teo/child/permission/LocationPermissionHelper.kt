package com.teo.child.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Android 11+ never offers "Allow all the time" in the runtime dialog — foreground location
 * must be granted first, then background is a separate manual trip to app settings.
 */
object LocationPermissionHelper {

    fun hasForegroundLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * ACCESS_BACKGROUND_LOCATION didn't exist as a separate permission before Android 10 (API 29)
     * — foreground location already covered background use on those OS versions. Checking it via
     * checkSelfPermission on an older OS returns DENIED forever (the platform has no record of
     * "granting" a permission it doesn't recognize), even though nothing is actually restricted.
     */
    fun hasBackgroundLocation(context: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            hasForegroundLocation(context)
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun openAppSettingsForBackgroundLocation(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
