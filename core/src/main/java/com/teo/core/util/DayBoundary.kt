package com.teo.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Timezone-aware "yyyy-MM-dd" key used to bucket usage/limits per calendar day. */
object DayBoundary {
    private val FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    private const val CHECK_INTERVAL_MS = 60_000L

    fun todayKey(timezone: String): String =
        LocalDate.now(zoneOf(timezone)).format(FORMATTER)

    /** Emits today's key immediately, then again whenever the calendar day rolls over. Use this
     *  instead of calling [todayKey] once and holding onto the result — a value grabbed at flow-
     *  build time and reused for the life of a long-lived ViewModel/service scope goes stale the
     *  moment midnight passes without the process restarting, silently freezing any "today" query
     *  built from it on whatever day it happened to start. */
    fun dateKeyFlow(timezone: String): Flow<String> = flow {
        while (true) {
            emit(todayKey(timezone))
            delay(CHECK_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    fun isSameDay(dateKey: String, timezone: String): Boolean =
        dateKey == todayKey(timezone)

    /** Local hour of day (0-23), used to bucket usage for the statistics hourly bar chart. */
    fun currentHour(timezone: String): Int =
        LocalDateTime.now(zoneOf(timezone)).hour

    /** ISO day-of-week: 1=Monday..7=Sunday — matches the key convention used by
     *  [com.teo.core.model.AppRule.weeklyLimitMinutes]. */
    fun isoDayOfWeek(timezone: String): Int =
        LocalDate.now(zoneOf(timezone)).dayOfWeek.value

    /** Monday of the current week through today. */
    fun thisWeekRange(timezone: String): Pair<String, String> {
        val today = LocalDate.now(zoneOf(timezone))
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return monday.format(FORMATTER) to today.format(FORMATTER)
    }

    /** Monday through Sunday of the previous calendar week. */
    fun lastWeekRange(timezone: String): Pair<String, String> {
        val today = LocalDate.now(zoneOf(timezone))
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val lastMonday = thisMonday.minusWeeks(1)
        val lastSunday = thisMonday.minusDays(1)
        return lastMonday.format(FORMATTER) to lastSunday.format(FORMATTER)
    }

    /** The 1st of the current month through today. */
    fun thisMonthRange(timezone: String): Pair<String, String> {
        val today = LocalDate.now(zoneOf(timezone))
        return today.withDayOfMonth(1).format(FORMATTER) to today.format(FORMATTER)
    }

    /** The 1st through the last day of the previous calendar month. */
    fun lastMonthRange(timezone: String): Pair<String, String> {
        val firstOfThisMonth = LocalDate.now(zoneOf(timezone)).withDayOfMonth(1)
        val lastMonthEnd = firstOfThisMonth.minusDays(1)
        val lastMonthStart = lastMonthEnd.withDayOfMonth(1)
        return lastMonthStart.format(FORMATTER) to lastMonthEnd.format(FORMATTER)
    }

    private fun zoneOf(timezone: String): ZoneId =
        runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
}
