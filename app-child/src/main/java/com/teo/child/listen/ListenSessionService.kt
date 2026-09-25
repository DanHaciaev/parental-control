package com.teo.child.listen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.teo.child.R
import com.teo.core.model.IceCandidateInfo
import com.teo.core.model.ListenSessionStatus
import com.teo.core.repository.ListenSessionRepository
import com.teo.core.webrtc.WebRtcConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import javax.inject.Inject

/**
 * Owns the WebRTC "answerer" side of a listen session for as long as it's ACTIVE, and the one
 * on-screen, non-dismissible notification that makes it impossible for the listening to happen
 * invisibly. Deliberately a separate foreground service from [com.teo.child.monitor.MonitorForegroundService]
 * — same single-purpose-component split as RingController/OverlayBlockerController — so its
 * `microphone` foreground-service type only ever applies while a session is genuinely live.
 */
@AndroidEntryPoint
class ListenSessionService : Service() {

    @Inject lateinit var listenSessionRepository: ListenSessionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var peerConnection: PeerConnection? = null
    @Volatile private var localAudioTrack: AudioTrack? = null
    private var maxDurationJob: Job? = null
    @Volatile private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val familyId = intent?.getStringExtra(EXTRA_FAMILY_ID)
        val sessionToken = intent?.getStringExtra(EXTRA_SESSION_TOKEN)
        val offerSdp = intent?.getStringExtra(EXTRA_OFFER_SDP)
        if (familyId == null || sessionToken == null || offerSdp == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(familyId))
        startAnswering(familyId, sessionToken, offerSdp)
        observeRemoteEnd(familyId, sessionToken)
        scheduleMaxDurationCutoff(familyId)
        return START_NOT_STICKY
    }

    private fun startAnswering(familyId: String, sessionToken: String, offerSdp: String) {
        val factory = WebRtcConfig.factory(this)
        val pc = factory.createPeerConnection(WebRtcConfig.rtcConfig(), object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                serviceScope.launch {
                    runCatching {
                        listenSessionRepository.addCalleeIceCandidate(
                            familyId,
                            IceCandidateInfo(sessionToken, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                        )
                    }
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.FAILED) endAndStop(familyId)
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit
        })
        if (pc == null) {
            endAndStop(familyId)
            return
        }
        peerConnection = pc

        val track = WebRtcConfig.createLocalAudioTrack(this)
        localAudioTrack = track
        pc.addTrack(track, listOf(LOCAL_STREAM_ID))

        pc.setRemoteDescription(
            sdpObserver(onSetSuccess = {
                pc.createAnswer(
                    sdpObserver(onCreateSuccess = { answer ->
                        pc.setLocalDescription(
                            sdpObserver(onSetSuccess = {
                                serviceScope.launch {
                                    runCatching {
                                        listenSessionRepository.respondToSession(
                                            familyId, accepted = true, answerSdp = answer.description
                                        )
                                    }.onFailure { endAndStop(familyId) }
                                }
                            }, onFailure = { endAndStop(familyId) }),
                            answer
                        )
                    }, onFailure = { endAndStop(familyId) }),
                    MediaConstraints()
                )
            }, onFailure = { endAndStop(familyId) }),
            SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        )

        observeCallerIceCandidates(familyId, sessionToken, pc)
    }

    private fun observeCallerIceCandidates(familyId: String, sessionToken: String, pc: PeerConnection) {
        serviceScope.launch {
            val seen = mutableSetOf<String>()
            runCatching {
                listenSessionRepository.observeCallerIceCandidates(familyId, sessionToken).collect { candidates ->
                    candidates.forEach { info ->
                        if (seen.add("${info.sdpMid}:${info.sdpMLineIndex}:${info.candidate}")) {
                            pc.addIceCandidate(IceCandidate(info.sdpMid, info.sdpMLineIndex, info.candidate))
                        }
                    }
                }
            }
        }
    }

    /** The parent can also end the call from their side (writes status ENDED) — this tears the
     *  service down to match, not just a locally-initiated stop. */
    private fun observeRemoteEnd(familyId: String, sessionToken: String) {
        serviceScope.launch {
            runCatching {
                listenSessionRepository.observeSession(familyId).collect { session ->
                    if (session == null || session.sessionToken != sessionToken) return@collect
                    if (session.status == ListenSessionStatus.ENDED || session.status == ListenSessionStatus.DECLINED) {
                        stopCleanly()
                    }
                }
            }
        }
    }

    private fun scheduleMaxDurationCutoff(familyId: String) {
        maxDurationJob?.cancel()
        maxDurationJob = serviceScope.launch {
            delay(MAX_SESSION_DURATION_MS)
            endAndStop(familyId)
        }
    }

    /** Waits for the ENDED write to actually leave the device before tearing down [serviceScope] —
     *  cancelling the scope right after launching the write (as a fire-and-forget) raced the write
     *  itself, so it very often never reached Firestore and left the session doc stuck ACTIVE. */
    private fun endAndStop(familyId: String) {
        serviceScope.launch {
            runCatching { listenSessionRepository.endSession(familyId) }
            stopCleanly()
        }
    }

    private fun stopCleanly() {
        if (stopping) return
        stopping = true
        peerConnection?.close()
        peerConnection = null
        localAudioTrack = null
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopCleanly()
        super.onDestroy()
    }

    private fun buildNotification(familyId: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Прослушивание вокруг", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Мама сейчас слушает вокруг")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
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

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "listen_session"
        private const val LOCAL_STREAM_ID = "teo-listen-stream"
        private const val MAX_SESSION_DURATION_MS = 5 * 60 * 1000L
        private const val EXTRA_FAMILY_ID = "familyId"
        private const val EXTRA_SESSION_TOKEN = "sessionToken"
        private const val EXTRA_OFFER_SDP = "offerSdp"

        fun startAnswering(context: Context, familyId: String, sessionToken: String, offerSdp: String) {
            val intent = Intent(context, ListenSessionService::class.java).apply {
                putExtra(EXTRA_FAMILY_ID, familyId)
                putExtra(EXTRA_SESSION_TOKEN, sessionToken)
                putExtra(EXTRA_OFFER_SDP, offerSdp)
            }
            context.startForegroundService(intent)
        }
    }
}
