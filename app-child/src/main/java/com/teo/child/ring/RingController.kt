package com.teo.child.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Loud "find my phone" alert. Plays on the ALARM stream so it's audible even if the
 * child has set the phone to silent/vibrate — no special permission required.
 */
object RingController {
    private var mediaPlayer: MediaPlayer? = null
    private var stopJob: Job? = null

    fun start(context: Context, scope: CoroutineScope, durationMs: Long = 45_000L) {
        stop(context)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        mediaPlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.getOrNull()

        stopJob = scope.launch {
            delay(durationMs)
            stop(context)
        }
    }

    fun stop(context: Context) {
        stopJob?.cancel()
        stopJob = null
        mediaPlayer?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        mediaPlayer = null
    }
}
