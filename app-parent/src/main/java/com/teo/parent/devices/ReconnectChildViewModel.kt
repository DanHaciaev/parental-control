package com.teo.parent.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
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

data class ReconnectChildUiState(
    val code: String? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val childPaired: Boolean = false,
    /** Resetting the old pairing is destructive (unlinks whatever device currently holds the
     *  family's single childUid slot) — gated behind this explicit confirmation step so opening
     *  the screen alone can't accidentally kick out a phone that's still working fine. */
    val confirmed: Boolean = false
)

/** For when the child app is gone from its phone entirely (uninstalled) — [PairingViewModel]'s
 *  onboarding flow only ever runs once, right after the family is created, because the security
 *  rules only let a child claim a pairing code while childUid is still null (see
 *  PairingRepository.claimCode). A reinstalled child app has a brand-new anonymous auth uid (that's
 *  lost along with the rest of its local data on uninstall) and needs childUid freed up again
 *  before it can claim anything — only the parent can do that (see
 *  FamilyRepository.resetChildPairing), so this mirrors PairingScreen's generate-code-and-wait UI
 *  but reachable any time from the dashboard instead of only during onboarding. */
@HiltViewModel
class ReconnectChildViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val pairingRepository: PairingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReconnectChildUiState())
    val uiState: StateFlow<ReconnectChildUiState> = _uiState.asStateFlow()

    private var familyId: String? = null

    fun confirm() {
        if (_uiState.value.confirmed) return
        _uiState.update { it.copy(confirmed = true, loading = true) }
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: run {
                _uiState.update { it.copy(loading = false, errorMessage = "Не удалось определить семью") }
                return@launch
            }
            val id = runCatching { familyRepository.findFamilyIdForParent(uid) }.getOrNull() ?: run {
                _uiState.update { it.copy(loading = false, errorMessage = "Не удалось определить семью") }
                return@launch
            }
            familyId = id
            runCatching { familyRepository.resetChildPairing(id) }.onFailure {
                _uiState.update {
                    it.copy(loading = false, errorMessage = "Не удалось сбросить привязку, попробуйте ещё раз")
                }
                return@launch
            }
            generateCode()
            while (true) {
                try {
                    familyRepository.observeFamily(id).collect { family ->
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
        val id = familyId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
                val code = pairingRepository.generateCode(id)
                _uiState.update { it.copy(loading = false, code = code) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, errorMessage = "Не удалось создать код, попробуйте ещё раз") }
            }
        }
    }
}
