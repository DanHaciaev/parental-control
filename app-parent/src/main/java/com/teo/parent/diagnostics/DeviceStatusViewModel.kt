package com.teo.parent.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.DeviceStatus
import com.teo.core.repository.DeviceStatusRepository
import com.teo.core.repository.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceStatusUiState(
    val loading: Boolean = true,
    val status: DeviceStatus? = null
)

@HiltViewModel
class DeviceStatusViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val deviceStatusRepository: DeviceStatusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceStatusUiState())
    val uiState: StateFlow<DeviceStatusUiState> = _uiState.asStateFlow()

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
                    deviceStatusRepository.observeStatus(familyId).collect { status ->
                        _uiState.update { it.copy(loading = false, status = status) }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }
}
