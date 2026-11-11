package com.teo.parent.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.teo.core.model.SecretType
import com.teo.core.repository.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class PinChallengeUiState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val unlocked: Boolean = false,
    /** Which input to show — the kind of code the parent chose. */
    val secretType: SecretType = SecretType.PIN,
    /** Unlocked with the account password because the code was forgotten — a new code must be
     *  set before going any further (see ParentNavHost). */
    val resetRequired: Boolean = false
)

@HiltViewModel
class PinChallengeViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinChallengeUiState())
    val uiState: StateFlow<PinChallengeUiState> = _uiState.asStateFlow()

    init {
        loadSecretType()
    }

    /** Re-arms the challenge — call when re-locking the app, since the ViewModel outlives this one screen visit. */
    fun lock() {
        val type = _uiState.value.secretType
        _uiState.value = PinChallengeUiState(secretType = type)
        loadSecretType()
    }

    fun verify(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, errorMessage = "Нет связи — проверьте интернет и попробуйте ещё раз")
                }
            }
        }
    }

    /** Fingerprint/face already passed in the system prompt — see BiometricUnlock. */
    fun unlockWithBiometric() {
        _uiState.update { it.copy(unlocked = true, errorMessage = null) }
    }

    /** "Забыли код?" — proves it's the account owner by re-entering the account password, then
     *  requires choosing a new code right away. */
    fun unlockWithAccountPassword(password: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
                user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
                _uiState.update { it.copy(loading = false, unlocked = true, resetRequired = true) }
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _uiState.update { it.copy(loading = false, errorMessage = "Неверный пароль от аккаунта") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, errorMessage = "Нет связи — проверьте интернет и попробуйте ещё раз")
                }
            }
        }
    }

    fun codeResetDone() {
        _uiState.update { it.copy(resetRequired = false) }
        loadSecretType()
    }

    private fun loadSecretType() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val family = runCatching {
                familyRepository.findFamilyIdForParent(uid)?.let { familyRepository.getFamily(it) }
            }.getOrNull() ?: return@launch
            _uiState.update { it.copy(secretType = SecretType.from(family.protectionSecretType)) }
        }
    }
}
