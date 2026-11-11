package com.teo.child.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teo.child.data.CachedProtectionCode
import com.teo.child.data.ChildPreferences
import com.teo.child.monitor.AirplaneModeGuard
import com.teo.core.model.SecretType
import com.teo.core.repository.FamilyRepository
import com.teo.core.util.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PinChallengeUiState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val unlocked: Boolean = false,
    /** Which input to show — the kind of code the parent chose. */
    val secretType: SecretType = SecretType.PIN
)

/**
 * Verifies against the family protection code the parent set in app-parent, via the copy cached
 * on this device (ChildPreferences.getCachedProtectionPin) — this has to work with zero network,
 * since the airplane-mode block is exactly when there is none. Only when nothing was cached yet
 * (fresh install, never online since) does it fall back to reading the family document.
 *
 * Wrong attempts are throttled: the code is entered on the child's own phone, so without a limit
 * a short PIN could simply be tried exhaustively by hand.
 */
@HiltViewModel
class PinChallengeViewModel @Inject constructor(
    private val airplaneModeGuard: AirplaneModeGuard,
    private val childPreferences: ChildPreferences,
    private val familyRepository: FamilyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinChallengeUiState())
    val uiState: StateFlow<PinChallengeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadCode()?.let { code -> _uiState.update { it.copy(secretType = code.type) } }
        }
    }

    /** Called once, right after a correct PIN unlocks the airplane-mode block — see
     *  AirplaneModeGuard's kdoc for why this is a one-shot grant rather than actually turning
     *  airplane mode off ourselves (no public API for that without Device Owner either). */
    fun markAirplaneModePinVerified() {
        airplaneModeGuard.markPinVerified()
    }

    fun verify(secret: String) {
        val now = System.currentTimeMillis()
        if (now < lockedUntilMs) {
            val seconds = (lockedUntilMs - now + 999) / 1000
            _uiState.update { it.copy(errorMessage = "Слишком много попыток. Подождите $seconds сек.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            val code = loadCode()
            if (code == null) {
                _uiState.update {
                    it.copy(loading = false, errorMessage = "Код ещё не загружен — подключите телефон к интернету и попробуйте снова")
                }
                return@launch
            }
            // PBKDF2 with a high iteration count — keep it off the main thread.
            val correct = withContext(Dispatchers.Default) { PinHasher.verify(secret, code.hash, code.salt) }
            if (correct) {
                failedAttempts = 0
                _uiState.update { it.copy(loading = false, unlocked = true) }
            } else {
                failedAttempts++
                if (failedAttempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
                    lockedUntilMs = System.currentTimeMillis() + LOCKOUT_MS
                    failedAttempts = 0
                }
                _uiState.update { it.copy(loading = false, errorMessage = "Неверный код") }
            }
        }
    }

    private suspend fun loadCode(): CachedProtectionCode? {
        childPreferences.getCachedProtectionPin()?.let { return it }
        val familyId = childPreferences.familyId.first() ?: return null
        val family = runCatching { familyRepository.getFamily(familyId) }.getOrNull() ?: return null
        if (family.protectionPinHash.isEmpty() || family.protectionPinSalt.isEmpty()) return null
        val type = SecretType.from(family.protectionSecretType)
        childPreferences.setCachedProtectionPin(family.protectionPinHash, family.protectionPinSalt, type)
        return CachedProtectionCode(family.protectionPinHash, family.protectionPinSalt, type)
    }

    private companion object {
        const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
        const val LOCKOUT_MS = 30_000L

        // Process-wide rather than per ViewModel: cancelling and reopening the challenge screen
        // would otherwise reset the counter and defeat the throttle.
        @Volatile var failedAttempts = 0
        @Volatile var lockedUntilMs = 0L
    }
}
