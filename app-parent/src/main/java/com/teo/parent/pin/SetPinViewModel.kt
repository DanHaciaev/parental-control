package com.teo.parent.pin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teo.core.repository.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetPinUiState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class SetPinViewModel @Inject constructor(
    private val familyRepository: FamilyRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"])

    private val _uiState = MutableStateFlow(SetPinUiState())
    val uiState: StateFlow<SetPinUiState> = _uiState.asStateFlow()

    fun savePin(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
                familyRepository.setProtectionPin(familyId, pin)
                _uiState.update { it.copy(loading = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, errorMessage = "Не удалось сохранить код, попробуйте ещё раз") }
            }
        }
    }
}
