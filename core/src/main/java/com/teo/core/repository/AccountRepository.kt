package com.teo.core.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Удалить аккаунт" (Google Play requires an in-app way to delete an account and its data).
 * There's no server, so the parent's own client does it, within what firestore.rules allow a parent:
 *
 * - a child also managed by another parent stays — this parent is only removed from it;
 * - a child managed by this parent alone is deleted with all of its data, subcollections first
 *   (the rules check parenthood against the family document, so it has to go last);
 * - then the Firebase Auth account itself.
 *
 * The child's phone keeps its cached protection code, so the parent can still remove Nest Kid
 * there with their code afterwards. Short-lived pairing/invite codes are left to expire.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val familyRepository: FamilyRepository
) {
    /** Throws FirebaseAuthInvalidCredentialsException on a wrong password, before anything is deleted. */
    suspend fun deleteAccount(password: String) {
        val user = auth.currentUser ?: error("not signed in")
        val email = user.email ?: error("account has no email")
        // Deleting the Auth account needs a recent sign-in anyway — doing it first also means a
        // wrong password stops everything before any data is touched.
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()

        val uid = user.uid
        familyRepository.familiesForParent(uid).forEach { family ->
            val others = family.allParentUids() - uid
            val familyRef = firestore.collection(FirestorePaths.FAMILIES).document(family.id)
            if (others.isNotEmpty()) {
                val update = mutableMapOf<String, Any>("parentUids" to FieldValue.arrayRemove(uid))
                if (family.parentUid == uid) update["parentUid"] = others.first()
                familyRef.update(update).await()
            } else {
                FAMILY_SUBCOLLECTIONS.forEach { name -> deleteAll(familyRef.collection(name)) }
                val listenSession = familyRef.collection(FirestorePaths.LISTEN_SESSION)
                    .document(FirestorePaths.LISTEN_SESSION_CURRENT_DOC)
                deleteAll(listenSession.collection(FirestorePaths.CALLER_ICE_CANDIDATES))
                deleteAll(listenSession.collection(FirestorePaths.CALLEE_ICE_CANDIDATES))
                deleteAll(familyRef.collection(FirestorePaths.LISTEN_SESSION))
                familyRef.delete().await()
            }
        }
        user.delete().await()
    }

    /** Page by page, in batches — Firestore caps a batch at 500 writes. */
    private suspend fun deleteAll(collection: CollectionReference) {
        while (true) {
            val page = collection.limit(BATCH_SIZE).get().await()
            if (page.isEmpty) return
            val batch = firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            if (page.size() < BATCH_SIZE) return
        }
    }

    private companion object {
        const val BATCH_SIZE = 400L

        val FAMILY_SUBCOLLECTIONS = listOf(
            FirestorePaths.RULES,
            FirestorePaths.USAGE_DAILY,
            FirestorePaths.USAGE_HOURLY,
            FirestorePaths.DEVICE_STATUS,
            FirestorePaths.LOCATION,
            FirestorePaths.GEOFENCES,
            FirestorePaths.INSTALLED_APPS,
            FirestorePaths.EVENTS,
            FirestorePaths.REQUESTS,
            FirestorePaths.TASKS,
            FirestorePaths.ASSIGNED_SKILLS,
            FirestorePaths.SKILL_PROGRESS,
            FirestorePaths.SCHEDULES,
            FirestorePaths.HISTORY,
            FirestorePaths.DEVICES
        )
    }
}
