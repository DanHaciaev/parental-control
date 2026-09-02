package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.teo.core.FirestorePaths
import com.teo.core.model.EventLogEntry
import com.teo.core.model.InstalledApp
import com.teo.core.model.MoreTimeRequest
import com.teo.core.model.RequestStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun familyRef(familyId: String) = firestore.collection(FirestorePaths.FAMILIES).document(familyId)
    private fun eventsRef(familyId: String) = familyRef(familyId).collection(FirestorePaths.EVENTS)
    private fun requestsRef(familyId: String) = familyRef(familyId).collection(FirestorePaths.REQUESTS)
    private fun installedAppsRef(familyId: String) = familyRef(familyId).collection(FirestorePaths.INSTALLED_APPS)

    suspend fun logEvent(familyId: String, event: EventLogEntry) {
        eventsRef(familyId).add(event).await()
    }

    fun observeRecentEvents(familyId: String, limit: Long = 50): Flow<List<EventLogEntry>> = callbackFlow {
        val registration = eventsRef(familyId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(EventLogEntry::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun markEventRead(familyId: String, eventId: String) {
        eventsRef(familyId).document(eventId).update("read", true).await()
    }

    /** Polled every ~15 min by the parent app while it's backgrounded, in lieu of push notifications. */
    suspend fun getUnreadEventCount(familyId: String): Int =
        eventsRef(familyId).whereEqualTo("read", false).get().await().size()

    /** One-shot diff source for the child's startup install scan — avoids re-logging apps it already knows about. */
    suspend fun getKnownPackageNames(familyId: String): Set<String> =
        installedAppsRef(familyId).get().await().documents.map { it.id }.toSet()

    fun observeInstalledApps(familyId: String): Flow<List<com.teo.core.model.InstalledApp>> = callbackFlow {
        val registration = installedAppsRef(familyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(com.teo.core.model.InstalledApp::class.java).orEmpty())
        }
        awaitClose { registration.remove() }
    }

    suspend fun recordNewInstall(familyId: String, packageName: String, appLabel: String, isSystemApp: Boolean) {
        installedAppsRef(familyId).document(packageName).set(
            InstalledApp(packageName = packageName, appLabel = appLabel, isSystemApp = isSystemApp)
        ).await()
        logEvent(
            familyId,
            EventLogEntry(
                type = com.teo.core.model.EventType.NEW_INSTALL,
                packageName = packageName,
                message = "Установлено новое приложение: $appLabel"
            )
        )
    }

    suspend fun createMoreTimeRequest(familyId: String, packageName: String): String {
        val doc = requestsRef(familyId).add(MoreTimeRequest(packageName = packageName)).await()
        return doc.id
    }

    fun observePendingRequests(familyId: String): Flow<List<MoreTimeRequest>> = callbackFlow {
        val registration = requestsRef(familyId)
            .whereEqualTo("status", RequestStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(MoreTimeRequest::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun resolveRequest(familyId: String, requestId: String, status: RequestStatus, minutes: Int?) {
        requestsRef(familyId).document(requestId).update(
            mapOf(
                "status" to status.name,
                "resolvedMinutes" to minutes
            )
        ).await()
    }
}
