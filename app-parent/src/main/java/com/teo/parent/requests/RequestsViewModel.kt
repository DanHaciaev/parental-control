package com.teo.parent.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.AppRule
import com.teo.core.model.MoreTimeRequest
import com.teo.core.model.RequestStatus
import com.teo.core.model.Task
import com.teo.core.model.TaskStatus
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.RuleRepository
import com.teo.core.repository.TaskRepository
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

data class RequestsUiState(
    val loading: Boolean = true,
    val familyId: String? = null,
    val timezone: String = "Europe/Moscow",
    val moreTimeRequests: List<MoreTimeRequest> = emptyList(),
    val tasksAwaitingApproval: List<Task> = emptyList(),
    val openTasks: List<Task> = emptyList(),
    val timeLimitedApps: List<AppRule> = emptyList()
)

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository,
    private val ruleRepository: RuleRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            val timezone = runCatching { familyRepository.getFamily(familyId)?.timezone }
                .getOrNull() ?: "Europe/Moscow"
            _uiState.update { it.copy(familyId = familyId, timezone = timezone) }

            while (true) {
                try {
                    combine(
                        eventRepository.observePendingRequests(familyId),
                        taskRepository.observeTasks(familyId),
                        ruleRepository.observeRules(familyId)
                    ) { requests, tasks, rules ->
                        Triple(requests, tasks, rules)
                    }.collect { (requests, tasks, rules) ->
                        _uiState.update {
                            it.copy(
                                loading = false,
                                moreTimeRequests = requests,
                                tasksAwaitingApproval = tasks.filter { t -> t.status == TaskStatus.DONE_BY_CHILD },
                                openTasks = tasks.filter { t -> t.status == TaskStatus.OPEN },
                                timeLimitedApps = rules.filter { r -> r.dailyLimitMinutes != null }
                            )
                        }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun approveRequest(request: MoreTimeRequest, minutes: Int) {
        val state = _uiState.value
        val familyId = state.familyId ?: return
        viewModelScope.launch {
            ruleRepository.grantBonusMinutes(familyId, request.packageName, minutes, DayBoundary.todayKey(state.timezone))
            eventRepository.resolveRequest(familyId, request.id, RequestStatus.APPROVED, minutes)
        }
    }

    fun denyRequest(request: MoreTimeRequest) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { eventRepository.resolveRequest(familyId, request.id, RequestStatus.DENIED, null) }
    }

    fun createTask(title: String, rewardMinutes: Int, rewardPackageName: String?) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { taskRepository.createTask(familyId, title, rewardMinutes, rewardPackageName) }
    }

    fun approveTask(task: Task) {
        val state = _uiState.value
        val familyId = state.familyId ?: return
        viewModelScope.launch {
            taskRepository.approveTask(familyId, task.id)
            val targetPackage = task.rewardPackageName
            if (targetPackage != null) {
                ruleRepository.grantBonusMinutes(
                    familyId, targetPackage, task.rewardMinutes, DayBoundary.todayKey(state.timezone)
                )
            }
        }
    }

    fun rejectTask(task: Task) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { taskRepository.rejectTask(familyId, task.id) }
    }
}
