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
import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.teo.child.MainActivity
import com.teo.child.R
import com.teo.child.admin.DeviceAdminHelper
import com.teo.child.block.LockScreenBlockActivity
import com.teo.child.block.LockScreenBlockController
import com.teo.child.data.ChildPreferences
import com.teo.child.data.local.BlockDecision
import com.teo.child.data.local.LocalUsageStore
import com.teo.child.data.local.ScheduleCacheDao
import com.teo.child.data.local.ScheduleCacheEntity
import com.teo.child.listen.ListenSessionListener
import com.teo.child.permission.AccessibilityPermissionHelper
import com.teo.child.permission.BatteryOptimizationHelper
import com.teo.child.permission.NotificationPolicyPermissionHelper
import com.teo.child.permission.OverlayPermissionHelper
import com.teo.child.permission.UsageAccessHelper
import com.teo.child.pin.PinChallengeActivity
import com.teo.child.ring.CallStateWatcher
import com.teo.child.ring.RingController
import com.teo.child.ring.RingerModeController
import com.teo.child.sos.ShakeDetector
import com.teo.child.sos.SosTrigger
import com.teo.core.model.CommandType
import com.teo.core.model.DeviceStatus
import com.teo.core.model.EventLogEntry
import com.teo.core.model.EventType
import com.teo.core.model.Family
import com.teo.core.model.InstallApprovalStatus
import com.teo.core.model.LocationPoint
import com.teo.core.model.HourlyUsageEntry
import com.teo.core.model.UsageEntry
import com.teo.core.repository.CommandRepository
import com.teo.core.repository.DeviceStatusRepository
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.HourlyUsageRepository
import com.teo.core.repository.LocationRepository
import com.teo.core.repository.UsageRepository
import com.teo.core.util.DayBoundary
import com.teo.core.util.ScheduleWindow
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import javax.inject.Inject

@AndroidEntryPoint
class MonitorForegroundService : Service() {

    @Inject lateinit var localUsageStore: LocalUsageStore
    @Inject lateinit var ruleSyncListener: RuleSyncListener
    @Inject lateinit var scheduleSyncListener: ScheduleSyncListener
    @Inject lateinit var listenSessionListener: ListenSessionListener
    @Inject lateinit var scheduleCacheDao: ScheduleCacheDao
    @Inject lateinit var childPreferences: ChildPreferences
    @Inject lateinit var eventRepository: EventRepository
    @Inject lateinit var commandRepository: CommandRepository
    @Inject lateinit var familyRepository: FamilyRepository
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var deviceStatusRepository: DeviceStatusRepository
    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var hourlyUsageRepository: HourlyUsageRepository
    @Inject lateinit var sosTrigger: SosTrigger
    @Inject lateinit var stepCounterTracker: StepCounterTracker
    @Inject lateinit var overlayController: OverlayBlockerController
    @Inject lateinit var lockScreenBlockController: LockScreenBlockController
    @Inject lateinit var airplaneModeGuard: AirplaneModeGuard

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var usageStatsManager: UsageStatsManager
    private var packageAddedReceiver: BroadcastReceiver? = null
    private var locationProvidersReceiver: BroadcastReceiver? = null
    private var airplaneModeReceiver: BroadcastReceiver? = null
    private var currentFamilyId: String? = null
    @Volatile private var activeSchedules: List<ScheduleCacheEntity> = emptyList()
    @Volatile private var family: Family? = null
    /** packageName -> approvalStatus, PENDING/DENIED entries only (APPROVED apps are omitted so a
     *  lookup miss just means "not blocked"). */
    @Volatile private var pendingApprovalPackages: Map<String, String> = emptyMap()
    @Volatile private var lastReportedForegroundPackage: String? = null
    @Volatile private var lastReportedForegroundLabel: String? = null
    @Volatile private var lastEventQueryTime: Long = 0L
    @Volatile private var lastAppliedEventTimestamp: Long = 0L
    @Volatile private var trackedForegroundPackage: String? = null
    private var lowBatteryNotified = false
    private var locationDisabledNotified = false
    private var airplaneModeNotified = false
    /** True only while the currently-showing overlay is the airplane-mode block — lets us hide it
     *  the instant airplane mode turns off without also mis-hiding some other overlay that happens
     *  to be showing at that exact moment (e.g. a schedule block starting right as the radio comes
     *  back on). See [hideAirplaneModeOverlayIfShowing]. */
    @Volatile private var airplaneOverlayActive = false
    private var shakeDetector: ShakeDetector? = null
    private var callStateWatcher: CallStateWatcher? = null

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        startForeground(NOTIFICATION_ID, buildNotification())
        registerShakeDetector()
        registerLocationProvidersReceiver()
        registerAirplaneModeReceiver()
        callStateWatcher = CallStateWatcher(
            this,
            onRinging = { RingerModeController.boostForIncomingCall(this) },
            onIdle = { RingerModeController.restoreAfterIncomingCall(this) }
        ).apply { start() }

