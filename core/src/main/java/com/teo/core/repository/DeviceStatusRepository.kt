package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
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
