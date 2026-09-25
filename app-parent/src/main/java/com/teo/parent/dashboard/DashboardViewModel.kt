package com.teo.parent.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.AppRule
import com.teo.core.model.CommandType
import com.teo.core.model.Device
import com.teo.core.model.DeviceStatus
import com.teo.core.model.EventType
import com.teo.core.model.RuleMode
import com.teo.core.model.TaskStatus
import com.teo.core.repository.CommandRepository
import com.teo.core.repository.DeviceRepository
import com.teo.core.repository.DeviceStatusRepository
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.RuleRepository
import com.teo.core.repository.ScheduleRepository
import com.teo.core.repository.TaskRepository
import com.teo.core.repository.UsageRepository
import com.teo.core.util.DayBoundary
import com.teo.core.util.ScheduleWindow
import com.teo.parent.notifications.SosNotifications
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

enum class AppCategory { BLOCKED, TIMED, UNRESTRICTED }

data class AppRow(
    val packageName: String,
    val appLabel: String,
    val rule: AppRule?,
    val iconBase64: String? = null,
    val usedMinutesToday: Int = 0,
    val bonusMinutesToday: Int = 0,
    /** ISO day-of-week (1=Monday..7=Sunday) this row was resolved for — resolving at construction
     *  time keeps every UI call site a plain zero-arg property instead of needing "today" threaded
     *  through the composable tree. */
    val isoDayOfWeek: Int = 1
) {
    val category: AppCategory
        get() = when (rule?.mode) {
            RuleMode.BLOCKED -> AppCategory.BLOCKED
            RuleMode.TIME_LIMIT -> AppCategory.TIMED
            null -> AppCategory.UNRESTRICTED
        }

    /** Today's limit (per-weekday override if set, else the flat daily one) plus any task-reward
     *  bonus minutes credited today — what the child can actually use. */
    val effectiveDailyLimitMinutes: Int?
        get() = rule?.effectiveLimitMinutes(isoDayOfWeek)?.plus(bonusMinutesToday)
}