        serviceScope.launch {
            val familyId = childPreferences.familyId.first() ?: run { stopSelf(); return@launch }
            currentFamilyId = familyId
            stepCounterTracker.start(familyId, TimeZone.getDefault().id)
            ruleSyncListener.start(familyId, serviceScope)
            listenSessionListener.start(familyId, serviceScope)
            syncInstalledApps(familyId)
            registerPackageAddedReceiver(familyId)
            observeCommands(familyId)
            observeFamilySettings(familyId)
            observeInstallApprovals(familyId)
            observeSchedules(familyId)
            locationStatusLoop(familyId)
            usageUploadLoop()
            pollLoop()
        }
    }

    /** Parent's remote commands — ring the device, or toggle ringer mode. */
    private fun observeCommands(familyId: String) {
        serviceScope.launch {
            while (true) {
                try {
                    commandRepository.observePendingCommand(familyId).collect { command ->
                        when (command?.type) {
                            CommandType.RING_DEVICE -> {
                                RingController.start(this@MonitorForegroundService, serviceScope)
                                commandRepository.acknowledgeCommand(familyId)
                            }
                            CommandType.STOP_RING -> {
                                RingController.stop(this@MonitorForegroundService)
                                commandRepository.acknowledgeCommand(familyId)
                            }
                            CommandType.SET_RINGER_NORMAL -> {
                                RingerModeController.setNormal(this@MonitorForegroundService)
                                commandRepository.acknowledgeCommand(familyId)
                            }
                            CommandType.SET_RINGER_SILENT -> {
                                RingerModeController.setSilent(this@MonitorForegroundService)
                                commandRepository.acknowledgeCommand(familyId)
                            }
                            CommandType.ENABLE_LOCATION -> {
                                enableLocationRemotely()
                                commandRepository.acknowledgeCommand(familyId)
                            }
                            // Drains any leftover pending command from before RELEASE_PROTECTION/
                            // DISABLE_AIRPLANE_MODE were removed — no-op otherwise, just clears it
                            // so it stops resurfacing.
                            @Suppress("DEPRECATION")
                            CommandType.RELEASE_PROTECTION, CommandType.DISABLE_AIRPLANE_MODE ->
                                commandRepository.acknowledgeCommand(familyId)
                            else -> Unit
                        }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    /** Primary ~1-minute location + battery/status cadence; the 15-min WorkManager workers remain a fallback. */
    private fun locationStatusLoop(familyId: String) {
        serviceScope.launch {
            while (true) {
                runCatching { uploadLocationAndStatus(familyId) }
                delay(LOCATION_STATUS_INTERVAL_MS)
            }
        }
    }

    /** Primary near-real-time usage sync; the 15-min WorkManager upload workers remain a fallback. */
    private fun usageUploadLoop() {
        serviceScope.launch {
            while (true) {
                runCatching { uploadPendingUsage() }
                delay(USAGE_UPLOAD_INTERVAL_MS)
            }
        }
    }

    private suspend fun uploadPendingUsage() {
        val familyId = currentFamilyId ?: return

        val pendingUsage = localUsageStore.getPendingUpload()
        if (pendingUsage.isNotEmpty()) {
            val entries = pendingUsage.map {
                UsageEntry(dateKey = it.dateKey, packageName = it.packageName, minutesUsedToday = it.minutesUsedToday)
            }
            usageRepository.uploadUsage(familyId, entries)
            localUsageStore.markUploaded(pendingUsage)
        }

        val pendingHourly = localUsageStore.getPendingHourlyUpload()
        if (pendingHourly.isNotEmpty()) {
            val hourlyEntries = pendingHourly.map {
                HourlyUsageEntry(dateKey = it.dateKey, hour = it.hour, minutesUsed = it.minutesUsed)
            }
            hourlyUsageRepository.uploadHourlyUsage(familyId, hourlyEntries)
            localUsageStore.markHourlyUploaded(pendingHourly)
        }
    }

    private suspend fun uploadLocationAndStatus(familyId: String) {
        runCatching {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            deviceStatusRepository.updateStatus(
                familyId,
                DeviceStatus(
                    batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
                    isCharging = batteryManager.isCharging,
                    appVersion = appVersionName(),
                    osVersion = Build.VERSION.RELEASE ?: "?",
                    protectionServiceRunning = true,
                    accessibilityEnabled = AccessibilityPermissionHelper.isEnabled(this),
                    usageAccessEnabled = UsageAccessHelper.hasUsageAccess(this),
                    overlayEnabled = OverlayPermissionHelper.hasOverlayPermission(this),
                    deviceAdminActive = DeviceAdminHelper.isActive(this),
                    batteryOptimizationExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this),
                    notificationPolicyAccess = NotificationPolicyPermissionHelper.isGranted(this),
                    currentForegroundApp = lastReportedForegroundPackage,
                    currentForegroundAppLabel = lastReportedForegroundLabel
                )
            )
        }

        captureAndUploadLocation(familyId)
    }

    private suspend fun captureAndUploadLocation(familyId: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            val client = LocationServices.getFusedLocationProviderClient(this)
            // BALANCED_POWER_ACCURACY leans on Wi-Fi/cell-tower positioning, not GPS — good enough for
            // "which city," not "which room," and a common source of ~50-150m error especially indoors.
            // HIGH_ACCURACY pulls in GPS, which this feature's whole purpose calls for over the battery savings.
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(LOCATION_STATUS_INTERVAL_MS)
                .build()
            val location = client.getCurrentLocation(request, null).await() ?: client.lastLocation.await()
            location?.let {
                locationRepository.updateLocation(
                    familyId,
                    LocationPoint(lat = it.latitude, lng = it.longitude, accuracy = it.accuracy)
                )
            }
        }
    }

    private fun appVersionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull() ?: "?"

    private fun observeFamilySettings(familyId: String) {
        serviceScope.launch {
            while (true) {
                try {
                    familyRepository.observeFamily(familyId).collect { fam ->
                        family = fam
                        if (!fam?.protectionPinHash.isNullOrEmpty() && !fam?.protectionPinSalt.isNullOrEmpty()) {
                            childPreferences.setCachedProtectionPin(fam!!.protectionPinHash, fam.protectionPinSalt)
                        }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    /** Live mirror of which installed apps still need (or were denied) the parent's approval —
     *  same retry-on-error pattern as observeFamilySettings, since a transient listener error
     *  here shouldn't crash the whole foreground service. */
    private fun observeInstallApprovals(familyId: String) {
        serviceScope.launch {
            while (true) {
                try {
                    eventRepository.observeInstalledApps(familyId).collect { apps ->
                        pendingApprovalPackages = apps
                            .filter { it.approvalStatus != InstallApprovalStatus.APPROVED.name }
                            .associate { it.packageName to it.approvalStatus }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    /** Firestore-to-Room sync plus a pure local Room read — the latter never needs network retry. */
    private fun observeSchedules(familyId: String) {
        scheduleSyncListener.start(familyId, serviceScope)
        serviceScope.launch {
            scheduleCacheDao.observeAll().collect { activeSchedules = it }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        packageAddedReceiver?.let { runCatching { unregisterReceiver(it) } }
        locationProvidersReceiver?.let { runCatching { unregisterReceiver(it) } }
        airplaneModeReceiver?.let { runCatching { unregisterReceiver(it) } }
        overlayController.hide()
        shakeDetector?.let {
            (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(it)
        }
        callStateWatcher?.stop()
        stepCounterTracker.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Hardware-independent SOS trigger: a deliberate hard-shake-several-times gesture. Tried volume-button
     * interception via the accessibility service first, but that proved unreliable in practice — Samsung
     * and other OEMs often handle volume keys below where an accessibility service can see them. The
     * accelerometer has no such OEM gatekeeping.
     */
    private fun registerShakeDetector() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val detector = ShakeDetector {
            serviceScope.launch {
                sosTrigger.trigger(
                    this@MonitorForegroundService,
                    message = "Ребёнок сильно потряс телефон — сигнал SOS"
                )
            }
        }
        shakeDetector = detector
        // NORMAL (~200ms) is plenty for a gesture that unfolds over ~1.5s — GAME-rate (~20ms) sampling
        // 24/7 in the background was unnecessary battery/CPU load with no detection benefit.
        sensorManager.registerListener(detector, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private suspend fun pollLoop() {
        while (true) {
            // serviceScope runs on Dispatchers.Default (a background thread pool with no prepared
            // Looper) — overlayController.show()/hide() add/remove a raw WindowManager view, which
            // requires a thread that has one. Without this, every overlayController call from tick()
            // silently threw (swallowed by the runCatching below) and just never showed anything.
            runCatching { withContext(Dispatchers.Main) { tick() } }
                .onFailure { android.util.Log.e("TeoScheduleDebug", "tick() threw", it) }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun tick() {
        val timezone = TimeZone.getDefault().id
        checkBattery()

        // Highest priority, unconditional — airplane mode kills the phone's ability to actually
        // dial out too, so there's no "essential app" list that makes sense here the way there is
        // for other blocks. Broadcast-driven detection (registerAirplaneModeReceiver) is the
        // primary, instant path; this is just the polling fallback in case that broadcast is ever
        // missed.
        if (airplaneModeGuard.shouldBlock(isAirplaneModeOn())) {
            showAirplaneModeOverlay()
            // Same debounced one-shot alert + best-effort location grab as the broadcast receiver
            // — kept here too so a missed ACTION_AIRPLANE_MODE_CHANGED broadcast doesn't silently
            // skip both.
            if (!airplaneModeNotified) {
                airplaneModeNotified = true
                notifyAirplaneModeEnabled()
                captureAndUploadLocationOnAirplaneMode()
            }
            return
        }
        // Polling fallback for the same hide the broadcast receiver already does instantly on
        // ACTION_AIRPLANE_MODE_CHANGED — without this, a missed/delayed broadcast (confirmed live:
        // toggling airplane mode off via the quick-settings tile while idle on the home screen can
        // leave queryForegroundApp() returning null, since the notification shade isn't a tracked
        // foreground app — meant the overlay had no other path left to ever hide itself, staying
        // stuck showing "Ввести код защиты" even though airplane mode was already off).
        hideAirplaneModeOverlayIfShowing()

        // Deliberately checked against the *tracked* package (which can be null — e.g. sitting
        // idle on the home screen, or toggling location from the quick-settings panel without
        // switching apps) rather than after the queryForegroundApp() ?: return below — that early
        // return used to skip this check entirely whenever no foreground app was currently tracked,
        // which is exactly the case a child idling at home with location off would hit, and it
        // would never get blocked at all.
        val trackedPackage = queryForegroundApp()
        if (trackedPackage == packageName) {
            // Our own app is never something to block — but a leftover overlay from blocking a
            // *different* app a moment ago is still a system-wide window sitting on top of everything,
            // including us. Without this, opening our own app right after hitting a limit shows nothing
            // but the block screen, with no way back in.
            if (overlayController.isShowing()) {
                overlayController.hide()
            }
            // LockScreenBlockActivity is itself part of this package, unlike the overlay — it's a
            // genuine focused Activity, so it's *always* the trackedPackage while showing, meaning
            // the location check below never runs for it otherwise. Confirmed live: without this,
            // re-enabling location while that screen was up left it stuck showing indefinitely,
            // since nothing else on this path ever re-evaluates whether it should close.
            if (lockScreenBlockController.isShowing(LockScreenBlockActivity.KIND_LOCATION_DISABLED) && isLocationServicesEnabled()) {
                locationDisabledNotified = false
                lockScreenBlockController.dismiss()
            }
            return
        }

        if (!isEssentialDuringFullBlock(trackedPackage) && trackedPackage != SETTINGS_PACKAGE && !isLocationServicesEnabled()) {
            showLocationDisabledOverlay(trackedPackage)
            return
        }
        locationDisabledNotified = false
        if (lockScreenBlockController.isShowing(LockScreenBlockActivity.KIND_LOCATION_DISABLED)) {
            lockScreenBlockController.dismiss()
        }

        val approvalStatus = pendingApprovalPackages[trackedPackage]
        if (approvalStatus != null && !isEssentialWithHomeExit(trackedPackage)) {
            goHome()
            overlayController.show(
                title = if (approvalStatus == InstallApprovalStatus.DENIED.name) "Приложение запрещено" else "Ждём разрешения",
                reason = if (approvalStatus == InstallApprovalStatus.DENIED.name) {
                    "Родитель не разрешил использовать это приложение."
                } else {
                    "Это приложение только что установлено. Родитель должен разрешить его использование."
                },
                allowRequestMore = false,
                onGoHome = ::goHome,
                onRequestMore = {},
                showHomeButton = false,
                blockedPackage = trackedPackage
            )
            return
        }

        val foregroundPackage = trackedPackage ?: return
        reportForegroundAppIfChanged(foregroundPackage)

        // The accessibility service already instant-blocks a BLOCKED app the moment its window
        // opens (see ProtectionAccessibilityService) — if that already put the overlay up for this
        // exact package, no need to re-evaluate or re-show it here.
        if (overlayController.isShowing() && overlayController.blockedPackage == foregroundPackage) {
            return
        }

        val scheduleBlockReason = evaluateSchedules(foregroundPackage, timezone)
        if (scheduleBlockReason != null) {
            goHome()
            overlayController.show(
                reason = scheduleBlockReason,
                allowRequestMore = false,
                onGoHome = ::goHome,
                onRequestMore = {},
                blockedPackage = foregroundPackage
            )
            return
        }

        localUsageStore.recordForegroundTick(foregroundPackage, POLL_INTERVAL_SECONDS, timezone)
        localUsageStore.recordHourlyTick(POLL_INTERVAL_SECONDS, timezone)

        // A full block is either an active "Блокировать всё" schedule, or the total daily time cap
        // (summed across TIME_LIMIT-tagged apps) running out — either way it's a genuine whole-device
        // lock, not just blocking one app, so it shares the same allow-list exemption.
        val totalCapMinutes = family?.effectiveTotalCapMinutes(DayBoundary.isoDayOfWeek(timezone))
        val fullBlockSchedule = activeFullBlockSchedule(timezone)
        val totalCapExceeded = totalCapMinutes != null &&
            localUsageStore.getTotalTimedMinutesForDay(DayBoundary.todayKey(timezone)) >= totalCapMinutes
        val fullBlockReason: String? = when {
            fullBlockSchedule != null -> "Сейчас «${fullBlockSchedule.name}» — телефон недоступен"
            totalCapExceeded -> "Общее время на сегодня закончилось"
            else -> null
        }

        if (fullBlockReason != null && !isEssentialWithHomeExit(foregroundPackage)) {
            goHome()
            overlayController.show(
                reason = fullBlockReason,
                allowRequestMore = false,
                onGoHome = ::goHome,
                onRequestMore = {},
                blockedPackage = foregroundPackage
            )
            return
        }

        // No PiP handling here anymore — ProtectionAccessibilityService now dismisses any
        // picture-in-picture window the instant it appears (see its updatePipState kdoc), so one
        // never survives long enough to need separate usage tracking or a block decision here.

        when (val decision = localUsageStore.evaluate(foregroundPackage, timezone)) {
            is BlockDecision.Blocked -> {
                goHome()
                overlayController.show(
                    reason = decision.reason,
                    allowRequestMore = decision.allowRequestMore,
                    onGoHome = ::goHome,
                    onRequestMore = { requestMoreTime(decision.packageName ?: foregroundPackage) },
                    blockedPackage = foregroundPackage
                )
            }
            BlockDecision.Allowed -> {
                if (overlayController.isShowing() && foregroundPackage != overlayController.blockedPackage) {
                    overlayController.hide()
                }
            }
        }
    }

    /** Edge-triggered — only writes to Firestore when the foreground app actually changes, not every 3s tick. */
    private suspend fun reportForegroundAppIfChanged(packageName: String) {
        if (packageName == lastReportedForegroundPackage) return
        val familyId = currentFamilyId ?: return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
        lastReportedForegroundPackage = packageName
        lastReportedForegroundLabel = label
        runCatching { deviceStatusRepository.updateForegroundApp(familyId, packageName, label) }
    }

    /** Hard safety-net apps that stay reachable no matter which blocking mechanism is in play —
     *  not parent-configurable, unlike [Family.fullBlockAllowedPackages]. ESSENTIAL_SYSTEM_PACKAGES
     *  (telecom framework, in-call UI) and the default dialer stay reachable so a child can always
     *  actually dial out in an emergency. Deliberately does NOT include the launcher — see
     *  [isEssentialWithHomeExit] for that. */
    private fun isAlwaysReachable(pkg: String?): Boolean {
        if (pkg == null) return false
        if (pkg in ESSENTIAL_SYSTEM_PACKAGES) return true
        return pkg == defaultDialerPackage()
    }

    /** [isAlwaysReachable] plus the launcher — for a specific-app blocklist schedule, whose overlay
     *  offers "На главный экран" as its actual intended exit. Without the launcher exemption,
     *  tapping that button is a trap, not an exit: goHome() switches to the launcher, the very next
     *  poll tick sees it as foreground, decides it's not essential, and blocks it again — bouncing
     *  straight back to the same overlay (confirmed live). Deliberately does NOT also check
     *  [Family.fullBlockAllowedPackages] — that's a different feature (see [isEssentialDuringFullBlock]'s
     *  kdoc for the live bug that mixing the two caused). */
    private fun isAlwaysReachableOrLauncher(pkg: String?): Boolean =
        isAlwaysReachable(pkg) || pkg == defaultLauncherPackage()

    /** [isAlwaysReachable] plus the parent's explicit "stays reachable during a full lockdown"
     *  choice ([Family.fullBlockAllowedPackages] — e.g. Camera, Messages). Deliberately does NOT
     *  exempt the launcher — used for the location-disabled gate, which is meant to keep nagging
     *  the child even while they're just sitting on the home screen doing nothing, not only once
     *  they try to open something (confirmed live: a parent explicitly wanted this to show up the
     *  instant location gets turned off, not only when the child next tries to open an app). See
     *  [isEssentialWithHomeExit] for the variant that also exempts the launcher.
     *
     *  Also deliberately NOT used by [evaluateSchedules]' specific per-app blocklist — confirmed
     *  live as a real bug: a parent had put Chrome on this full-lockdown allow-list for an
     *  unrelated reason, which silently exempted Chrome from a *different* schedule that
     *  explicitly, deliberately named Chrome to block — an allow-list for "stay reachable when
     *  everything is blocked" has no business overriding a parent's specific, deliberate choice to
     *  block one exact app. */
    private fun isEssentialDuringFullBlock(pkg: String?): Boolean {
        if (isAlwaysReachable(pkg)) return true
        return pkg in (family?.fullBlockAllowedPackages ?: emptyList())
    }

    /** [isEssentialDuringFullBlock] plus the launcher — for gates whose overlay offers "На главный
     *  экран" as its actual intended exit (pending install approval; a "Блокировать всё" schedule
     *  or the total daily time cap running out). Same "tapping home must not be a trap" reasoning
     *  as [isAlwaysReachableOrLauncher], just layered on top of the parent-configurable allow-list
     *  too since these gates are genuine whole-device locks, unlike the per-app blocklist. */
    private fun isEssentialWithHomeExit(pkg: String?): Boolean {
        if (isEssentialDuringFullBlock(pkg)) return true
        return pkg == defaultLauncherPackage()
    }

    private fun defaultDialerPackage(): String? = runCatching {
        (getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)?.defaultDialerPackage
    }.getOrNull()

    private fun defaultLauncherPackage(): String? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    /** An active "Блокировать всё" schedule right now, if any — a genuine full-device lock, distinct
     *  from a schedule that only blocks a specific app list (handled by [evaluateSchedules]). */
    private fun activeFullBlockSchedule(timezone: String): ScheduleCacheEntity? {
        val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
        val now = LocalTime.now(zone)
        val nowMinutes = now.hour * 60 + now.minute
        val isoDayOfWeek = DayBoundary.isoDayOfWeek(timezone)
        return activeSchedules.firstOrNull {
            it.enabled && it.blockAllApps &&
                ScheduleWindow.matchesDay(it.daysOfWeekList(), isoDayOfWeek) &&
                ScheduleWindow.contains(it.startMinutes, it.endMinutes, nowMinutes)
        }
    }

    /** Only the per-app blocklist schedules — full-device-lock schedules are handled separately
     *  in [activeFullBlockSchedule]. Deliberately checks [isAlwaysReachableOrLauncher], NOT
     *  [isEssentialDuringFullBlock]/[isEssentialWithHomeExit] — the parent's full-lockdown
     *  allow-list is a different feature and must not silently override a specific, deliberate
     *  "block exactly this app" choice (see isEssentialDuringFullBlock's kdoc for the live bug
     *  this fixes), while the launcher still needs to stay exempt so this overlay's own home
     *  button actually works. Null = allowed. */
    private fun evaluateSchedules(pkg: String, timezone: String): String? {
        if (isAlwaysReachableOrLauncher(pkg)) return null
        val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
        val now = LocalTime.now(zone)
        val nowMinutes = now.hour * 60 + now.minute
        val isoDayOfWeek = DayBoundary.isoDayOfWeek(timezone)
        val active = activeSchedules.filter {
            it.enabled && !it.blockAllApps &&
                ScheduleWindow.matchesDay(it.daysOfWeekList(), isoDayOfWeek) &&
                ScheduleWindow.contains(it.startMinutes, it.endMinutes, nowMinutes)
        }
        val specific = active.firstOrNull { s -> pkg in s.blockedPackageNamesCsv.split(",").filter { it.isNotBlank() } }
        return specific?.let { "Сейчас «${it.name}» — это приложение недоступно" }
    }

    private fun ScheduleCacheEntity.daysOfWeekList(): List<Int> =
        daysOfWeekCsv.split(",").mapNotNull { it.trim().toIntOrNull() }

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

    private fun isLocationServicesEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return runCatching { LocationManagerCompat.isLocationEnabled(locationManager) }.getOrDefault(true)
    }

    private fun showLocationDisabledOverlay(blockedPackage: String?) {
        notifyLocationDisabledIfNeeded()
        goHome()
        overlayController.show(
            title = "Геолокация отключена",
            reason = "Родитель должен видеть, где ты находишься. Включи геолокацию в настройках, чтобы продолжить пользоваться телефоном.",
            allowRequestMore = false,
            onGoHome = ::goHome,
            onRequestMore = {},
            actionLabel = "Открыть настройки",
            onAction = { openLocationSettings() },
            showHomeButton = false,
            blockedPackage = blockedPackage
        )
        ensureLockScreenBlockShowing(
            kind = LockScreenBlockActivity.KIND_LOCATION_DISABLED,
            title = "Геолокация отключена",
            reason = "Родитель должен видеть, где ты находишься. Включи геолокацию в настройках, чтобы продолжить пользоваться телефоном."
        )
    }

    private fun openLocationSettings() {
        // TYPE_APPLICATION_OVERLAY sits above regular activity windows — without this, Settings
        // genuinely launches underneath (confirmed live via dumpsys) but stays visually hidden
        // behind this still-showing block screen, making "Открыть настройки" look like it does
        // nothing. The tick() check above already exempts SETTINGS_PACKAGE from re-triggering this
        // same overlay while the child is legitimately in there trying to fix it.
        overlayController.hide()
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            // FLAG_ACTIVITY_NEW_TASK alone isn't enough — confirmed live on Samsung's Settings app
            // that if it already has a task running in the background (e.g. left open on Airplane
            // Mode from something else entirely), Android just resurfaces that existing task as-is
            // instead of navigating within it to the newly requested screen, landing on whatever
            // unrelated page was last open. CLEAR_TASK forces a genuinely fresh launch instead.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
    }

    /** Debounced like checkBattery()'s lowBatteryNotified — one alert per off-period, not one per 3s tick. */
    private fun notifyLocationDisabledIfNeeded() {
        if (locationDisabledNotified) return
        locationDisabledNotified = true
        val familyId = currentFamilyId ?: return
        serviceScope.launch {
            runCatching {
                eventRepository.logEvent(
                    familyId,
                    EventLogEntry(type = EventType.LOCATION_DISABLED, message = "Ребёнок отключил геолокацию")
                )
            }
        }
    }

    /** A race against the radio actually powering off a moment after this broadcast — best-effort,
     *  launched immediately (not awaited by anything) to give the write the best possible chance of
     *  reaching Firestore over whatever connection is still alive at this exact instant. If it
     *  doesn't make it out in time, the parent simply won't know until airplane mode comes back off
     *  — there's no way around that once the radio is actually down. */
    private fun notifyAirplaneModeEnabled() {
        val familyId = currentFamilyId ?: return
        serviceScope.launch {
            runCatching {
                eventRepository.logEvent(
                    familyId,
                    EventLogEntry(type = EventType.AIRPLANE_MODE_ENABLED, message = "Ребёнок включил авиарежим")
                )
            }
        }
    }

    /** Same race-the-radio reasoning as [notifyAirplaneModeEnabled], run as its own independent
     *  coroutine so a slow location fix never delays that event write (or vice versa) — this is
     *  the one moment location tracking matters most (the child just went dark on purpose), and
     *  the normal ~60s [locationStatusLoop] cadence could otherwise leave the parent looking at a
     *  position from up to a minute before whatever prompted this. Once the radio is actually down
     *  there's nothing left to upload to, same caveat as the event log. */
    private fun captureAndUploadLocationOnAirplaneMode() {
        val familyId = currentFamilyId ?: return
        serviceScope.launch { captureAndUploadLocation(familyId) }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
    }

    /**
     * MOVE_TO_FOREGROUND fires once per app switch, not continuously — querying only a fixed
     * trailing window (the old approach) goes blind the moment the child stops switching apps,
     * since the one relevant event ages out of the window with nothing to replace it. Instead we
     * only ever query events since the last tick and carry the tracked package forward, so staying
     * in one app keeps reporting that app until it's actually backgrounded.
     */
    private fun queryForegroundApp(): String? {
        val end = System.currentTimeMillis()
        // On the very first tick after a (re)start, the currently open app may have been sitting in the
        // foreground for a long time already — its MOVE_TO_FOREGROUND event could be well outside a short
        // window. Look back far enough on that one cold-start query to still find it and pick up tracking
        // where it left off, instead of only catching apps the child switches to *after* the service starts.
        //
        // On every later tick, re-query a few seconds further back than our own last query's end time
        // (not exactly from it) — UsageStatsManager doesn't guarantee an event is queryable the instant
        // it happens, and advancing the cursor with zero overlap risks permanently skipping one that was
        // still being recorded right as the previous query ran. That skipped event is exactly what leaves
        // the tracked package stuck on whatever was open before (e.g. a photo picker briefly opened from
        // Chrome/Telegram, and the switch back is the event that goes missing). The timestamp dedup below
        // stops the overlap from reprocessing/rewinding events we've already applied.
        val begin = if (lastEventQueryTime == 0L) {
            end - COLD_START_QUERY_WINDOW_MS
        } else {
            lastEventQueryTime - EVENT_QUERY_OVERLAP_MS
        }
        lastEventQueryTime = end
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp <= lastAppliedEventTimestamp) continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> trackedForegroundPackage = event.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (event.packageName == trackedForegroundPackage) trackedForegroundPackage = null
                }
            }
            lastAppliedEventTimestamp = event.timeStamp
        }
        return trackedForegroundPackage
    }

    private suspend fun syncInstalledApps(familyId: String) {
        val pm = packageManager
        val iconStatus = runCatching { eventRepository.getKnownAppIconStatus(familyId) }.getOrDefault(emptyMap())
        val launchable = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName && pm.getLaunchIntentForPackage(it.packageName) != null }

        // Only the very first run of this scan (ever, for this device) should grandfather unknown
        // apps in as approved — requiring approval there would lock the child out of everything
        // they already had the moment setup finished. Every later run only happens because the
        // foreground service restarted (frequent — Samsung's aggressive background kills), and by
        // then any app still missing from Firestore is missing because it was genuinely installed
        // after setup, not because it was already there. Treating that the same as "already
        // approved" silently defeated the entire approval feature: a real post-setup install just
        // needed to land in the gap before the ACTION_PACKAGE_ADDED receiver re-registered.
        val isFirstEverSync = !childPreferences.installBaselineSynced.first()

        launchable.forEach { appInfo ->
            val hasIcon = iconStatus[appInfo.packageName]
            when (hasIcon) {
                null -> recordInstalledApp(familyId, appInfo, requiresApproval = !isFirstEverSync)
                // Already recorded (from before icon capture existed) but missing one — backfill
                // without re-logging a "new install" event for an app that isn't actually new.
                false -> runCatching {
                    iconToBase64(pm.getApplicationIcon(appInfo))?.let { eventRepository.backfillAppIcon(familyId, appInfo.packageName, it) }
                }
                true -> Unit
            }
        }

        if (isFirstEverSync) childPreferences.setInstallBaselineSynced()
    }

    private suspend fun recordInstalledApp(familyId: String, appInfo: ApplicationInfo, requiresApproval: Boolean) {
        val pm = packageManager
        val label = pm.getApplicationLabel(appInfo).toString()
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val iconBase64 = runCatching { iconToBase64(pm.getApplicationIcon(appInfo)) }.getOrNull()
        runCatching {
            eventRepository.recordNewInstall(familyId, appInfo.packageName, label, isSystem, iconBase64, requiresApproval)
        }
    }

    /** Renders the app's real launcher icon (handles adaptive icons, vectors, bitmaps uniformly
     *  via Canvas) into a small PNG, embedded directly in the InstalledApp doc — see its kdoc for
     *  why this avoids needing Firebase Storage. */
    private fun iconToBase64(drawable: Drawable, size: Int = 96): String? = runCatching {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    private fun registerPackageAddedReceiver(familyId: String) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // EXTRA_REPLACING is true when this broadcast is an existing app being updated, not
                // a genuinely new install — without this check, every routine app update (Play
                // Store auto-updates included) re-ran recordInstalledApp with requiresApproval=true,
                // and since that writes via a full .set() (see recordNewInstall), it overwrote an
                // already-APPROVED app's status straight back to PENDING. Confirmed live: this made
                // the parent's approval look like it didn't stick — she'd approve it, then the next
                // update (or even a re-delivery of the same broadcast after a service restart) would
                // silently revert it, locking the child back out of an app they already had.
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                val addedPackage = intent.data?.schemeSpecificPart ?: return
                serviceScope.launch {
                    val pm = packageManager
                    val appInfo = runCatching { pm.getApplicationInfo(addedPackage, 0) }.getOrNull() ?: return@launch
                    if (pm.getLaunchIntentForPackage(addedPackage) == null) return@launch
                    // A real post-setup install (not the baseline scan) — needs the parent's okay
                    // before the child can open it. See Family instant-block check in tick().
                    recordInstalledApp(familyId, appInfo, requiresApproval = true)
                }
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") })
        packageAddedReceiver = receiver
    }

    /** Reacts to location being toggled off the instant it happens (system broadcast, not the
     *  ~3s poll tick) — previously the block screen only appeared once the child next switched
     *  apps, which read as "only shows up when he tries to open an app" even though the same
     *  tick()-based check would have caught it a few seconds later regardless of that. This makes
     *  it immediate no matter what the child does (or doesn't do) right after disabling it. */
    private fun registerLocationProvidersReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (isLocationServicesEnabled()) return
                val trackedPackage = trackedForegroundPackage
                if (trackedPackage == packageName || trackedPackage == SETTINGS_PACKAGE ||
                    isEssentialDuringFullBlock(trackedPackage)
                ) return
                showLocationDisabledOverlay(trackedPackage)
            }
        }
        registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        locationProvidersReceiver = receiver
    }

    /** Airplane mode would cut the phone off from Firestore entirely — the same way a child could
     *  otherwise sidestep every other block by just going offline. There's no public API to
     *  actually *prevent* toggling it (that requires Device Owner, which can't be added to an
     *  already-set-up phone without a factory reset), so instead: the instant it's turned on, lock
     *  the whole screen and require the parent's protection PIN (same one used for the uninstall
     *  guard) to get back in. See [AirplaneModeGuard]. */
    private fun registerAirplaneModeReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val isOn = intent.getBooleanExtra("state", isAirplaneModeOn())
                airplaneModeGuard.onAirplaneModeChanged(isOn)
                if (!isOn) {
                    airplaneModeNotified = false
                    hideAirplaneModeOverlayIfShowing()
                    return
                }
                if (!airplaneModeGuard.shouldBlock(isOn)) return
                showAirplaneModeOverlay()
                // Debounced like checkBattery()'s lowBatteryNotified — Android can fire this
                // broadcast more than once for a single toggle, and this is a one-shot heads-up,
                // not something that should spam the parent's event log once per redundant fire.
                if (!airplaneModeNotified) {
                    airplaneModeNotified = true
                    notifyAirplaneModeEnabled()
                    captureAndUploadLocationOnAirplaneMode()
                }
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        airplaneModeReceiver = receiver
    }

    private fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    /** Remote "get me out of this" for a parent, e.g. the child disabled location and isn't
     *  cooperating (or isn't reachable) to fix it themselves. Writing this setting needs
     *  WRITE_SECURE_SETTINGS, which isn't grantable to a normal app at runtime — it has to be
     *  handed to this app once via
     *  `adb shell pm grant com.teo.child android.permission.WRITE_SECURE_SETTINGS`
     *  when the device is set up (doesn't survive an uninstall+reinstall without redoing that
     *  step). Silently does nothing if that was never granted — runCatching absorbs the
     *  SecurityException rather than crashing the command-handling loop over it.
     *
     *  There's deliberately no airplane-mode equivalent of this — confirmed live it can't actually
     *  work in the one case it would matter (zero connectivity): the command to turn it back off
     *  travels over Firestore, so a device with no network at all can't receive the very command
     *  meant to restore its network. LOCATION_MODE itself is deprecated (API 19-28 era) in favor
     *  of per-provider toggles, but the framework still honors it as a compatibility shim on
     *  current Android versions, and there's no public non-deprecated equivalent a third-party app
     *  (even with this permission) can write instead. */
    @Suppress("DEPRECATION")
    private fun enableLocationRemotely() {
        runCatching {
            Settings.Secure.putInt(contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
        }
    }

    private fun showAirplaneModeOverlay() {
        airplaneOverlayActive = true
        overlayController.show(
            title = "Авиарежим включён",
            reason = "Телефон недоступен, пока включён авиарежим. Попроси маму ввести код защиты, чтобы продолжить.",
            allowRequestMore = false,
            onGoHome = {},
            onRequestMore = {},
            actionLabel = "Ввести код защиты",
            onAction = { openPinChallengeForAirplaneMode() },
            showHomeButton = false,
            blockedPackage = null
        )
        ensureLockScreenBlockShowing(
            kind = LockScreenBlockActivity.KIND_AIRPLANE_MODE,
            title = "Авиарежим включён",
            reason = "Телефон недоступен, пока включён авиарежим. Попроси маму ввести код защиты, чтобы продолжить."
        )
    }

    private fun hideAirplaneModeOverlayIfShowing() {
        if (lockScreenBlockController.isShowing(LockScreenBlockActivity.KIND_AIRPLANE_MODE)) {
            lockScreenBlockController.dismiss()
        }
        if (!airplaneOverlayActive) return
        airplaneOverlayActive = false
        overlayController.hide()
    }

    /** Launches [LockScreenBlockActivity] alongside the regular overlay — see that class's kdoc
     *  for why both are needed. Guarded by [LockScreenBlockController.isShowing] so this doesn't
     *  relaunch it every ~3s tick while the condition holds; when unlocked it's a harmless no-op
     *  sitting behind the regular overlay, which always renders above it. */
    private fun ensureLockScreenBlockShowing(kind: String, title: String, reason: String) {
        if (lockScreenBlockController.isShowing(kind)) return
        startActivity(
            Intent(this, LockScreenBlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(LockScreenBlockActivity.EXTRA_KIND, kind)
                putExtra(LockScreenBlockActivity.EXTRA_TITLE, title)
                putExtra(LockScreenBlockActivity.EXTRA_REASON, reason)
            }
        )
    }

    private fun openPinChallengeForAirplaneMode() {
        // TYPE_APPLICATION_OVERLAY sits above regular activity windows, including our own — without
        // this, PinChallengeActivity actually launches and has real input focus underneath (confirmed
        // live via dumpsys), but stays visually hidden behind the still-showing block screen, making
        // "Ввести код защиты" look like it does nothing at all. If the PIN entry is cancelled or
        // fails, the next poll tick (within ~3s) re-shows this overlay on its own — see tick()'s
        // airplaneModeGuard check — so nothing here needs to re-show it manually.
        overlayController.hide()
        startActivity(
            Intent(this, PinChallengeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(PinChallengeActivity.EXTRA_PURPOSE, PinChallengeActivity.PURPOSE_AIRPLANE_MODE)
            }
        )
    }

    private fun buildNotification(): android.app.Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Nest Kid", NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, openAppIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nest Kid активен")
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
        private const val LOCATION_STATUS_INTERVAL_MS = 60_000L
        private const val USAGE_UPLOAD_INTERVAL_MS = 30_000L
        private const val COLD_START_QUERY_WINDOW_MS = 6 * 60 * 60 * 1000L
        private const val EVENT_QUERY_OVERLAP_MS = 5_000L
        private const val LOW_BATTERY_THRESHOLD = 15
        private const val LOW_BATTERY_RESET_THRESHOLD = 20
        /** Exempted specifically from the location-disabled block — its whole "Открыть настройки"
         *  action sends the child straight into this exact app, so it has to be reachable while
         *  they're legitimately there fixing the problem, or the block just re-triggers itself. */
        private const val SETTINGS_PACKAGE = "com.android.settings"
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
