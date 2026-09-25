package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.HourlyUsageEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HourlyUsageRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun hourlyRef(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId).collection(FirestorePaths.USAGE_HOURLY)

    /** Batched upload — called by the periodic worker, not per-tick, to stay well within the free quota. */
    suspend fun uploadHourlyUsage(familyId: String, entries: List<HourlyUsageEntry>) {
        if (entries.isEmpty()) return
        val batch = firestore.batch()
        entries.forEach { entry ->
            val docId = "${entry.dateKey}_${entry.hour.toString().padStart(2, '0')}"
            batch.set(hourlyRef(familyId).document(docId), entry)
        }
        batch.commit().await()
    }

    fun observeHourlyUsageForDay(familyId: String, dateKey: String): Flow<List<HourlyUsageEntry>> = callbackFlow {
        val registration = hourlyRef(familyId)
            .whereEqualTo("dateKey", dateKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(HourlyUsageEntry::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }
}
