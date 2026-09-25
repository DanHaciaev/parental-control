package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.AppRule
import com.teo.core.model.RuleMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun rulesRef(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId).collection(FirestorePaths.RULES)

    fun observeRules(familyId: String): Flow<List<AppRule>> = callbackFlow {
        val registration = rulesRef(familyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(AppRule::class.java).orEmpty())
        }
        awaitClose { registration.remove() }
    }

    suspend fun getRules(familyId: String): List<AppRule> =
        rulesRef(familyId).get().await().toObjects(AppRule::class.java)

    suspend fun setRule(familyId: String, rule: AppRule) {
        rulesRef(familyId).document(rule.packageName).set(rule).await()
    }

    suspend fun removeRule(familyId: String, packageName: String) {
        rulesRef(familyId).document(packageName).delete().await()
    }

    /** Sets a single weekday's limit without touching the other 6 or the flat fallback — a plain
     *  [setRule] would overwrite the whole document, clobbering whatever the other days were already
     *  set to. Read-modify-write instead of a Firestore dotted-field merge because the security rule
     *  on this collection validates the full resulting `dailyLimitMinutes` field, which a partial
     *  map write would otherwise omit on a brand-new document. [isoDayOfWeek] is 1=Monday..7=Sunday,
     *  matching [AppRule.weeklyLimitMinutes]'s keys. */
    suspend fun setDayLimit(familyId: String, packageName: String, appLabel: String, isoDayOfWeek: Int, minutes: Int) {
        val docRef = rulesRef(familyId).document(packageName)
        val existing = docRef.get().await().toObject(AppRule::class.java)
        val updatedWeekly = (existing?.weeklyLimitMinutes.orEmpty()) + (isoDayOfWeek.toString() to minutes)
        docRef.set(
            (existing ?: AppRule(packageName = packageName, appLabel = appLabel)).copy(
                mode = RuleMode.TIME_LIMIT,
                weeklyLimitMinutes = updatedWeekly
            )
        ).await()
    }

    /** Removes just one weekday's override, leaving the other days and the flat fallback untouched. */
    suspend fun clearDayLimit(familyId: String, packageName: String, isoDayOfWeek: Int) {
        val docRef = rulesRef(familyId).document(packageName)
        val existing = docRef.get().await().toObject(AppRule::class.java) ?: return
        docRef.set(existing.copy(weeklyLimitMinutes = existing.weeklyLimitMinutes?.minus(isoDayOfWeek.toString()))).await()
    }

    /** Adds to today's existing bonus rather than overwriting it, so approving two rewards the same day stacks. */
    suspend fun grantBonusMinutes(familyId: String, packageName: String, minutes: Int, dateKey: String) {
        val docRef = rulesRef(familyId).document(packageName)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val existingBonusDateKey = snapshot.getString("bonusDateKey")
            val existingBonus = snapshot.getLong("bonusMinutesToday")?.toInt() ?: 0
            val newBonus = if (existingBonusDateKey == dateKey) existingBonus + minutes else minutes
            transaction.update(
                docRef,
                mapOf(
                    "bonusMinutesToday" to newBonus,
                    "bonusDateKey" to dateKey
                )
            )
        }.await()
    }
}
