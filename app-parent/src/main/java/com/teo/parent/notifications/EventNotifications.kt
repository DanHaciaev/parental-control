package com.teo.parent.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.teo.parent.MainActivity
import com.teo.parent.R

/**
 * Shared by both the ~15-min WorkManager fallback poll and ParentAlertService's live listener —
 * a Telegram-style heads-up banner is enough here (unlike SOS, which genuinely warrants a
 * full-screen takeover): IMPORTANCE_HIGH already makes it slide down over whatever the parent is
 * doing and be tappable, without hijacking the whole screen for a routine event.
 */
object EventNotifications {
    private const val CHANNEL_ID = "events_v2"
    private const val NOTIFICATION_ID = 2001
    private const val MAX_INBOX_LINES = 5
    const val EXTRA_OPEN_INSTALL_APPROVALS = "open_install_approvals"

    /** [messages] are each event's own human-readable text (e.g. "Ребёнок отключил геолокацию",
     *  "Ребёнок пытается установить: WhatsApp") — already descriptive per EventLogEntry.message at
     *  every call site, so there's no reason to collapse them into a generic "N новых событий" the
     *  parent has to tap through to actually read. */
    fun show(context: Context, messages: List<String>, deepLinkInstallApprovals: Boolean) {
        if (messages.isEmpty()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_HIGH is what actually gets a heads-up popup + sound — confirmed live that
            // IMPORTANCE_DEFAULT just sat silently in the shade with no heads-up at all. A channel's
            // importance is fixed by the OS the moment it's first created and createNotificationChannel
            // silently no-ops on every call after that (even with a different importance passed in) —
            // the channel ID itself had to change to actually take effect on phones where the old
            // "events" channel (IMPORTANCE_DEFAULT) already existed from before this fix.
            val channel = NotificationChannel(CHANNEL_ID, "Уведомления", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            if (deepLinkInstallApprovals) putExtra(EXTRA_OPEN_INSTALL_APPROVALS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, if (deepLinkInstallApprovals) 1 else 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Nest Parent")
            .setContentText(messages.first())
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        // A collapsed single line can't show more than one event's text — InboxStyle expands to a
        // readable list on long-press/pull-down instead of forcing the parent to open the app just
        // to see what the other events were.
        if (messages.size > 1) {
            builder.setStyle(
                NotificationCompat.InboxStyle().also { style ->
                    messages.take(MAX_INBOX_LINES).forEach { style.addLine(it) }
                    style.setSummaryText("${messages.size} новых событий")
                }
            )
            builder.setContentText("${messages.size} новых событий")
        }

        manager.notify(NOTIFICATION_ID, builder.build())
    }
}
