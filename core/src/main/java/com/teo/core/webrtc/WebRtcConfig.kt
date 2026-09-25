package com.teo.core.webrtc

import android.content.Context
import android.media.AudioAttributes
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Shared WebRTC setup for the "listen to surroundings" call — audio-only (no camera/video) in
 * both directions, so both app-child (mic capture) and app-parent (playback) can reuse the exact
 * same factory/ICE-server boilerplate instead of duplicating PeerConnectionFactory init per app.
 * Signaling (SDP offer/answer + ICE candidates) rides Firestore, see
 * [com.teo.core.repository.ListenSessionRepository].
 *
 * ICE servers are Google's public STUN plus a free Metered.ca TURN relay (confirmed live: without
 * a TURN relay, parent and child on different networks — e.g. one on Wi-Fi, one on mobile data —
 * very often can't establish direct P2P connectivity at all, since STUN alone can't traverse
 * carrier-grade/symmetric NAT). The TURN username/password below are long-lived static credentials
 * from Metered's dashboard — safe to ship in the client by design (unlike their account *secret*
 * key, which generated these and is never embedded here) — free tier is capped at 500MB/month,
 * which a relayed audio-only call consumes at roughly 25-50 kbit/s. A connection that still can't
 * be established (free-tier quota exhausted, TURN server unreachable, etc.) must be treated by
 * callers as a real ended/failed session, not left hanging silently.
 */
object WebRtcConfig {
    private const val LOCAL_AUDIO_TRACK_ID = "teo-listen-audio"
    private const val TURN_USERNAME = "9d6cf9312e2854e28226f864"
    private const val TURN_CREDENTIAL = "B5bG0ftohCPC6JTc"

    @Volatile private var factory: PeerConnectionFactory? = null

    fun factory(context: Context): PeerConnectionFactory {
        factory?.let { return it }
        synchronized(this) {
            factory?.let { return it }
            val appContext = context.applicationContext
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
            )
            // Confirmed live on a Huawei handset: playing the incoming call-attributed audio
            // track wouldn't route to the main speaker no matter what AudioManager.setSpeakerphoneOn
            // said — some OEM call-routing policies just don't honor it for a non-Telecom app.
            // Tagging playback as ordinary media (like a music/podcast app) instead sidesteps that
            // entirely: every OEM already has to route USAGE_MEDIA to the main speaker by default,
            // since that's what virtually every media app on the device relies on. The child side
            // never actually plays anything back (it only ever sends), so this only affects parent.
            val audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .createAudioDeviceModule()
            return PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()
                .also { factory = it }
        }
    }

    fun rtcConfig(): PeerConnection.RTCConfiguration =
        PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
                PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
                    .setUsername(TURN_USERNAME).setPassword(TURN_CREDENTIAL).createIceServer(),
                PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80?transport=tcp")
                    .setUsername(TURN_USERNAME).setPassword(TURN_CREDENTIAL).createIceServer(),
                PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443")
                    .setUsername(TURN_USERNAME).setPassword(TURN_CREDENTIAL).createIceServer(),
                PeerConnection.IceServer.builder("turns:global.relay.metered.ca:443?transport=tcp")
                    .setUsername(TURN_USERNAME).setPassword(TURN_CREDENTIAL).createIceServer()
            )
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

    /** Child side only — captures the device mic into a track to attach to the answer PeerConnection. */
    fun createLocalAudioTrack(context: Context): AudioTrack {
        val pcFactory = factory(context)
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        val audioSource = pcFactory.createAudioSource(constraints)
        return pcFactory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, audioSource)
    }
}
