package com.teo.parent.weeklylimits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.model.Family
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.RuleRepository
import com.teo.parent.dashboard.AppRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeeklyLimitsUiState(
    val loading: Boolean = true,
    val familyId: String? = null,
    val apps: List<AppRow> = emptyList(),
    val family: Family? = null
)

@HiltViewModel
class WeeklyLimitsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository,
    private val ruleRepository: RuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyLimitsUiState())
    val uiState: StateFlow<WeeklyLimitsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            _uiState.update { it.copy(familyId = familyId) }

            // Retries on failure (e.g. a transient PERMISSION_DENIED right after cold start,
            // before the auth token is fully attached) instead of letting the listener crash the app.
            while (true) {
                try {
                    combine(
                        eventRepository.observeInstalledApps(familyId),
                        ruleRepository.observeRules(familyId),
                        familyRepository.observeFamily(familyId)
                    ) { apps, rules, family ->
                        val ruleByPackage = rules.associateBy { it.packageName }
                        val rows = apps
                            .map { app ->
                                AppRow(
                                    packageName = app.packageName,
                                    appLabel = app.appLabel,
                                    rule = ruleByPackage[app.packageName]
                                )
                            }
                            .sortedBy { it.appLabel.lowercase() }
                        rows to family
                    }.collect { (rows, family) ->
                        _uiState.update { it.copy(loading = false, apps = rows, family = family) }
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    /** [isoDayOfWeek] is 1=Monday..7=Sunday. The list (observed via observeRules) refreshes itself
     *  once Firestore echoes the write back — no manual state patch needed here. */
    fun setDayLimit(app: AppRow, isoDayOfWeek: Int, minutes: Int) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch {
            runCatching { ruleRepository.setDayLimit(familyId, app.packageName, app.appLabel, isoDayOfWeek, minutes) }
        }
    }

    fun clearDayLimit(app: AppRow, isoDayOfWeek: Int) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { ruleRepository.clearDayLimit(familyId, app.packageName, isoDayOfWeek) } }
    }

    fun setDayTotalCap(isoDayOfWeek: Int, minutes: Int) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { familyRepository.setDayTotalCap(familyId, isoDayOfWeek, minutes) } }
    }

    fun clearDayTotalCap(isoDayOfWeek: Int) {
        val familyId = _uiState.value.familyId ?: return
        viewModelScope.launch { runCatching { familyRepository.clearDayTotalCap(familyId, isoDayOfWeek) } }
    }
}
