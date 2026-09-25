package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.CommandStatus
import com.teo.core.model.CommandType
import com.teo.core.model.Device
import com.teo.core.model.PendingCommand
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Non-phone devices (currently just the Windows laptop client) — see [Device] for why this is a
 *  sibling to the phone's single-slot childUid rather than a replacement for it. */
@Singleton
class DeviceRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun devicesRef(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId).collection(FirestorePaths.DEVICES)

    fun observeDevices(familyId: String): Flow<List<Device>> = callbackFlow {
        val registration = devicesRef(familyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(Device::class.java) ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    suspend fun setDeviceDailyLimit(familyId: String, deviceId: String, minutes: Int?) {
        devicesRef(familyId).document(deviceId).update("dailyLimitMinutes", minutes).await()
    }

    suspend fun setDeviceLabel(familyId: String, deviceId: String, label: String) {
        devicesRef(familyId).document(deviceId).update("label", label).await()
    }

    suspend fun removeDevice(familyId: String, deviceId: String) {
        devicesRef(familyId).document(deviceId).delete().await()
    }

    suspend fun sendDeviceCommand(familyId: String, deviceId: String, type: CommandType) {
        devicesRef(familyId).document(deviceId).update(
            "pendingCommand",
            PendingCommand(type = type, status = CommandStatus.PENDING)
        ).await()
    }
}
