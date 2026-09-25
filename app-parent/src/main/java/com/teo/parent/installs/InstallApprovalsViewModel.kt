package com.teo.parent.installs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.InstallApprovalStatus
import com.teo.core.model.InstalledApp
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

data class InstallApprovalsUiState(
    val loading: Boolean = true,
    val familyId: String? = null,
    val pendingInstalls: List<InstalledApp> = emptyList()
)

@HiltViewModel
class InstallApprovalsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstallApprovalsUiState())
    val uiState: StateFlow<InstallApprovalsUiState> = _uiState.asStateFlow()

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
                    eventRepository.observeInstalledApps(familyId).collect { apps ->
                        _uiState.update {
                            it.copy(
                                loading = false,
                                pendingInstalls = apps.filter { app -> app.approvalStatus == InstallApprovalStatus.PENDING.name }
                            )
                        }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun approveInstall(app: InstalledApp) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch {
            runCatching { eventRepository.setInstallApproval(familyId, app.packageName, InstallApprovalStatus.APPROVED) }
        }
    }

    fun denyInstall(app: InstalledApp) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch {
            runCatching { eventRepository.setInstallApproval(familyId, app.packageName, InstallApprovalStatus.DENIED) }
        }
    }
}
