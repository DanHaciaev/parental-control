package com.teo.child.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent

object DeviceAdminHelper {

    fun isActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ProtectionDeviceAdminReceiver.componentName(context))
    }

    /**
     * Deliberately no FLAG_ACTIVITY_NEW_TASK: the system's DeviceAdminAdd screen refuses to
     * open as the root of a new task ("Cannot start ADD_DEVICE_ADMIN as a new task" in logcat)
     * and immediately closes itself — call this with an Activity context.
     */
    fun requestActivation(context: Context) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ProtectionDeviceAdminReceiver.componentName(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(com.teo.child.R.string.device_admin_description)
            )
        }
        context.startActivity(intent)
    }

    /** Self-relinquish — an admin app can always drop its own admin status with no user confirmation. */
    fun relinquish(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.removeActiveAdmin(ProtectionDeviceAdminReceiver.componentName(context))
    }
}
