package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.AssignedSkill
import com.teo.core.model.SkillProgressEntry
import com.teo.core.model.SkillTemplate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ready-made daily quotas ("Логика 0 из 20 мин", "Приседания 0 из 20") a parent assigns from
 * SkillTemplateCatalog instead of typing a custom Task. Reward granting is deliberately NOT done
 * here — same separation TaskRepository documents — because there's no Cloud Functions backend
 * (free Spark plan) to safely auto-grant the instant a child-controlled counter crosses its
 * target; a child-writable reward path would be directly exploitable via raw Firestore calls.
 * Progress only flips to "awaiting confirmation" here; RuleRepository.grantBonusMinutes is called
 * separately by the parent-side caller once they confirm.
 */
@Singleton
class SkillRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun assignedRef(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId).collection(FirestorePaths.ASSIGNED_SKILLS)

    private fun progressRef(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId).collection(FirestorePaths.SKILL_PROGRESS)

    fun observeAssignedSkills(familyId: String): Flow<List<AssignedSkill>> = callbackFlow {
        val registration = assignedRef(familyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(AssignedSkill::class.java).orEmpty())
        }
        awaitClose { registration.remove() }
    }

    suspend fun assignSkill(
        familyId: String,
        template: SkillTemplate,
        targetPerDay: Int,
        rewardMinutes: Int,
        rewardPackageName: String?
    ) {
        assignedRef(familyId).document(template.id).set(
            AssignedSkill(
                id = template.id,
                templateId = template.id,
                title = template.title,
                emoji = template.emoji,
                category = template.category,
                unit = template.unit.name,
                targetPerDay = targetPerDay,
                rewardMinutes = rewardMinutes,
                rewardPackageName = rewardPackageName
            )
        ).await()
    }

    suspend fun unassignSkill(familyId: String, skillId: String) {
        assignedRef(familyId).document(skillId).delete().await()
    }

    fun observeProgressForDay(familyId: String, dateKey: String): Flow<List<SkillProgressEntry>> = callbackFlow {
        val registration = progressRef(familyId)
            .whereEqualTo("dateKey", dateKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(SkillProgressEntry::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    /** Transaction so two quick taps on the child's "+1" button don't lose an increment to a race.
     *  No-ops once the day's entry is already approved — matches the child UI disabling the tap
     *  target past target, but guarded here too since the UI state is never fully trustworthy. */
    suspend fun incrementProgress(familyId: String, skillId: String, dateKey: String, amount: Int) {
        val docRef = progressRef(familyId).document("${dateKey}_$skillId")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            if (snapshot.getBoolean("approved") == true) return@runTransaction
            val current = snapshot.getLong("progress")?.toInt() ?: 0
            transaction.set(
                docRef,
                SkillProgressEntry(dateKey = dateKey, skillId = skillId, progress = current + amount)
            )
        }.await()
    }

    /** Absolute set rather than a delta — used by sensor-driven progress (steps), which already
     *  knows the true daily total from the device's step counter, unlike the tap-driven
     *  [incrementProgress] path. No transaction needed since there's no read-then-add race. */
    suspend fun setProgress(familyId: String, skillId: String, dateKey: String, progress: Int) {
        val docRef = progressRef(familyId).document("${dateKey}_$skillId")
        if (docRef.get().await().getBoolean("approved") == true) return
        docRef.set(SkillProgressEntry(dateKey = dateKey, skillId = skillId, progress = progress)).await()
    }

    /** Parent-only per firestore.rules — the actual reward grant is a separate
     *  RuleRepository.grantBonusMinutes call made by the ViewModel right after this succeeds. */
    suspend fun approveSkillProgress(familyId: String, skillId: String, dateKey: String) {
        progressRef(familyId).document("${dateKey}_$skillId").update("approved", true).await()
    }
}
