package com.teo.core.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.teo.core.FirestorePaths
import com.teo.core.model.IceCandidateInfo
import com.teo.core.model.ListenSession
import com.teo.core.model.ListenSessionStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parent <-> child "listen to surroundings" signaling. Firestore can't carry the audio itself
 * (see WebRtcConfig) — this repository only ever carries the WebRTC SDP offer/answer and ICE
 * candidates, plus the session status, over a singleton doc per family (mirrors
 * deviceStatus/current, location/current).
 */
@Singleton
class ListenSessionRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun sessionRef(familyId: String) =
        firestore.collection(FirestorePaths.FAMILIES).document(familyId)
            .collection(FirestorePaths.LISTEN_SESSION).document(FirestorePaths.LISTEN_SESSION_CURRENT_DOC)

    private fun callerCandidatesRef(familyId: String) =
        sessionRef(familyId).collection(FirestorePaths.CALLER_ICE_CANDIDATES)

    private fun calleeCandidatesRef(familyId: String) =
        sessionRef(familyId).collection(FirestorePaths.CALLEE_ICE_CANDIDATES)

    /** Parent starts a brand-new session, overwriting any previous (already-ended) one. Returns
     *  the fresh [ListenSession.sessionToken] so the caller can scope its own ICE writes/reads. */
    suspend fun createSession(familyId: String, offerSdp: String): String {
        val token = UUID.randomUUID().toString()
        sessionRef(familyId).set(
            ListenSession(sessionToken = token, status = ListenSessionStatus.PENDING_CONSENT, offerSdp = offerSdp)
        ).await()
        return token
    }

    fun observeSession(familyId: String): Flow<ListenSession?> = callbackFlow {
        val registration = sessionRef(familyId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(ListenSession::class.java))
        }
        awaitClose { registration.remove() }
    }

    /** Child device's automatic answer — accept starts the call, decline (no RECORD_AUDIO
     *  permission) ends it without ever touching the microphone. */
    suspend fun respondToSession(familyId: String, accepted: Boolean, answerSdp: String? = null) {
        val updates = mutableMapOf<String, Any?>(
            "status" to (if (accepted) ListenSessionStatus.ACTIVE else ListenSessionStatus.DECLINED).name,
            "respondedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        if (accepted) updates["answerSdp"] = answerSdp
        sessionRef(familyId).update(updates).await()
    }

    suspend fun endSession(familyId: String) {
        sessionRef(familyId).update(
            "status", ListenSessionStatus.ENDED.name,
            "endedAt", com.google.firebase.firestore.FieldValue.serverTimestamp()
        ).await()
    }

    suspend fun addCallerIceCandidate(familyId: String, candidate: IceCandidateInfo) {
        callerCandidatesRef(familyId).add(candidate).await()
    }

    suspend fun addCalleeIceCandidate(familyId: String, candidate: IceCandidateInfo) {
        calleeCandidatesRef(familyId).add(candidate).await()
    }

    /** Parent listens to the child's candidates, filtered to the current [sessionToken] so a
     *  leftover listener from a previous session can't feed stale candidates into a new one. */
    fun observeCalleeIceCandidates(familyId: String, sessionToken: String): Flow<List<IceCandidateInfo>> =
        observeIceCandidates(calleeCandidatesRef(familyId), sessionToken)

    /** Child listens to the parent's candidates. */
    fun observeCallerIceCandidates(familyId: String, sessionToken: String): Flow<List<IceCandidateInfo>> =
        observeIceCandidates(callerCandidatesRef(familyId), sessionToken)

    private fun observeIceCandidates(
        collection: com.google.firebase.firestore.CollectionReference,
        sessionToken: String
    ): Flow<List<IceCandidateInfo>> = callbackFlow {
        val registration = collection
            .whereEqualTo("sessionToken", sessionToken)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(IceCandidateInfo::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }
}
