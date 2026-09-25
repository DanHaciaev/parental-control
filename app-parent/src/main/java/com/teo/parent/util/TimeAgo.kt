package com.teo.parent.util

import java.util.Date
import java.util.concurrent.TimeUnit

fun timeAgo(date: Date?): String {
    if (date == null) return "неизвестно"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - date.time)
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 24 * 60 -> "${minutes / 60} ч назад"
        else -> "${minutes / (24 * 60)} дн назад"
    }
}
