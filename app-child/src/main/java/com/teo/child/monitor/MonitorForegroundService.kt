package com.teo.child.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.teo.child.MainActivity
import com.teo.child.R
import com.teo.child.admin.DeviceAdminHelper
import com.teo.child.data.ChildPreferences
import com.teo.child.data.local.BlockDecision
import com.teo.child.data.local.LocalUsageStore
import com.teo.core.model.CommandType
import com.teo.core.model.EventLogEntry
import com.teo.core.model.EventType
import com.teo.core.repository.CommandRepository
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import javax.inject.Inject

@AndroidEntryPoint
class MonitorForegroundService : Service() {

    @Inject lateinit var localUsageStore: LocalUsageStore
    @Inject lateinit var ruleSyncListener: RuleSyncListener
    @Inject lateinit var childPreferences: ChildPreferences
    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var commandRepository: CommandRepository
    @Inject lateinit var familyRepository: FamilyRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var overlayController: OverlayBlockerController
    private var blockedPackage: String? = null
    private var packageAddedReceiver: BroadcastReceiver? = null
    private var currentFamilyId: String? = null
    @Volatile private var bedtimeWindow: Pair<Int, Int>? = null
    private var lowBatteryNotified = false

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        overlayController = OverlayBlockerController(this)
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            val familyId = childPreferences.familyId.first() ?: run { stopSelf(); return@launch }
            currentFamilyId = familyId
            ruleSyncListener.start(familyId, serviceScope)
            syncInstalledApps(familyId)
            registerPackageAddedReceiver(familyId)
            observeCommands(familyId)
            observeBedtime(familyId)
            pollLoop()
        }
    }

    /** Parent's remote "release protection" command — self-relinquishes admin with no dialog at all. */
    private fun observeCommands(familyId: String) {
        serviceScope.launch {
            while (true) {
                try {
                    commandRepository.observePendingCommand(familyId).collect { command ->
                        if (command?.type == CommandType.RELEASE_PROTECTION) {
                            DeviceAdminHelper.relinquish(this@MonitorForegroundService)
                            commandRepository.acknowledgeCommand(familyId)
                        }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    private fun observeBedtime(familyId: String) {
        serviceScope.launch {
            while (true) {
                try {
                    familyRepository.observeFamily(familyId).collect { family ->
                        val start = family?.bedtimeStartMinutes
                        val end = family?.bedtimeEndMinutes
                        bedtimeWindow = if (start != null && end != null) start to end else null
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        packageAddedReceiver?.let { runCatching { unregisterReceiver(it) } }
        overlayController.hide()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun pollLoop() {
        while (true) {
            runCatching { tick() }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun tick() {
        val timezone = TimeZone.getDefault().id
        checkBattery()

        val foregroundPackage = queryForegroundApp() ?: return
        if (foregroundPackage == packageName) return

        if (isBedtimeNow(timezone) && !isEssentialDuringBedtime(foregroundPackage)) {
            blockedPackage = foregroundPackage
            goHome()
            overlayController.show(
                reason = "Сейчас ночной режим — телефон недоступен",
                allowRequestMore = false,
                onGoHome = ::goHome,
                onRequestMore = {}
            )
            return
        }

        localUsageStore.recordForegroundTick(foregroundPackage, POLL_INTERVAL_SECONDS, timezone)

        when (val decision = localUsageStore.evaluate(foregroundPackage, timezone)) {
            is BlockDecision.Blocked -> {
                blockedPackage = foregroundPackage
                goHome()
                overlayController.show(
                    reason = decision.reason,
                    allowRequestMore = decision.allowRequestMore,
                    onGoHome = ::goHome,
                    onRequestMore = { requestMoreTime(decision.packageName ?: foregroundPackage) }
                )
            }
            BlockDecision.Allowed -> {
                if (overlayController.isShowing() && foregroundPackage != blockedPackage) {
                    overlayController.hide()
                    blockedPackage = null
                }
            }
        }
    }

    /** Phone calls and the camera stay usable through bedtime — only general app browsing is blocked. */
    private fun isEssentialDuringBedtime(pkg: String): Boolean {
        if (pkg in ESSENTIAL_SYSTEM_PACKAGES) return true
        if (pkg == defaultDialerPackage()) return true
        if (pkg == defaultCameraPackage()) return true
        return false
    }

    private fun defaultDialerPackage(): String? = runCatching {
        (getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)?.defaultDialerPackage
    }.getOrNull()

    private fun defaultCameraPackage(): String? = runCatching {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    private fun isBedtimeNow(timezone: String): Boolean {
        val (start, end) = bedtimeWindow ?: return false
        val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
        val now = LocalTime.now(zone)
        val nowMinutes = now.hour * 60 + now.minute
        return if (start <= end) nowMinutes in start until end else nowMinutes >= start || nowMinutes < end
    }

    private fun requestMoreTime(targetPackage: String) {
        val familyId = currentFamilyId ?: return
        serviceScope.launch { runCatching { eventRepository.createMoreTimeRequest(familyId, targetPackage) } }
    }

    private fun checkBattery() {
        val level = runCatching {
            (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(-1)
        if (level < 0) return

        if (level <= LOW_BATTERY_THRESHOLD && !lowBatteryNotified) {
            lowBatteryNotified = true
            val familyId = currentFamilyId ?: return
            serviceScope.launch {
                runCatching {
                    eventRepository.logEvent(
                        familyId,
                        EventLogEntry(type = EventType.LOW_BATTERY, message = "Батарея телефона разряжена: $level%")
                    )
                }
            }
        } else if (level >= LOW_BATTERY_RESET_THRESHOLD) {
            lowBatteryNotified = false
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
    }

    private fun queryForegroundApp(): String? {
        val end = System.currentTimeMillis()
        val begin = end - QUERY_WINDOW_MS
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    private suspend fun syncInstalledApps(familyId: String) {
        val pm = packageManager
        val known = runCatching { eventRepository.getKnownPackageNames(familyId) }.getOrDefault(emptySet())
        val launchable = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName && pm.getLaunchIntentForPackage(it.packageName) != null }

        launchable.filter { it.packageName !in known }.forEach { appInfo ->
            recordInstalledApp(familyId, appInfo)
        }
    }

    private suspend fun recordInstalledApp(familyId: String, appInfo: ApplicationInfo) {
        val pm = packageManager
        val label = pm.getApplicationLabel(appInfo).toString()
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        runCatching { eventRepository.recordNewInstall(familyId, appInfo.packageName, label, isSystem) }
    }

    private fun registerPackageAddedReceiver(familyId: String) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val addedPackage = intent.data?.schemeSpecificPart ?: return
                serviceScope.launch {
                    val pm = packageManager
                    val appInfo = runCatching { pm.getApplicationInfo(addedPackage, 0) }.getOrNull() ?: return@launch
                    if (pm.getLaunchIntentForPackage(addedPackage) == null) return@launch
                    recordInstalledApp(familyId, appInfo)
                }
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") })
        packageAddedReceiver = receiver
    }

    private fun buildNotification(): android.app.Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Родительский контроль", NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, openAppIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Родительский контроль активен")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "monitor_service"
        private const val POLL_INTERVAL_SECONDS = 3
        private const val POLL_INTERVAL_MS = POLL_INTERVAL_SECONDS * 1000L
        private const val QUERY_WINDOW_MS = 10_000L
        private const val LOW_BATTERY_THRESHOLD = 15
        private const val LOW_BATTERY_RESET_THRESHOLD = 20
        private val ESSENTIAL_SYSTEM_PACKAGES = setOf(
            "com.android.server.telecom",
            "com.android.phone",
            "com.android.incallui",
            "com.samsung.android.incallui"
        )

        fun start(context: Context) {
            val intent = Intent(context, MonitorForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
