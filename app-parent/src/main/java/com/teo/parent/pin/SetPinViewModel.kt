package com.teo.parent.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.SecretType
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
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetPinUiState())
    val uiState: StateFlow<SetPinUiState> = _uiState.asStateFlow()

    /** The ViewModel can outlive one visit (activity-scoped when shown over the nav host), so a
     *  later code change must not see the previous one's "saved" and skip straight past. */
    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    fun savePin(secret: String, type: SecretType) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
                val uid = auth.currentUser?.uid ?: error("not signed in")
                familyRepository.setProtectionPin(uid, secret, type)
                _uiState.update { it.copy(loading = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, errorMessage = "Не удалось сохранить код, попробуйте ещё раз") }
            }
        }
    }
}
