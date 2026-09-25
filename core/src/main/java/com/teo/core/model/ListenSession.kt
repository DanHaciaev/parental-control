package com.teo.core.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class ListenSessionStatus {
    /** Parent created the session; waiting for the child device to pick it up and answer. Brief —
     *  the child device answers automatically, with no on-screen prompt. */
    PENDING_CONSENT,
    /** WebRTC audio is flowing — the child device answered automatically as soon as it saw the
     *  PENDING_CONSENT session (see [com.teo.child.listen.ListenConsentActivity]). The only
     *  on-device indicator is the non-dismissible notification from
     *  [com.teo.child.listen.ListenSessionService]. */
    ACTIVE,
    /** The child device couldn't answer — currently only happens when RECORD_AUDIO isn't granted
     *  on that device (see [com.teo.child.listen.ListenConsentActivity]). */
    DECLINED,
    /** Either side ended an ACTIVE session (manually or via the max-duration cutoff). A WebRTC
     *  connection failure (e.g. no P2P path found without a TURN relay) is also surfaced as an
     *  immediate ENDED — whichever side's PeerConnection observer notices it is responsible for
     *  calling [com.teo.core.repository.ListenSessionRepository.endSession] and showing its own
     *  local "не удалось подключиться" message; there's no separate synced FAILED state. */
    ENDED
}

/**
 * Singleton doc at families/{familyId}/listenSession/current (see [com.teo.core.FirestorePaths]).
 * [sessionToken] disambiguates a brand-new session from a previous one when a listener's callback
 * fires with stale data, and scopes the ICE candidate subcollections without needing batch deletes.
 */
data class ListenSession(
    val sessionToken: String = "",
    val status: ListenSessionStatus = ListenSessionStatus.PENDING_CONSENT,
    @ServerTimestamp val requestedAt: Date? = null,
    val respondedAt: Date? = null,
    val endedAt: Date? = null,
    val offerSdp: String? = null,
    val answerSdp: String? = null
)

/** One ICE candidate, mirrored into either the callerCandidates or calleeCandidates subcollection. */
data class IceCandidateInfo(
    val sessionToken: String = "",
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0,
    val candidate: String = ""
)
