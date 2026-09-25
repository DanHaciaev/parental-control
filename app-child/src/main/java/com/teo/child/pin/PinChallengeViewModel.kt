package com.teo.child.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teo.child.monitor.AirplaneModeGuard
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

/** Verifies against a fixed code, independent of the family protection PIN set in app-parent —
 *  deliberately not tied to the parent's own PIN, so this still works with zero network and
 *  without needing the parent's app at all (see ProtectionAccessibilityService, which launches
 *  this on a detected uninstall attempt against this app itself). */
@HiltViewModel
class PinChallengeViewModel @Inject constructor(
    private val airplaneModeGuard: AirplaneModeGuard
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinChallengeUiState())
    val uiState: StateFlow<PinChallengeUiState> = _uiState.asStateFlow()

    /** Called once, right after a correct PIN unlocks the airplane-mode block — see
     *  AirplaneModeGuard's kdoc for why this is a one-shot grant rather than actually turning
     *  airplane mode off ourselves (no public API for that without Device Owner either). */
    fun markAirplaneModePinVerified() {
        airplaneModeGuard.markPinVerified()
    }

    fun verify(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            val correct = pin == FIXED_CODE
            _uiState.update {
                if (correct) it.copy(loading = false, unlocked = true)
                else it.copy(loading = false, errorMessage = "Неверный код")
            }
        }
    }

    private companion object {
        const val FIXED_CODE = "Vantanan27"
    }
}
