package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.UsageEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun usageRef(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId).collection(FirestorePaths.USAGE_DAILY)

    /** Batched upload — called by the periodic worker, not per-tick, to stay well within the free quota. */
    suspend fun uploadUsage(familyId: String, entries: List<UsageEntry>) {
        if (entries.isEmpty()) return
        val batch = firestore.batch()
        entries.forEach { entry ->
            val docId = "${entry.dateKey}_${entry.packageName}"
            batch.set(usageRef(familyId).document(docId), entry)
        }
        batch.commit().await()
    }

    fun observeUsageForDay(familyId: String, dateKey: String): Flow<List<UsageEntry>> = callbackFlow {
        val registration = usageRef(familyId)
            .whereEqualTo("dateKey", dateKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(UsageEntry::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun getUsageForRange(familyId: String, fromDateKey: String, toDateKey: String): List<UsageEntry> =
        usageRef(familyId)
            .whereGreaterThanOrEqualTo("dateKey", fromDateKey)
            .whereLessThanOrEqualTo("dateKey", toDateKey)
            .get().await()
            .toObjects(UsageEntry::class.java)
}
