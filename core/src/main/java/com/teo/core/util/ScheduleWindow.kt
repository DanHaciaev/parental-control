package com.teo.core.util

/** Shared wraparound-aware time-window match, used by both the child's enforcement loop and the parent's "active now" indicator. */
object ScheduleWindow {
    fun contains(startMinutes: Int, endMinutes: Int, nowMinutes: Int): Boolean =
        if (startMinutes <= endMinutes) nowMinutes in startMinutes until endMinutes
        else nowMinutes >= startMinutes || nowMinutes < endMinutes

    /** Empty list defensively treated as "every day" (matches Schedule.daysOfWeek's own default)
     *  rather than "never" — a schedule with a corrupted/cleared day list should fail open to its
     *  old always-on behavior, not silently stop enforcing anything. [isoDayOfWeek] is 1=Monday..7=Sunday.
     *  Doesn't attempt to special-case a wraparound window (e.g. 22:00->07:00) that spans midnight
     *  into a day not in the list — evaluated against "today" as a simplification. */
    fun matchesDay(daysOfWeek: List<Int>, isoDayOfWeek: Int): Boolean =
        daysOfWeek.isEmpty() || isoDayOfWeek in daysOfWeek
}