data class DashboardUiState(
    val loading: Boolean = true,
    val familyId: String? = null,
    val childName: String? = null,
    val apps: List<AppRow> = emptyList(),
    val pendingTasksCount: Int = 0,
    val tamperAlert: Boolean = false,
    val deviceStatus: DeviceStatus? = null,
    val totalScreenTimeCapMinutes: Int? = null,
    val activeScheduleName: String? = null,
    val devices: List<Device> = emptyList()
) {
    val blocked get() = apps.filter { it.category == AppCategory.BLOCKED }
    val timed get() = apps.filter { it.category == AppCategory.TIMED }
    val unrestricted get() = apps.filter { it.category == AppCategory.UNRESTRICTED }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository,
    private val ruleRepository: RuleRepository,
    private val usageRepository: UsageRepository,
    private val commandRepository: CommandRepository,
    private val taskRepository: TaskRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val scheduleRepository: ScheduleRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** Tracked in-memory so we alert once per SOS event per process lifetime, not on every recomposition. */
    private val notifiedSosEventIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            _uiState.update { it.copy(familyId = familyId) }
            val timezone = runCatching { familyRepository.getFamily(familyId)?.timezone }
                .getOrNull() ?: "Europe/Moscow"

            // Retries on failure (e.g. a transient PERMISSION_DENIED right after cold start,
            // before the auth token is fully attached) instead of letting the listener crash the app.
            while (true) {
                try {
                    // DayBoundary.dateKeyFlow re-subscribes on day rollover — pinning a single
                    // todayKey here instead would freeze "used today" at whatever day the dashboard
                    // happened to be opened on for as long as this process stays alive past midnight,
                    // making a daily limit look like it never resets.
                    DayBoundary.dateKeyFlow(timezone).flatMapLatest { todayKey ->
                        val isoDayOfWeek = LocalDate.parse(todayKey).dayOfWeek.value
                        combine(
                            eventRepository.observeInstalledApps(familyId),
                            ruleRepository.observeRules(familyId),
                            usageRepository.observeUsageForDay(familyId, todayKey)
                        ) { apps, rules, usage ->
                            val ruleByPackage = rules.associateBy { it.packageName }
                            val usageByPackage = usage.associateBy { it.packageName }
                            apps
                                .map { app ->
                                    val rule = ruleByPackage[app.packageName]
                                    val bonus = if (rule?.bonusDateKey == todayKey) rule.bonusMinutesToday else 0
                                    AppRow(
                                        packageName = app.packageName,
                                        appLabel = app.appLabel,
                                        rule = rule,
                                        iconBase64 = app.iconBase64,
                                        usedMinutesToday = usageByPackage[app.packageName]?.minutesUsedToday ?: 0,
                                        bonusMinutesToday = bonus,
                                        isoDayOfWeek = isoDayOfWeek
                                    )
                                }
                                .sortedBy { it.appLabel.lowercase() }
                        }
                    }.collect { rows ->
                        _uiState.update { it.copy(loading = false, apps = rows) }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            while (true) {
                try {
                    combine(
                        eventRepository.observePendingRequests(familyId),
                        taskRepository.observeTasks(familyId),
                        eventRepository.observeRecentEvents(familyId)
                    ) { requests, tasks, events ->
                        val pendingCount = requests.size + tasks.count { it.status == TaskStatus.DONE_BY_CHILD }
                        val tamperAlert = events.any { it.type == EventType.PROTECTION_TAMPERED && !it.read }
                        Triple(pendingCount, tamperAlert, events)
                    }.collect { (count, tamperAlert, events) ->
                        _uiState.update { it.copy(pendingTasksCount = count, tamperAlert = tamperAlert) }
                        events.filter { it.type == EventType.SOS && !it.read && it.id !in notifiedSosEventIds }
                            .forEach { event ->
                                notifiedSosEventIds += event.id
                                SosNotifications.show(appContext)
                                // Without this, the in-memory notifiedSosEventIds set (which doesn't
                                // survive a process restart) is the only thing stopping a re-fire — every
                                // app restart would re-alarm for every historical unread SOS event again.
                                runCatching { eventRepository.markEventRead(familyId, event.id) }
                            }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            val timezone = runCatching { familyRepository.getFamily(familyId)?.timezone }
                .getOrNull() ?: "Europe/Moscow"
            // Deliberately NOT combined with the schedules listener below: a permission error or
            // outage on one subcollection would otherwise make the whole combine() throw forever,
            // silently blocking total-cap and device-status updates that have nothing to do with it.
            while (true) {
                try {
                    combine(
                        familyRepository.observeFamily(familyId),
                        deviceStatusRepository.observeStatus(familyId)
                    ) { family, status -> family to status }
                        .collect { (family, status) ->
                            _uiState.update {
                                it.copy(
                                    childName = family?.childName?.takeIf { name -> name.isNotBlank() },
                                    totalScreenTimeCapMinutes = family?.effectiveTotalCapMinutes(DayBoundary.isoDayOfWeek(timezone)),
                                    deviceStatus = status
                                )
                            }
                        }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            val timezone = runCatching { familyRepository.getFamily(familyId)?.timezone }
                .getOrNull() ?: "Europe/Moscow"
            while (true) {
                try {
                    scheduleRepository.observeSchedules(familyId).collect { schedules ->
                        val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
                        val now = LocalTime.now(zone)
                        val nowMinutes = now.hour * 60 + now.minute
                        val isoDayOfWeek = DayBoundary.isoDayOfWeek(timezone)
                        val activeScheduleName = schedules.firstOrNull {
                            it.enabled &&
                                ScheduleWindow.matchesDay(it.daysOfWeek, isoDayOfWeek) &&
                                ScheduleWindow.contains(it.startMinutes, it.endMinutes, nowMinutes)
                        }?.name
                        _uiState.update { it.copy(activeScheduleName = activeScheduleName) }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            while (true) {
                try {
                    deviceRepository.observeDevices(familyId).collect { devices ->
                        _uiState.update { it.copy(devices = devices) }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun setDeviceDailyLimit(deviceId: String, minutes: Int?) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { deviceRepository.setDeviceDailyLimit(familyId, deviceId, minutes) } }
    }

    fun lockDeviceNow(deviceId: String) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { deviceRepository.sendDeviceCommand(familyId, deviceId, CommandType.LOCK_NOW) } }
    }

    fun removeDevice(deviceId: String) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { deviceRepository.removeDevice(familyId, deviceId) } }
    }

    fun blockApp(packageName: String, appLabel: String) = setRule(
        AppRule(packageName = packageName, appLabel = appLabel, mode = RuleMode.BLOCKED)
    )

    fun setTimeLimit(packageName: String, appLabel: String, minutes: Int) = setRule(
        AppRule(packageName = packageName, appLabel = appLabel, mode = RuleMode.TIME_LIMIT, dailyLimitMinutes = minutes)
    )

    fun clearRule(packageName: String) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { ruleRepository.removeRule(familyId, packageName) } }
    }

    fun ringDevice() = sendCommand(CommandType.RING_DEVICE)

    fun stopRinging() = sendCommand(CommandType.STOP_RING)

    fun setRingerNormal() = sendCommand(CommandType.SET_RINGER_NORMAL)

    fun setRingerSilent() = sendCommand(CommandType.SET_RINGER_SILENT)

    fun enableLocation() = sendCommand(CommandType.ENABLE_LOCATION)

    private fun sendCommand(type: CommandType) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { commandRepository.sendCommand(familyId, type) } }
    }

    private fun setRule(rule: AppRule) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { ruleRepository.setRule(familyId, rule) } }
    }
}
