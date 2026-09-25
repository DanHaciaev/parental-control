package com.teo.child.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teo.child.data.ChildPreferences
import com.teo.child.data.local.AppBalance
import com.teo.child.data.local.LocalUsageStore
import com.teo.core.model.EventLogEntry
import com.teo.core.model.EventType
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

data class HomeUiState(
    val familyId: String? = null,
    val parentPhone: String? = null,
    val sosSent: Boolean = false,
    val balances: List<AppBalance> = emptyList(),
    val blockedAppLabels: List<String> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val childPreferences: ChildPreferences,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository,
    private val localUsageStore: LocalUsageStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val familyId = childPreferences.familyId.first() ?: return@launch
            _uiState.update { it.copy(familyId = familyId) }
            val family = runCatching { familyRepository.getFamily(familyId) }.getOrNull()
            _uiState.update { it.copy(parentPhone = family?.parentPhone) }

            val timezone = family?.timezone ?: TimeZone.getDefault().id
            launch {
                while (true) {
                    try {
                        localUsageStore.observeBalances(timezone).collect { balances ->
                            _uiState.update { it.copy(balances = balances) }
                        }
                    } catch (e: Exception) {
                        delay(2000)
                    }
                }
            }
            launch {
                while (true) {
                    try {
                        localUsageStore.observeBlockedAppLabels().collect { labels ->
                            _uiState.update { it.copy(blockedAppLabels = labels) }
                        }
                    } catch (e: Exception) {
                        delay(2000)
                    }
                }
            }
        }
    }

    /** Device Admin went inactive without the parent's RELEASE_PROTECTION command — likely the child. */
    fun onProtectionTamperedLocally() {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch {
            runCatching {
                eventRepository.logEvent(
                    familyId,
                    EventLogEntry(
                        type = EventType.PROTECTION_TAMPERED,
                        message = "Защита была отключена на телефоне ребёнка (Device Admin выключен вручную)"
                    )
                )
            }
        }
    }

    fun sendSos() {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch {
            runCatching {
                eventRepository.logEvent(
                    familyId,
                    EventLogEntry(type = EventType.SOS, message = "Ребёнок нажал кнопку SOS")
                )
            }
            _uiState.update { it.copy(sosSent = true) }
            // Otherwise the button is stuck reading "sent" forever and can't be pressed again for a real emergency.
            delay(SOS_SENT_DISPLAY_MS)
            _uiState.update { it.copy(sosSent = false) }
        }
    }

    private companion object {
        const val SOS_SENT_DISPLAY_MS = 15_000L
    }
}
