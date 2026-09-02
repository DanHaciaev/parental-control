package com.teo.parent.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.teo.parent.MainActivity
import com.teo.parent.R
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Stand-in for push notifications while the free tier has no server to send them from:
 * polled every ~15 min, only while the app isn't already open (which gets events live via Firestore listeners).
 */
@HiltWorker
class EventPollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid ?: return Result.success()
        val familyId = familyRepository.findFamilyIdForParent(uid) ?: return Result.success()

        return try {
            val unreadCount = eventRepository.getUnreadEventCount(familyId)
            if (unreadCount > 0) showNotification(unreadCount)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(count: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Уведомления", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Родительский контроль")
            .setContentText(if (count == 1) "1 новое событие" else "$count новых событий")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "events"
        private const val NOTIFICATION_ID = 2001
    }
}
