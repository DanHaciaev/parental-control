package com.teo.core.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class UsageEntry(
    val dateKey: String = "",
    val packageName: String = "",
    val minutesUsedToday: Int = 0,
    @ServerTimestamp val lastUpdated: Date? = null,
    val limitReachedNotified: Boolean = false
)
