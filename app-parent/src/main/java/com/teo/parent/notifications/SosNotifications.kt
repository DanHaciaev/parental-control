package com.teo.parent.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.teo.parent.MainActivity
import com.teo.parent.R

/**
 * Separate high-priority channel from the generic "events" one, so the heads-up banner cuts through
 * even when the parent has notifications muted. Deliberately silent (vibration only) — the actual
 * incoming call from the child (see SosTrigger) is the audio signal; an app-played siren on top of
 * that used to fight the phone call for audio focus and made the call itself unreliable to answer.
 */
object SosNotifications {
    // "_v3" because notification channel sound/importance is locked once created on-device;
    // changing these fields in code requires a new channel id to actually take effect for existing installs.
    private const val CHANNEL_ID = "sos_v3"
    private const val NOTIFICATION_ID = 4001

    fun show(context: Context) {
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🆘 SOS от ребёнка")
            .setContentText("Ребёнок нажал кнопку SOS — сейчас поступит звонок")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            // Launches MainActivity straight over the lock screen / whatever the parent is doing,
            // instead of just sitting in the shade — MainActivity's own showWhenLocked/turnScreenOn
            // (manifest) plus its existing app-level PIN lock keep this safe to show unconditionally.
            .setFullScreenIntent(pendingIntent, true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "SOS от ребёнка", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Срочное уведомление, когда ребёнок нажимает кнопку SOS (следом идёт настоящий звонок)"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Full-screen SOS needs USE_FULL_SCREEN_INTENT, which from Android 14 is only pre-granted to
     * calling and alarm apps — everything else asks the user in system settings. Without it the
     * SOS still arrives as a high-priority heads-up notification, just not over the lock screen.
     */
    fun canShowFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()

    fun openFullScreenSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private const val PREFS = "sos_prompt"
    private const val KEY_ASKED = "full_screen_asked"

    /** Ask once; after that it's reachable from "Код защиты и вход". */
    fun shouldAskForFullScreen(context: Context): Boolean =
        !canShowFullScreen(context) &&
            !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markFullScreenAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }
}
