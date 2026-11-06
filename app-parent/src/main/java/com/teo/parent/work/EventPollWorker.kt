package com.teo.parent.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.teo.parent.notifications.EventNotifications
import com.teo.parent.notifications.SosNotifications
import com.teo.core.model.EventType
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Fallback for when ParentAlertService's live listener wasn't running (app force-killed, device
 * rebooted before it restarted, etc.) — polled every ~15 min, the shortest interval WorkManager's
 * periodic work allows.
 */
@HiltWorker
class EventPollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid ?: return Result.success()

        return try {
            val families = familyRepository.familiesForParent(uid)
            families.forEach { family ->
                val childLabel = if (families.size > 1) family.childName?.takeIf { it.isNotBlank() } else null
                // Per-parent notifiedTo rather than the shared read flag — see EventLogEntry.notifiedTo.
                val pending = eventRepository.getEventsNotNotified(family.id, uid)
                if (pending.any { it.type == EventType.SOS }) {
                    SosNotifications.show(applicationContext)
                }
                val other = pending.filterNot { it.type == EventType.SOS }
                if (other.isNotEmpty()) {
                    val deepLinkInstalls = other.any { it.type == EventType.INSTALL_ATTEMPT || it.type == EventType.NEW_INSTALL }
                    val messages = other.map { e -> childLabel?.let { "$it: ${e.message}" } ?: e.message }
                    EventNotifications.show(applicationContext, messages, deepLinkInstalls)
                }
                // Without this, every ~15-minute run would re-notify (siren included) for the same
                // events forever.
                pending.forEach { event -> runCatching { eventRepository.markEventNotified(family.id, event.id, uid) } }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
