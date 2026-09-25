package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.teo.core.FirestorePaths
import com.teo.core.model.DeviceStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStatusRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun statusDoc(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId)
            .collection(FirestorePaths.DEVICE_STATUS).document(FirestorePaths.DEVICE_STATUS_CURRENT_DOC)

    suspend fun updateStatus(familyId: String, status: DeviceStatus) {
        statusDoc(familyId).set(status).await()
    }

    /**
     * Partial write for the periodic health-check fields only. Must NOT touch currentForegroundApp/
     * currentForegroundAppLabel — those are written far more often by [updateForegroundApp], and a full
     * [updateStatus] object built without them would otherwise null them back out every time this runs.
     */
    suspend fun updateHealthStatus(familyId: String, status: DeviceStatus) {
        statusDoc(familyId).set(
            mapOf(
                "batteryPercent" to status.batteryPercent,
                // Firestore's Kotlin bean-mapper strips the "is" prefix off boolean properties, so the
                // field that round-trips through DeviceStatus.isCharging is "charging", not "isCharging" —
                // matching that here keeps this write consistent with the full-object updateStatus() path.
                "charging" to status.isCharging,
                "appVersion" to status.appVersion,
                "osVersion" to status.osVersion,
                "protectionServiceRunning" to status.protectionServiceRunning,
                "accessibilityEnabled" to status.accessibilityEnabled,
                "usageAccessEnabled" to status.usageAccessEnabled,
                "overlayEnabled" to status.overlayEnabled,
                "deviceAdminActive" to status.deviceAdminActive,
                "batteryOptimizationExempt" to status.batteryOptimizationExempt,
                "notificationPolicyAccess" to status.notificationPolicyAccess
            ),
            SetOptions.merge()
        ).await()
    }

    /** Partial write — used for the frequently-changing "current app" field between full status syncs. */
    suspend fun updateForegroundApp(familyId: String, packageName: String?, appLabel: String?) {
        statusDoc(familyId).set(
            mapOf(
                "currentForegroundApp" to packageName,
                "currentForegroundAppLabel" to appLabel
            ),
            SetOptions.merge()
        ).await()
    }

    fun observeStatus(familyId: String): Flow<DeviceStatus?> = callbackFlow {
        val registration = statusDoc(familyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(DeviceStatus::class.java))
        }
        awaitClose { registration.remove() }
    }
}
