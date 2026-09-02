package com.teo.parent.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.AppRule
import com.teo.core.model.CommandType
import com.teo.core.model.RuleMode
import com.teo.core.model.TaskStatus
import com.teo.core.repository.CommandRepository
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.RuleRepository
import com.teo.core.repository.TaskRepository
import com.teo.core.repository.UsageRepository
import com.teo.core.util.DayBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppCategory { BLOCKED, TIMED, UNRESTRICTED }

data class AppRow(
    val packageName: String,
    val appLabel: String,
    val rule: AppRule?,
    val usedMinutesToday: Int = 0
) {
    val category: AppCategory
        get() = when (rule?.mode) {
            RuleMode.BLOCKED -> AppCategory.BLOCKED
            RuleMode.TIME_LIMIT -> AppCategory.TIMED
            null -> AppCategory.UNRESTRICTED
        }
}

data class DashboardUiState(
    val loading: Boolean = true,
    val familyId: String? = null,
    val apps: List<AppRow> = emptyList(),
    val pendingTasksCount: Int = 0
) {
    val blocked get() = apps.filter { it.category == AppCategory.BLOCKED }
    val timed get() = apps.filter { it.category == AppCategory.TIMED }
    val unrestricted get() = apps.filter { it.category == AppCategory.UNRESTRICTED }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository,
    private val ruleRepository: RuleRepository,
    private val usageRepository: UsageRepository,
    private val commandRepository: CommandRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

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
            val todayKey = DayBoundary.todayKey(timezone)

            // Retries on failure (e.g. a transient PERMISSION_DENIED right after cold start,
            // before the auth token is fully attached) instead of letting the listener crash the app.
            while (true) {
                try {
                    combine(
                        eventRepository.observeInstalledApps(familyId),
                        ruleRepository.observeRules(familyId),
                        usageRepository.observeUsageForDay(familyId, todayKey)
                    ) { apps, rules, usage ->
                        val ruleByPackage = rules.associateBy { it.packageName }
                        val usageByPackage = usage.associateBy { it.packageName }
                        apps
                            .map { app ->
                                AppRow(
                                    packageName = app.packageName,
                                    appLabel = app.appLabel,
                                    rule = ruleByPackage[app.packageName],
                                    usedMinutesToday = usageByPackage[app.packageName]?.minutesUsedToday ?: 0
                                )
                            }
                            .sortedBy { it.appLabel.lowercase() }
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
                        taskRepository.observeTasks(familyId)
                    ) { requests, tasks ->
                        requests.size + tasks.count { it.status == TaskStatus.DONE_BY_CHILD }
                    }.collect { count ->
                        _uiState.update { it.copy(pendingTasksCount = count) }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun blockApp(packageName: String, appLabel: String) = setRule(
        AppRule(packageName = packageName, appLabel = appLabel, mode = RuleMode.BLOCKED)
    )

    fun setTimeLimit(packageName: String, appLabel: String, minutes: Int) = setRule(
        AppRule(packageName = packageName, appLabel = appLabel, mode = RuleMode.TIME_LIMIT, dailyLimitMinutes = minutes)
    )

    fun clearRule(packageName: String) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { ruleRepository.removeRule(familyId, packageName) }
    }

    /** Lets the child's phone uninstall its own monitoring app — the only sanctioned way to remove it. */
    fun releaseChildProtection() {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { commandRepository.sendCommand(familyId, CommandType.RELEASE_PROTECTION) }
    }

    private fun setRule(rule: AppRule) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { ruleRepository.setRule(familyId, rule) }
    }
}
