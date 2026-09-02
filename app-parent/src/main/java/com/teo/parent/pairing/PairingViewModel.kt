package com.teo.parent.pairing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairingUiState(
    val code: String? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val childPaired: Boolean = false
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val familyRepository: FamilyRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"])

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        generateCode()
        viewModelScope.launch {
            while (true) {
                try {
                    familyRepository.observeFamily(familyId).collect { family ->
                        if (!family?.childUid.isNullOrEmpty()) {
                            _uiState.update { it.copy(childPaired = true) }
                        }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun generateCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
                val code = pairingRepository.generateCode(familyId)
                _uiState.update { it.copy(loading = false, code = code) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, errorMessage = "Не удалось создать код, попробуйте ещё раз") }
            }
        }
    }
}
