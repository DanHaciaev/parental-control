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
    /** A photo of the actual exercise (e.g. photographed from a workbook) or a link to one —
     *  what the child is meant to solve, shown on their task card. [photoBase64] holds the
     *  compressed JPEG bytes inline (no Firebase Storage — that now requires a billing account),
     *  cleared once the task is approved/rejected (see TaskRepository) since it's only ever
     *  needed while the task is still open. */
    val photoBase64: String? = null,
    val linkUrl: String? = null,
    @ServerTimestamp val createdAt: Date? = null,
    val completedAt: Date? = null
)
