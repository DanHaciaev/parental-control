package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.AppRule
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
