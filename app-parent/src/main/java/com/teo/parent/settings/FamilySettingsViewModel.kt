package com.teo.parent.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
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

data class FamilySettingsUiState(
    val loading: Boolean = true,
    val familyId: String? = null,
    val childName: String = "",
    val phone: String = "",
    val totalCapEnabled: Boolean = false,
    val totalCapMinutes: String = "60",
    val fullBlockAllowedPackages: Set<String> = emptySet(),
    val installedApps: List<InstalledApp> = emptyList(),
    val saved: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class FamilySettingsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FamilySettingsUiState())
    val uiState: StateFlow<FamilySettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false) }
                return@launch
            }
            val family = runCatching { familyRepository.getFamily(familyId) }.getOrNull()
            _uiState.update {
                it.copy(
                    loading = false,
                    familyId = familyId,
                    childName = family?.childName.orEmpty(),
                    phone = family?.parentPhone.orEmpty(),
                    totalCapEnabled = family?.totalScreenTimeCapMinutes != null,
                    totalCapMinutes = family?.totalScreenTimeCapMinutes?.toString() ?: "60",
                    fullBlockAllowedPackages = family?.fullBlockAllowedPackages?.toSet() ?: emptySet()
                )
            }

            while (true) {
                try {
                    eventRepository.observeInstalledApps(familyId).collect { apps ->
                        _uiState.update { it.copy(installedApps = apps.sortedBy { app -> app.appLabel.lowercase() }) }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    fun save(
        childName: String,
        phone: String,
        totalCapEnabled: Boolean,
        totalCapMinutes: String,
        fullBlockAllowedPackages: Set<String>
    ) {
        val familyId = _uiState.value.familyId ?: return
        val parsedCap = totalCapMinutes.toIntOrNull()
        // Silently writing null here (the old behavior) is indistinguishable from the parent
        // explicitly clearing the cap — it just quietly reverts to "not configured" with no explanation.
        if (totalCapEnabled && (parsedCap == null || parsedCap <= 0)) {
            _uiState.update { it.copy(errorMessage = "Укажите лимит в минутах больше нуля") }
            return
        }
        viewModelScope.launch {
            try {
                if (childName.isNotBlank()) familyRepository.setChildName(familyId, childName)
                if (phone.isNotBlank()) familyRepository.setParentPhone(familyId, phone)
                familyRepository.setTotalScreenTimeCap(familyId, if (totalCapEnabled) parsedCap else null)
                familyRepository.setFullBlockAllowedPackages(familyId, fullBlockAllowedPackages.toList())
                _uiState.update {
                    it.copy(
                        saved = true,
                        errorMessage = null,
                        childName = if (childName.isNotBlank()) childName else it.childName,
                        phone = if (phone.isNotBlank()) phone else it.phone,
                        totalCapEnabled = totalCapEnabled,
                        totalCapMinutes = totalCapMinutes,
                        fullBlockAllowedPackages = fullBlockAllowedPackages
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Не удалось сохранить — проверьте интернет и попробуйте ещё раз")
                }
            }
        }
    }
}
