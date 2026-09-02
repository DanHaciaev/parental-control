package com.teo.child.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.child.data.ChildPreferences
import com.teo.core.repository.PairingException
import com.teo.core.repository.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ChildPairingUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val paired: Boolean = false
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val pairingRepository: PairingRepository,
    private val childPreferences: ChildPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChildPairingUiState())
    val uiState: StateFlow<ChildPairingUiState> = _uiState.asStateFlow()

    /** Persisted flag, not a live permission re-check — a later remote RELEASE_PROTECTION
     *  intentionally turns Device Admin back off and must not bounce the child into onboarding. */
    val onboardingComplete: Flow<Boolean> = childPreferences.onboardingComplete

    fun markOnboardingComplete() {
        viewModelScope.launch { childPreferences.setOnboardingComplete() }
    }

    init {
        viewModelScope.launch {
            val existingFamilyId = childPreferences.familyId.first()
            if (existingFamilyId != null) {
                _uiState.update { it.copy(loading = false, paired = true) }
            } else {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun submitCode(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
                val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
                val familyId = pairingRepository.claimCode(code.trim(), uid)
                childPreferences.setFamilyId(familyId)
                _uiState.update { it.copy(loading = false, paired = true) }
            } catch (e: PairingException) {
                _uiState.update { it.copy(loading = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, errorMessage = "Не удалось подключиться. Проверьте код и интернет")
                }
            }
        }
    }
}
