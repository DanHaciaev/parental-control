package com.teo.child.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teo.child.data.ChildPreferences
import com.teo.core.model.AssignedSkill
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.SkillRepository
import com.teo.core.util.DayBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

data class ChildSkillRow(val assigned: AssignedSkill, val progress: Int, val approved: Boolean) {
    val target get() = assigned.targetPerDay
    val completed get() = progress >= target
}

data class ChildSkillsUiState(val dateKey: String = "", val rows: List<ChildSkillRow> = emptyList())

@HiltViewModel
class ChildSkillsViewModel @Inject constructor(
    private val childPreferences: ChildPreferences,
    private val familyRepository: FamilyRepository,
    private val skillRepository: SkillRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChildSkillsUiState())
    val uiState: StateFlow<ChildSkillsUiState> = _uiState.asStateFlow()
    private var familyId: String? = null

    init {
        viewModelScope.launch {
            val fid = childPreferences.familyId.first() ?: return@launch
            familyId = fid
            val timezone = runCatching { familyRepository.getFamily(fid)?.timezone }
                .getOrNull() ?: TimeZone.getDefault().id

            // DayBoundary.dateKeyFlow re-subscribes on day rollover, same reason as every other
            // "today" query fixed elsewhere this session — otherwise progress would freeze on
            // whatever day the child app process happened to start.
            while (true) {
                try {
                    DayBoundary.dateKeyFlow(timezone).flatMapLatest { dateKey ->
                        combine(
                            skillRepository.observeAssignedSkills(fid),
                            skillRepository.observeProgressForDay(fid, dateKey)
                        ) { assigned, progress ->
                            val progressBySkillId = progress.associateBy { it.skillId }
                            val rows = assigned.map { a ->
                                val p = progressBySkillId[a.id]
                                ChildSkillRow(a, p?.progress ?: 0, p?.approved ?: false)
                            }
                            dateKey to rows
                        }
                    }.collect { (dateKey, rows) ->
                        _uiState.update { it.copy(dateKey = dateKey, rows = rows) }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun increment(row: ChildSkillRow, amount: Int) {
        val fid = familyId ?: return
        val dateKey = _uiState.value.dateKey
        if (dateKey.isBlank() || row.completed) return
        viewModelScope.launch {
            runCatching { skillRepository.incrementProgress(fid, row.assigned.id, dateKey, amount) }
        }
    }
}
