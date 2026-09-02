package com.teo.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class TaskStatus { OPEN, DONE_BY_CHILD, APPROVED, REJECTED }

data class Task(
    @DocumentId val id: String = "",
    val title: String = "",
    val rewardMinutes: Int = 15,
    val rewardPackageName: String? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    @ServerTimestamp val createdAt: Date? = null,
    val completedAt: Date? = null
)
