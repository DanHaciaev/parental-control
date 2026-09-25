package com.teo.parent.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.EventType
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import com.teo.parent.MainActivity
import com.teo.parent.R
import com.teo.parent.notifications.EventNotifications
import com.teo.parent.notifications.SosNotifications
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps a live Firestore listener on the family's events running for as long as the parent's phone
 * is on, so a new event shows up as a heads-up notification within seconds instead of waiting for
 * EventPollWorker's ~15-minute floor — the whole point of "the child just tapped Install and I want
 * to know right now," not fifteen minutes later. EventPollWorker stays in place as a fallback for
 * whenever this got killed (Android can and does kill any non-exempt background process eventually).
 */
@AndroidEntryPoint
class ParentAlertService : Service() {

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var familyRepository: FamilyRepository
    @Inject lateinit var eventRepository: EventRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var knownUnreadIds: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildOwnNotification())
        serviceScope.launch {
            val uid = auth.currentUser?.uid ?: run { stopSelf(); return@launch }
            val familyId = familyRepository.findFamilyIdForParent(uid) ?: run { stopSelf(); return@launch }
            while (true) {
                try {
                    eventRepository.observeRecentEvents(familyId).collect { events ->
                        val unread = events.filter { !it.read }
                        val newOnes = unread.filter { it.id !in knownUnreadIds }
                        knownUnreadIds = unread.map { it.id }.toSet()
                        if (newOnes.isEmpty()) return@collect

                        val sos = newOnes.filter { it.type == EventType.SOS }
                        if (sos.isNotEmpty()) {
                            SosNotifications.show(applicationContext)
                        }
                        val other = newOnes.filterNot { it.type == EventType.SOS }
                        if (other.isNotEmpty()) {
                            val deepLinkInstalls = other.any {
                                it.type == EventType.INSTALL_ATTEMPT || it.type == EventType.NEW_INSTALL
                            }
                            EventNotifications.show(applicationContext, other.map { it.message }, deepLinkInstalls)
                        }
                        newOnes.forEach { event -> runCatching { eventRepository.markEventRead(familyId, event.id) } }
                    }
                } catch (e: Exception) {
                    delay(5_000)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildOwnNotification(): android.app.Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Nest Parent", NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, openAppIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nest Parent следит за уведомлениями")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 5001
        private const val CHANNEL_ID = "parent_alert_service"

        fun start(context: Context) {
            val intent = Intent(context, ParentAlertService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
