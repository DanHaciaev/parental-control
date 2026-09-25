package com.teo.core.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/** A SkillTemplate the parent has turned on for their child, with the target/reward they chose —
 *  doc id equals the template id (natural key, like rules/{packageName}). */
data class AssignedSkill(
    @DocumentId val id: String = "",
    val templateId: String = "",
    val title: String = "",
    val emoji: String = "",
    val category: String = "",
    val unit: String = SkillUnit.MINUTES.name,
    val targetPerDay: Int = 0,
    val rewardMinutes: Int = 0,
    val rewardPackageName: String? = null,
    @ServerTimestamp val updatedAt: Date? = null
)
