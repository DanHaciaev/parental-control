package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.teo.core.FirestorePaths
import com.teo.core.model.HistoryEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun historyRef(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId).collection(FirestorePaths.HISTORY)

    suspend fun logEntry(familyId: String, entry: HistoryEntry) {
        historyRef(familyId).add(entry).await()
    }

    fun observeRecent(familyId: String, limit: Long = 200): Flow<List<HistoryEntry>> = callbackFlow {
        val registration = historyRef(familyId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(HistoryEntry::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }
}
