package com.teo.core.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Timezone-aware "yyyy-MM-dd" key used to bucket usage/limits per calendar day. */
object DayBoundary {
    private val FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE

    fun todayKey(timezone: String): String =
        LocalDate.now(zoneOf(timezone)).format(FORMATTER)

    fun isSameDay(dateKey: String, timezone: String): Boolean =
        dateKey == todayKey(timezone)

    private fun zoneOf(timezone: String): ZoneId =
        runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
}
