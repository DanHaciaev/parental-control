package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.LocationPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun locationDoc(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId)
            .collection(FirestorePaths.LOCATION).document(FirestorePaths.LOCATION_CURRENT_DOC)

    suspend fun updateLocation(familyId: String, point: LocationPoint) {
        locationDoc(familyId).set(point).await()
    }

    fun observeLocation(familyId: String): Flow<LocationPoint?> = callbackFlow {
        val registration = locationDoc(familyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(LocationPoint::class.java))
        }
        awaitClose { registration.remove() }
    }
}
