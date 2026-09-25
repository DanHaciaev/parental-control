package com.teo.parent.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.EventLogEntry
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventsUiState(
    val loading: Boolean = true,
    val familyId: String? = null,
    val events: List<EventLogEntry> = emptyList()
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            _uiState.update { it.copy(familyId = familyId) }
            while (true) {
                try {
                    eventRepository.observeRecentEvents(familyId).collect { events ->
                        _uiState.update { it.copy(loading = false, events = events) }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun markRead(event: EventLogEntry) {
        val familyId = _uiState.value.familyId ?: return
        if (event.read) return
        viewModelScope.launch { runCatching { eventRepository.markEventRead(familyId, event.id) } }
    }
}
