package com.teo.parent.listen

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.IceCandidateInfo
import com.teo.core.model.ListenSessionStatus
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.ListenSessionRepository
import com.teo.core.webrtc.WebRtcConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import javax.inject.Inject

data class ListenSessionUiState(
    /** Null until the session doc has actually been created (WebRTC offer built + written). */
    val status: ListenSessionStatus? = null,
    val elapsedSeconds: Int = 0,
    val errorMessage: String? = null
)

/**
 * Parent side ("caller") of the WebRTC audio call — receive-only, no local mic capture. Signaling
 * rides [ListenSessionRepository]; see its kdoc and [WebRtcConfig] for why there's no TURN relay
 * and why a connection failure is a real end state rather than a silent hang.
 *
 * The "Ещё" overlay screens (see DashboardScreen) aren't Navigation destinations, so
 * [androidx.hilt.navigation.compose.hiltViewModel] resolves to the Activity-scoped ViewModelStore
 * — this instance survives being unmounted and remounted across separate "Послушать вокруг" taps,
 * it's never recreated. [start] therefore has to fully reset its own state (and cancel whatever
 * the *previous* call to it was still doing) rather than run once and stick — a `run only once`
 * guard here previously meant a second tap just replayed the first call's leftover ENDED state.
 */
@HiltViewModel
class ListenSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val listenSessionRepository: ListenSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListenSessionUiState())
    val uiState: StateFlow<ListenSessionUiState> = _uiState.asStateFlow()

    @Volatile private var familyId: String? = null
    @Volatile private var sessionToken: String? = null
    @Volatile private var peerConnection: PeerConnection? = null
    private var remoteDescriptionSet = false
    private var timerJob: Job? = null
    private var previousMusicVolume: Int? = null

    /** Everything belonging to one "Послушать вокруг" attempt — cancelling this alone tears down
     *  every coroutine that attempt started (offer creation, Firestore listeners, ICE exchange),
     *  so a fresh [start] can't cross-talk with a still-running previous one. */
    private var sessionScope: CoroutineScope? = null

    /** The in-flight ENDED write from a just-closed previous attempt, if any. [start] joins this
     *  before creating a new session — otherwise a slow "Завершить" write can land on the server
     *  *after* the brand-new session's PENDING_CONSENT/ACTIVE write and stomp it back to ENDED,
     *  which is exactly what a quick end-then-restart used to trigger. */
    private var pendingEndJob: Job? = null

    /** ICE candidates can start arriving the instant setLocalDescription succeeds, before the
     *  session doc (and therefore sessionToken) exists yet to address them to — buffered here and
     *  flushed once the doc write returns. */
    private val pendingLocalCandidates = mutableListOf<IceCandidate>()

    fun start() {
        teardown()
        _uiState.value = ListenSessionUiState()
        sessionToken = null
        remoteDescriptionSet = false
        synchronized(pendingLocalCandidates) { pendingLocalCandidates.clear() }

        val scope = CoroutineScope(viewModelScope.coroutineContext + Job())
        sessionScope = scope
        scope.launch {
            pendingEndJob?.join()
            pendingEndJob = null

            val uid = auth.currentUser?.uid ?: run {
                _uiState.update { it.copy(errorMessage = "Не удалось определить семью") }
                return@launch
            }
            val fid = runCatching { familyRepository.findFamilyIdForParent(uid) }.getOrNull() ?: run {
                _uiState.update { it.copy(errorMessage = "Не удалось определить семью") }
                return@launch
            }
            familyId = fid
            // Force-clear whatever the session doc currently holds before creating a new one — the
            // parent is always allowed to write ENDED regardless of the doc's current status (see
            // firestore.rules), so this can't get stuck the way relying on the *previous* attempt's
            // own ENDED write could: a leftover ACTIVE/PENDING_CONSENT doc from a crashed app, a
            // child-side service that never got its own ENDED write out, or any other stale state
            // otherwise blocks every future create with PERMISSION_DENIED until someone manually
            // clears it. NOT_FOUND (no doc yet) is expected and fine.
            runCatching { listenSessionRepository.endSession(fid) }
            createOfferAndSession(fid, scope)
            // observeSession is started from persistSession, once our own sessionToken actually
            // exists — starting it here in parallel with offer/session creation used to mean its
            // very first snapshot could be the ENDED doc state from the force-clear write above,
            // which read as "the session we're creating already ended" and tore the whole scope
            // down before createSession had even run.
        }
    }

    private fun createOfferAndSession(familyId: String, scope: CoroutineScope) {
        val factory = WebRtcConfig.factory(context)
        val pc = factory.createPeerConnection(WebRtcConfig.rtcConfig(), object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val token = sessionToken
                if (token == null) {
                    synchronized(pendingLocalCandidates) { pendingLocalCandidates.add(candidate) }
                } else {
                    sendCallerCandidate(familyId, token, candidate, scope)
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.FAILED -> {
                        _uiState.update { it.copy(errorMessage = "Не удалось подключиться") }
                        endSession()
                    }
                    PeerConnection.IceConnectionState.CONNECTED -> boostMediaVolume()
                    else -> Unit
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            // Boost the incoming track's own gain in addition to the device-level volume changes
            // in enableSpeakerphone() — on some devices (confirmed on a Huawei handset) the
            // default 1.0 gain plus max stream volume still played back barely audibly.
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                (receiver?.track() as? org.webrtc.AudioTrack)?.setVolume(REMOTE_TRACK_MAX_GAIN)
            }
        })
        if (pc == null) {
            _uiState.update { it.copy(errorMessage = "WebRTC недоступен на этом устройстве") }
            return
        }
        peerConnection = pc
        // Receive-only: the parent never sends audio, just declares it wants the child's track.
        pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )

        pc.createOffer(
            sdpObserver(onCreateSuccess = { offer ->
                pc.setLocalDescription(
                    sdpObserver(
                        onSetSuccess = { scope.launch { persistSession(familyId, offer.description) } },
                        onFailure = { _uiState.update { it.copy(errorMessage = "Не удалось начать сессию") } }
                    ),
                    offer
                )
            }, onFailure = { _uiState.update { it.copy(errorMessage = "Не удалось начать сессию") } }),
            MediaConstraints()
        )
    }

    /** Retries like [com.teo.parent.auth.AuthViewModel.loadFamilyState] does for the same reason:
     *  right after sign-in/app start, Firestore's write stream can briefly reject the very first
     *  write with PERMISSION_DENIED while the auth token is still attaching to that connection —
     *  a transient race, not a real permissions problem. Listen requests are often the first write
     *  of a session (open app, straight to "Ещё" → "Послушать вокруг"), which is exactly when this bites. */
    private suspend fun persistSession(familyId: String, offerSdp: String) {
        var lastError: Throwable? = null
        var token: String? = null
        for (attempt in 0 until CREATE_SESSION_RETRY_ATTEMPTS) {
            val result = runCatching { listenSessionRepository.createSession(familyId, offerSdp) }
            token = result.getOrNull()
            if (token != null) break
            lastError = result.exceptionOrNull()
            if (attempt < CREATE_SESSION_RETRY_ATTEMPTS - 1) delay(500L * (attempt + 1))
        }
        val resolvedToken = token
        if (resolvedToken == null) {
            _uiState.update {
                it.copy(errorMessage = "Не удалось начать сессию: ${lastError?.message}")
            }
            return
        }
        sessionToken = resolvedToken
        val buffered = synchronized(pendingLocalCandidates) {
            pendingLocalCandidates.toList().also { pendingLocalCandidates.clear() }
        }
        val scope = sessionScope ?: return
        buffered.forEach { sendCallerCandidate(familyId, resolvedToken, it, scope) }
        observeSession(familyId, scope)
    }

    private fun sendCallerCandidate(familyId: String, sessionToken: String, candidate: IceCandidate, scope: CoroutineScope) {
        scope.launch {
            runCatching {
                listenSessionRepository.addCallerIceCandidate(
                    familyId,
                    IceCandidateInfo(sessionToken, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                )
            }
        }
    }

    private fun observeSession(familyId: String, scope: CoroutineScope) {
        scope.launch {
            runCatching {
                listenSessionRepository.observeSession(familyId).collect { session ->
                    if (session == null || session.sessionToken != sessionToken) return@collect
                    _uiState.update { it.copy(status = session.status) }
                    when (session.status) {
                        ListenSessionStatus.ACTIVE -> {
                            val pc = peerConnection
                            val answer = session.answerSdp
                            if (pc != null && answer != null && !remoteDescriptionSet) {
                                remoteDescriptionSet = true
                                pc.setRemoteDescription(
                                    sdpObserver(
                                        onSetSuccess = { startTimer(scope) },
                                        onFailure = { _uiState.update { s -> s.copy(errorMessage = "Не удалось подключиться") } }
                                    ),
                                    SessionDescription(SessionDescription.Type.ANSWER, answer)
                                )
                                observeCalleeIceCandidates(familyId, session.sessionToken, pc, scope)
                            }
                        }
                        ListenSessionStatus.ENDED, ListenSessionStatus.DECLINED -> teardown()
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun observeCalleeIceCandidates(familyId: String, sessionToken: String, pc: PeerConnection, scope: CoroutineScope) {
        scope.launch {
            val seen = mutableSetOf<String>()
            runCatching {
                listenSessionRepository.observeCalleeIceCandidates(familyId, sessionToken).collect { candidates ->
                    candidates.forEach { info ->
                        if (seen.add("${info.sdpMid}:${info.sdpMLineIndex}:${info.candidate}")) {
                            pc.addIceCandidate(IceCandidate(info.sdpMid, info.sdpMLineIndex, info.candidate))
                        }
                    }
                }
            }
        }
    }

    private fun startTimer(scope: CoroutineScope) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    fun endSession() {
        val fid = familyId ?: run { teardown(); return }
        pendingEndJob = viewModelScope.launch { runCatching { listenSessionRepository.endSession(fid) } }
        teardown()
    }

    /** Called when the screen leaves composition (button tap or system back). A session that's
     *  already reached a terminal state on its own (declined, or ended some other way) is left
     *  as-is so its real outcome stays on record — only a still-pending or still-live session
     *  needs an explicit ENDED write here, which also stops the child device from answering (or
     *  streaming) if it's still mid-flight. */
    fun onScreenClosed() {
        when (_uiState.value.status) {
            ListenSessionStatus.DECLINED, ListenSessionStatus.ENDED -> teardown()
            else -> endSession()
        }
    }

    /** [WebRtcConfig] tags playback as ordinary media (USAGE_MEDIA), so it already defaults to the
     *  main speaker like any music/podcast app — this just makes sure it's actually audible over
     *  whatever the parent's regular media volume happened to be left at, and remembers that level
     *  to restore once the call ends rather than leaving media volume maxed permanently. */
    private fun boostMediaVolume() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (previousMusicVolume == null) {
            previousMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            0
        )
    }

    private fun restoreMediaVolume() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        previousMusicVolume?.let { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) }
        previousMusicVolume = null
    }

    private fun teardown() {
        sessionScope?.cancel()
        sessionScope = null
        timerJob?.cancel()
        peerConnection?.close()
        peerConnection = null
        restoreMediaVolume()
    }

    override fun onCleared() {
        teardown()
        super.onCleared()
    }

    private fun sdpObserver(
        onCreateSuccess: (SessionDescription) -> Unit = {},
        onSetSuccess: () -> Unit = {},
        onFailure: (String?) -> Unit = {}
    ) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) = onCreateSuccess(sdp)
        override fun onSetSuccess() = onSetSuccess()
        override fun onCreateFailure(error: String?) = onFailure(error)
        override fun onSetFailure(error: String?) = onFailure(error)
    }

    private companion object {
        const val CREATE_SESSION_RETRY_ATTEMPTS = 3
        /** WebRTC's AudioTrack.setVolume range is 0.0-10.0; 1.0 is the unamplified default. */
        const val REMOTE_TRACK_MAX_GAIN = 10.0
    }
}
