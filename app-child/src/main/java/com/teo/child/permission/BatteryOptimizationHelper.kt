package com.teo.child.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens this app's own system settings page, where the parent sets Battery → "Unrestricted"
     *  ("Без ограничений" on Samsung). Not the one-tap ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     *  dialog: that needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which Google Play allows only for
     *  a short list of app categories. */
    fun requestIgnore(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}
