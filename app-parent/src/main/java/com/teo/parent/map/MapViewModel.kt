package com.teo.parent.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.DeviceStatus
import com.teo.core.model.LocationPoint
import com.teo.core.repository.DeviceStatusRepository
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val loading: Boolean = true,
    val location: LocationPoint? = null,
    val deviceStatus: DeviceStatus? = null,
    val childName: String? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val locationRepository: LocationRepository,
    private val deviceStatusRepository: DeviceStatusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            launch {
                while (true) {
                    try {
                        locationRepository.observeLocation(familyId).collect { point ->
                            _uiState.update { it.copy(loading = false, location = point) }
                        }
                    } catch (e: Exception) {
                        delay(2000)
                    }
                }
            }
            launch {
                while (true) {
                    try {
                        deviceStatusRepository.observeStatus(familyId).collect { status ->
                            _uiState.update { it.copy(deviceStatus = status) }
                        }
                    } catch (e: Exception) {
                        delay(2000)
                    }
                }
            }
            launch {
                while (true) {
                    try {
                        familyRepository.observeFamily(familyId).collect { family ->
                            _uiState.update { it.copy(childName = family?.childName?.takeIf { name -> name.isNotBlank() }) }
                        }
                    } catch (e: Exception) {
                        delay(2000)
                    }
                }
            }
        }
    }
}
