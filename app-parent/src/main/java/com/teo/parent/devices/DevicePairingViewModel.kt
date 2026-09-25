package com.teo.parent.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.repository.DeviceRepository
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicePairingUiState(
    val code: String? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val devicePaired: Boolean = false
)

/** Mirrors PairingViewModel's phone-pairing flow (same generateCode call, same poll-for-arrival
 *  shape) but watches the new `devices` subcollection for an arrival instead of the family doc's
 *  single childUid slot — see DeviceRepository/PairingRepository.claimCodeForDevice. */
@HiltViewModel
class DevicePairingViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val pairingRepository: PairingRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicePairingUiState())
    val uiState: StateFlow<DevicePairingUiState> = _uiState.asStateFlow()

    private var familyId: String? = null

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val id = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            familyId = id
            // Snapshot which devices already exist before generating a code, so only a device
            // that appears *after* this counts as "just paired" here.
            val knownDeviceIds = runCatching {
                deviceRepository.observeDevices(id).first().map { it.id }.toSet()
            }.getOrDefault(emptySet())
            generateCode()
            while (true) {
                try {
                    deviceRepository.observeDevices(id).collect { devices ->
                        if (devices.any { it.id !in knownDeviceIds }) {
                            _uiState.update { it.copy(devicePaired = true) }
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
