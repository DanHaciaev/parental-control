package com.teo.child.permission

import android.content.Context
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
    val locationBackground: Boolean = false
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
            locationBackground = LocationPermissionHelper.hasBackgroundLocation(context)
        )
    }

    fun setNotificationsGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(notifications = granted)
    }

    fun setLocationForegroundGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(locationForeground = granted)
    }
}
