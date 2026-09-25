package com.teo.parent.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.InstalledApp
import com.teo.core.model.Schedule
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleUiState(
    val loading: Boolean = true,
    val familyId: String? = null,
    val schedules: List<Schedule> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList()
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val scheduleRepository: ScheduleRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            _uiState.update { it.copy(familyId = familyId) }

            runCatching { familyRepository.getFamily(familyId) }.getOrNull()?.let { family ->
                runCatching { scheduleRepository.migrateLegacyBedtimeIfNeeded(familyId, family) }
            }

            while (true) {
                try {
                    combine(
                        scheduleRepository.observeSchedules(familyId),
                        eventRepository.observeInstalledApps(familyId)
                    ) { schedules, apps -> schedules to apps }.collect { (schedules, apps) ->
                        _uiState.update {
                            it.copy(
                                loading = false,
                                schedules = schedules.sortedBy { s -> s.startMinutes },
                                installedApps = apps.sortedBy { app -> app.appLabel.lowercase() }
                            )
                        }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun save(schedule: Schedule) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { scheduleRepository.setSchedule(familyId, schedule) } }
    }

    fun delete(scheduleId: String) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { scheduleRepository.deleteSchedule(familyId, scheduleId) } }
    }

    fun toggleEnabled(schedule: Schedule, enabled: Boolean) = save(schedule.copy(enabled = enabled))
}
