package com.teo.parent.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.teo.core.repository.EventRepository
import com.teo.core.repository.FamilyRepository
import com.teo.core.repository.HourlyUsageRepository
import com.teo.core.repository.UsageRepository
import com.teo.core.util.DayBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import javax.inject.Inject

enum class StatsPreset { TODAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH }

data class AppUsageRow(
    val packageName: String,
    val appLabel: String,
    val minutesUsedToday: Int
)

data class StatisticsUiState(
    val loading: Boolean = true,
    val rangeLabel: String = "Сегодня",
    /** Drives which chart renders: the 24-hour bar for a single day, or a per-day bar across the range. */
    val isSingleDay: Boolean = true,
    val totalMinutesToday: Int = 0,
    val hourlyMinutes: List<Int> = List(24) { 0 },
    val currentHour: Int = 0,
    val dailyTotals: List<Pair<String, Int>> = emptyList(),
    val appBreakdown: List<AppUsageRow> = emptyList()
)

private sealed class Selection {
    data object Today : Selection()
    data class Fixed(val fromKey: String, val toKey: String, val label: String) : Selection()
}

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyRepository: FamilyRepository,
    private val usageRepository: UsageRepository,
    private val hourlyUsageRepository: HourlyUsageRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private var timezone: String = TimeZone.getDefault().id
    private val selection = MutableStateFlow<Selection>(Selection.Today)

    init {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val familyId = try {
                familyRepository.findFamilyIdForParent(uid) ?: return@launch
            } catch (e: Exception) {
                return@launch
            }
            timezone = runCatching { familyRepository.getFamily(familyId)?.timezone }
                .getOrNull() ?: TimeZone.getDefault().id

            // Retries on failure (e.g. a transient PERMISSION_DENIED right after cold start,
            // before the auth token is fully attached) instead of letting the listener crash the app.
            while (true) {
                try {
                    selection.flatMapLatest { sel ->
                        when (sel) {
                            // Tracks day rollover live via DayBoundary.dateKeyFlow instead of pinning
                            // a single todayKey — otherwise "Сегодня" would freeze on whatever day the
                            // screen was first opened for as long as the parent app stays alive past
                            // midnight.
                            is Selection.Today -> DayBoundary.dateKeyFlow(timezone).flatMapLatest { todayKey ->
                                singleDayFlow(familyId, todayKey, "Сегодня")
                            }
                            is Selection.Fixed -> if (sel.fromKey == sel.toKey) {
                                singleDayFlow(familyId, sel.fromKey, sel.label)
                            } else {
                                rangeFlow(familyId, sel.fromKey, sel.toKey, sel.label)
                            }
                        }
                    }.collect { state -> _uiState.update { state } }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }

    fun selectPreset(preset: StatsPreset) {
        if (preset == StatsPreset.TODAY) {
            selection.value = Selection.Today
            return
        }
        val (from, to, label) = when (preset) {
            StatsPreset.THIS_WEEK -> DayBoundary.thisWeekRange(timezone).let { (f, t) -> Triple(f, t, "Эта неделя") }
            StatsPreset.LAST_WEEK -> DayBoundary.lastWeekRange(timezone).let { (f, t) -> Triple(f, t, "Прошлая неделя") }
            StatsPreset.THIS_MONTH -> DayBoundary.thisMonthRange(timezone).let { (f, t) -> Triple(f, t, "Этот месяц") }
            StatsPreset.LAST_MONTH -> DayBoundary.lastMonthRange(timezone).let { (f, t) -> Triple(f, t, "Прошлый месяц") }
            StatsPreset.TODAY -> return
        }
        selection.value = Selection.Fixed(from, to, label)
    }

    fun selectCustomRange(from: LocalDate, to: LocalDate) {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        selection.value = Selection.Fixed(from.format(formatter), to.format(formatter), "Свой период")
    }

    /** Live-updating single-day view (today or a specific past day) — reuses the realtime hourly
     *  breakdown, so "Сегодня" keeps ticking up as usage accumulates. */
    private fun singleDayFlow(familyId: String, dateKey: String, label: String): Flow<StatisticsUiState> =
        combine(
            usageRepository.observeUsageForDay(familyId, dateKey),
            hourlyUsageRepository.observeHourlyUsageForDay(familyId, dateKey),
            eventRepository.observeInstalledApps(familyId)
        ) { usage, hourly, apps ->
            val labelByPackage = apps.associate { it.packageName to it.appLabel }
            val breakdown = usage
                .filter { it.minutesUsedToday > 0 }
                .map { entry ->
                    AppUsageRow(
                        packageName = entry.packageName,
                        appLabel = labelByPackage[entry.packageName] ?: entry.packageName,
                        minutesUsedToday = entry.minutesUsedToday
                    )
                }
                .sortedByDescending { it.minutesUsedToday }
            val hourlyMinutes = MutableList(24) { 0 }
            hourly.forEach { entry ->
                if (entry.hour in 0..23) hourlyMinutes[entry.hour] = entry.minutesUsed
            }
            StatisticsUiState(
                loading = false,
                rangeLabel = label,
                isSingleDay = true,
                totalMinutesToday = breakdown.sumOf { it.minutesUsedToday },
                hourlyMinutes = hourlyMinutes,
                currentHour = DayBoundary.currentHour(timezone),
                dailyTotals = emptyList(),
                appBreakdown = breakdown
            )
        }

    /** Multi-day view — a one-shot fetch via the existing UsageRepository.getUsageForRange (past
     *  days' usage doesn't change once written, so no realtime listener is needed here). */
    private fun rangeFlow(familyId: String, fromKey: String, toKey: String, label: String): Flow<StatisticsUiState> =
        combine(
            flow { emit(usageRepository.getUsageForRange(familyId, fromKey, toKey)) },
            eventRepository.observeInstalledApps(familyId)
        ) { usage, apps ->
            val labelByPackage = apps.associate { it.packageName to it.appLabel }
            val breakdown = usage
                .groupBy { it.packageName }
                .map { (pkg, entries) ->
                    AppUsageRow(
                        packageName = pkg,
                        appLabel = labelByPackage[pkg] ?: pkg,
                        minutesUsedToday = entries.sumOf { it.minutesUsedToday }
                    )
                }
                .filter { it.minutesUsedToday > 0 }
                .sortedByDescending { it.minutesUsedToday }
            val dailyTotals = usage
                .groupBy { it.dateKey }
                .map { (day, entries) -> day to entries.sumOf { it.minutesUsedToday } }
                .sortedBy { it.first }
            StatisticsUiState(
                loading = false,
                rangeLabel = label,
                isSingleDay = false,
                totalMinutesToday = breakdown.sumOf { it.minutesUsedToday },
                hourlyMinutes = List(24) { 0 },
                currentHour = DayBoundary.currentHour(timezone),
                dailyTotals = dailyTotals,
                appBreakdown = breakdown
            )
        }
}
