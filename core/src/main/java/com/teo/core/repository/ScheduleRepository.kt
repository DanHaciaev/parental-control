package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.Family
import com.teo.core.model.Schedule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun schedulesRef(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId).collection(FirestorePaths.SCHEDULES)

    fun observeSchedules(familyId: String): Flow<List<Schedule>> = callbackFlow {
        val registration = schedulesRef(familyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(Schedule::class.java).orEmpty())
        }
        awaitClose { registration.remove() }
    }

    suspend fun getSchedules(familyId: String): List<Schedule> =
        schedulesRef(familyId).get().await().toObjects(Schedule::class.java)

    /** Generated-id doc — unlike AppRule's natural packageName key, a family can have many schedules. */
    suspend fun setSchedule(familyId: String, schedule: Schedule) {
        val docRef = if (schedule.id.isBlank()) schedulesRef(familyId).document() else schedulesRef(familyId).document(schedule.id)
        docRef.set(schedule.copy(id = docRef.id)).await()
    }

    suspend fun deleteSchedule(familyId: String, scheduleId: String) {
        schedulesRef(familyId).document(scheduleId).delete().await()
    }

    /** One-time, idempotent: turns a pre-existing Family.bedtime* pair into a first-class "Сон" schedule. */
    suspend fun migrateLegacyBedtimeIfNeeded(familyId: String, family: Family) {
        val start = family.bedtimeStartMinutes ?: return
        val end = family.bedtimeEndMinutes ?: return
        val existing = getSchedules(familyId)
        if (existing.any { it.name == "Сон" }) return
        setSchedule(
            familyId,
            Schedule(name = "Сон", startMinutes = start, endMinutes = end, blockAllApps = true, enabled = true)
        )
    }
}
