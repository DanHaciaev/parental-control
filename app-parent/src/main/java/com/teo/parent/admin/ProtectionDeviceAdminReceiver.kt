package com.teo.parent.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class ProtectionDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Введите код защиты в приложении, чтобы отключить эту защиту."

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, ProtectionDeviceAdminReceiver::class.java)
    }
}
