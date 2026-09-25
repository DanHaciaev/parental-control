package com.teo.child.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.teo.child.admin.DeviceAdminHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PermissionsUiState(
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
    val batteryExempt: Boolean = false,
    val deviceAdmin: Boolean = false,
    val accessibility: Boolean = false,
    val locationForeground: Boolean = false,
    val locationBackground: Boolean = false,
    /** Optional — only needed for the parent's "включить звук" ringer-mode control; never blocks onboarding. */
    val notificationPolicyAccess: Boolean = false,
    /** Optional — lets the SOS button place the call directly instead of opening the dialer; never blocks onboarding. */
    val callPhone: Boolean = false,
    /** Optional — lets the app notice any incoming call is ringing and unmute for it. */
    val readPhoneState: Boolean = false,
    /** Optional — lets the "Шаги" skill auto-track from the phone's step sensor instead of manual taps. */
    val activityRecognition: Boolean = false,
    /** Optional — needed for "Послушать вокруг"; once granted, the mic starts automatically on
     *  every parent request with no further on-device prompt. Never blocks onboarding. */
    val recordAudio: Boolean = false
) {
    val allGranted: Boolean
        get() = usageAccess && overlay && notifications && batteryExempt && deviceAdmin &&
            accessibility && locationForeground && locationBackground
}

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Special-permission grants only take effect after the user returns from Settings — call on resume. */
    fun refresh() {
        _uiState.value = PermissionsUiState(
            usageAccess = UsageAccessHelper.hasUsageAccess(context),
            overlay = OverlayPermissionHelper.hasOverlayPermission(context),
            notifications = hasNotificationPermission(context),
            batteryExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context),
            deviceAdmin = DeviceAdminHelper.isActive(context),
            accessibility = AccessibilityPermissionHelper.isEnabled(context),
            locationForeground = LocationPermissionHelper.hasForegroundLocation(context),
            locationBackground = LocationPermissionHelper.hasBackgroundLocation(context),
            notificationPolicyAccess = NotificationPolicyPermissionHelper.isGranted(context),
            callPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED,
            readPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED,
            activityRecognition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
            recordAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    fun setNotificationsGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(notifications = granted)
    }

    fun setCallPhoneGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(callPhone = granted)
    }

    fun setReadPhoneStateGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(readPhoneState = granted)
    }

    fun setLocationForegroundGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(locationForeground = granted)
    }

    fun setActivityRecognitionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(activityRecognition = granted)
    }

    fun setRecordAudioGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(recordAudio = granted)
    }
}
