package com.teo.parent.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.repository.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilySettingsUiState(
    val loading: Boolean = true,
    val familyId: String? = null,
    val phone: String = "",
    val bedtimeEnabled: Boolean = false,
    val bedtimeStart: String = "22:00",
    val bedtimeEnd: String = "07:00",
    val saved: Boolean = false
)

@HiltViewModel
class FamilySettingsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FamilySettingsUiState())
    val uiState: StateFlow<FamilySettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            try {
                val familyId = familyRepository.findFamilyIdForParent(uid) ?: return@launch
                val family = familyRepository.getFamily(familyId)
                _uiState.update {
                    it.copy(
                        loading = false,
                        familyId = familyId,
                        phone = family?.parentPhone.orEmpty(),
                        bedtimeEnabled = family?.bedtimeStartMinutes != null,
                        bedtimeStart = family?.bedtimeStartMinutes?.let(::formatMinutes) ?: "22:00",
                        bedtimeEnd = family?.bedtimeEndMinutes?.let(::formatMinutes) ?: "07:00"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun save(phone: String, bedtimeEnabled: Boolean, bedtimeStart: String, bedtimeEnd: String) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch {
            try {
                if (phone.isNotBlank()) familyRepository.setParentPhone(familyId, phone)
                familyRepository.setBedtime(
                    familyId,
                    if (bedtimeEnabled) parseMinutes(bedtimeStart) else null,
                    if (bedtimeEnabled) parseMinutes(bedtimeEnd) else null
                )
                _uiState.update { it.copy(saved = true) }
            } catch (e: Exception) {
                // Leave the dialog open with current field values so the user can retry.
            }
        }
    }

    private fun formatMinutes(total: Int): String = "%02d:%02d".format(total / 60, total % 60)

    private fun parseMinutes(text: String): Int? {
        val parts = text.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
}
