package com.teo.core.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.teo.core.FirestorePaths
import com.teo.core.model.Task
import com.teo.core.model.TaskStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parent-assigned chores that reward bonus screen minutes on approval.
 * Reuses RuleRepository.grantBonusMinutes for the actual reward — kept as a
 * separate orchestration step in the caller rather than a cross-repo dependency.
 */
@Singleton
class TaskRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun tasksRef(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId).collection(FirestorePaths.TASKS)

    suspend fun createTask(familyId: String, title: String, rewardMinutes: Int, rewardPackageName: String?): String {
        val doc = tasksRef(familyId).add(
            Task(title = title, rewardMinutes = rewardMinutes, rewardPackageName = rewardPackageName)
        ).await()
        return doc.id
    }

    fun observeTasks(familyId: String): Flow<List<Task>> = callbackFlow {
        val registration = tasksRef(familyId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Task::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun markDoneByChild(familyId: String, taskId: String) {
        tasksRef(familyId).document(taskId).update(
            mapOf(
                "status" to TaskStatus.DONE_BY_CHILD.name,
                "completedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun approveTask(familyId: String, taskId: String) {
        tasksRef(familyId).document(taskId).update("status", TaskStatus.APPROVED.name).await()
    }

    suspend fun rejectTask(familyId: String, taskId: String) {
        tasksRef(familyId).document(taskId).update("status", TaskStatus.REJECTED.name).await()
    }

    suspend fun deleteTask(familyId: String, taskId: String) {
        tasksRef(familyId).document(taskId).delete().await()
    }
}
