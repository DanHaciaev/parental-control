package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.CommandStatus
import com.teo.core.model.CommandType
import com.teo.core.model.Family
import com.teo.core.model.PendingCommand
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parent -> child commands ride the family doc's `pendingCommand` field, delivered instantly
 * via the Firestore listener the child's foreground service already keeps open (no FCM/server needed).
 */
@Singleton
class CommandRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun familyDoc(familyId: String) = firestore.collection(FirestorePaths.FAMILIES).document(familyId)

    suspend fun sendCommand(familyId: String, type: CommandType) {
        familyDoc(familyId).update(
            "pendingCommand",
            PendingCommand(type = type, status = CommandStatus.PENDING)
        ).await()
    }

    suspend fun acknowledgeCommand(familyId: String) {
        familyDoc(familyId).update("pendingCommand.status", CommandStatus.DONE.name).await()
    }

    fun observePendingCommand(familyId: String): Flow<PendingCommand?> = callbackFlow {
        val registration = familyDoc(familyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(Family::class.java)?.pendingCommand)
        }
        awaitClose { registration.remove() }
    }.map { command -> command?.takeIf { it.status == CommandStatus.PENDING } }
}
