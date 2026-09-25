package com.teo.parent.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.AppRule
import com.teo.core.model.AssignedSkill
import com.teo.core.model.MoreTimeRequest
import com.teo.core.model.RequestStatus
import com.teo.core.model.SkillTemplate
import com.teo.core.model.Task
import com.teo.core.model.TaskStatus
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.RuleRepository
import com.teo.core.repository.SkillRepository
import com.teo.core.repository.TaskRepository
import com.teo.core.util.DayBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An assigned skill joined with today's progress — [assigned] and today's [SkillProgressEntry]
 *  are two separate documents/listeners, combined here for the UI. */
data class SkillRow(val assigned: AssignedSkill, val progress: Int, val approved: Boolean) {
    val target get() = assigned.targetPerDay
    val completed get() = progress >= target
}

data class RequestsUiState(
    val loading: Boolean = true,
    val familyId: String? = null,
    val timezone: String = "Europe/Moscow",
    val moreTimeRequests: List<MoreTimeRequest> = emptyList(),
    val tasksAwaitingApproval: List<Task> = emptyList(),
    val openTasks: List<Task> = emptyList(),
    val timeLimitedApps: List<AppRule> = emptyList(),
    val skillsAwaitingApproval: List<SkillRow> = emptyList(),
    val skillsInProgress: List<SkillRow> = emptyList(),
    val assignedTemplateIds: Set<String> = emptySet()
)

private data class RequestsSnapshot(
    val requests: List<MoreTimeRequest>,
    val tasks: List<Task>,
    val rules: List<AppRule>,
    val skillsAwaitingApproval: List<SkillRow>,
    val skillsInProgress: List<SkillRow>,
    val assignedTemplateIds: Set<String>
)

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository,
    private val ruleRepository: RuleRepository,
    private val taskRepository: TaskRepository,
    private val skillRepository: SkillRepository
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

            // DayBoundary.dateKeyFlow re-subscribes skill progress on day rollover — a single
            // todayKey grabbed once here (the original bug in this file) would otherwise freeze
            // "today's" skill progress on whatever day the screen was first opened, the same class
            // of bug fixed in DashboardViewModel/StatisticsViewModel earlier.
            while (true) {
                try {
                    DayBoundary.dateKeyFlow(timezone).flatMapLatest { dateKey ->
                        combine(
                            eventRepository.observePendingRequests(familyId),
                            taskRepository.observeTasks(familyId),
                            ruleRepository.observeRules(familyId),
                            skillRepository.observeAssignedSkills(familyId),
                            skillRepository.observeProgressForDay(familyId, dateKey)
                        ) { requests, tasks, rules, assignedSkills, progress ->
                            val progressBySkillId = progress.associateBy { it.skillId }
                            val skillRows = assignedSkills.map { assigned ->
                                val p = progressBySkillId[assigned.id]
                                SkillRow(assigned = assigned, progress = p?.progress ?: 0, approved = p?.approved ?: false)
                            }
                            RequestsSnapshot(
                                requests = requests,
                                tasks = tasks,
                                rules = rules,
                                skillsAwaitingApproval = skillRows.filter { it.completed && !it.approved },
                                skillsInProgress = skillRows.filter { !it.completed },
                                assignedTemplateIds = assignedSkills.map { it.templateId }.toSet()
                            )
                        }
                    }.collect { snap ->
                        _uiState.update {
                            it.copy(
                                loading = false,
                                moreTimeRequests = snap.requests,
                                tasksAwaitingApproval = snap.tasks.filter { t -> t.status == TaskStatus.DONE_BY_CHILD },
                                openTasks = snap.tasks.filter { t -> t.status == TaskStatus.OPEN },
                                timeLimitedApps = snap.rules.filter { r -> r.dailyLimitMinutes != null },
                                skillsAwaitingApproval = snap.skillsAwaitingApproval,
                                skillsInProgress = snap.skillsInProgress,
                                assignedTemplateIds = snap.assignedTemplateIds
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
            runCatching {
                ruleRepository.grantBonusMinutes(familyId, request.packageName, minutes, DayBoundary.todayKey(state.timezone))
                eventRepository.resolveRequest(familyId, request.id, RequestStatus.APPROVED, minutes)
            }
        }
    }

    fun denyRequest(request: MoreTimeRequest) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch {
            runCatching { eventRepository.resolveRequest(familyId, request.id, RequestStatus.DENIED, null) }
        }
    }

    fun createTask(
        title: String,
        rewardMinutes: Int,
        rewardPackageName: String?,
        photoBytes: ByteArray? = null,
        linkUrl: String? = null
    ) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch {
            runCatching {
                taskRepository.createTask(familyId, title, rewardMinutes, rewardPackageName, photoBytes, linkUrl)
            }
        }
    }

    fun approveTask(task: Task) {
        val state = _uiState.value
        val familyId = state.familyId ?: return
        viewModelScope.launch {
            runCatching {
                taskRepository.approveTask(familyId, task.id)
                val targetPackage = task.rewardPackageName
                if (targetPackage != null) {
                    ruleRepository.grantBonusMinutes(
                        familyId, targetPackage, task.rewardMinutes, DayBoundary.todayKey(state.timezone)
                    )
                }
            }
        }
    }

    fun rejectTask(task: Task) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { taskRepository.rejectTask(familyId, task.id) } }
    }

    fun assignSkill(template: SkillTemplate, targetPerDay: Int, rewardMinutes: Int, rewardPackageName: String?) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch {
            runCatching { skillRepository.assignSkill(familyId, template, targetPerDay, rewardMinutes, rewardPackageName) }
        }
    }

    fun unassignSkill(skillId: String) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { skillRepository.unassignSkill(familyId, skillId) } }
    }

    /** Approve-then-grant, mirroring approveTask above — the reward only exists once both the
     *  child hit the quota AND the parent confirmed it here. */
    fun confirmSkill(row: SkillRow) {
        val state = _uiState.value
        val familyId = state.familyId ?: return
        val dateKey = DayBoundary.todayKey(state.timezone)
        viewModelScope.launch {
            runCatching {
                skillRepository.approveSkillProgress(familyId, row.assigned.id, dateKey)
                val targetPackage = row.assigned.rewardPackageName
                if (targetPackage != null) {
                    ruleRepository.grantBonusMinutes(familyId, targetPackage, row.assigned.rewardMinutes, dateKey)
                }
            }
        }
    }
}
