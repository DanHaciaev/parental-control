package com.teo.child.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class ProtectionDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Это приложение защищено родительским контролем и не должно отключаться без разрешения родителя."

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, ProtectionDeviceAdminReceiver::class.java)
    }
}
