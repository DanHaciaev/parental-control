package com.teo.parent.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.HistoryEntry
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val loading: Boolean = true,
    val entries: List<HistoryEntry> = emptyList()
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            while (true) {
                try {
                    historyRepository.observeRecent(familyId).collect { entries ->
                        _uiState.update { it.copy(loading = false, entries = entries) }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }
}
