package com.teo.parent.pin

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

data class PinChallengeUiState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val unlocked: Boolean = false
)

@HiltViewModel
class PinChallengeViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinChallengeUiState())
    val uiState: StateFlow<PinChallengeUiState> = _uiState.asStateFlow()

    fun verify(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            val uid = auth.currentUser?.uid
            val familyId = uid?.let { familyRepository.findFamilyIdForParent(it) }
            if (familyId == null) {
                _uiState.update { it.copy(loading = false, errorMessage = "Не удалось проверить код") }
                return@launch
            }
            val correct = familyRepository.verifyProtectionPin(familyId, pin)
            _uiState.update {
                if (correct) it.copy(loading = false, unlocked = true)
                else it.copy(loading = false, errorMessage = "Неверный код")
            }
        }
    }
}
