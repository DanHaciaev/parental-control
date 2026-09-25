package com.teo.child.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teo.child.data.ChildPreferences
import com.teo.core.model.Task
import com.teo.core.model.TaskStatus
import com.teo.core.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChildTasksUiState(
    val familyId: String? = null,
    val openTasks: List<Task> = emptyList(),
    val waitingApprovalTasks: List<Task> = emptyList(),
    val approvedCount: Int = 0
)

@HiltViewModel
class ChildTasksViewModel @Inject constructor(
    private val childPreferences: ChildPreferences,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChildTasksUiState())
    val uiState: StateFlow<ChildTasksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val familyId = childPreferences.familyId.first() ?: return@launch
            _uiState.update { it.copy(familyId = familyId) }
            while (true) {
                try {
                    taskRepository.observeTasks(familyId).collect { tasks ->
                        _uiState.update {
                            it.copy(
                                openTasks = tasks.filter { t -> t.status == TaskStatus.OPEN },
                                waitingApprovalTasks = tasks.filter { t -> t.status == TaskStatus.DONE_BY_CHILD },
                                approvedCount = tasks.count { t -> t.status == TaskStatus.APPROVED }
                            )
                        }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun markDone(task: Task) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { taskRepository.markDoneByChild(familyId, task.id) } }
    }
}
