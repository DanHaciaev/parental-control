package com.teo.core.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/** One day's progress toward an AssignedSkill's quota — doc id is "{dateKey}_{skillId}", same
 *  bucketing convention as UsageEntry, so a new day's progress starts at 0 automatically with no
 *  explicit reset job needed. [approved] is only ever set true by the parent (see SkillRepository/
 *  firestore.rules) — reaching [progress] >= target on its own does not grant the reward. */
data class SkillProgressEntry(
    val dateKey: String = "",
    val skillId: String = "",
    val progress: Int = 0,
    val approved: Boolean = false,
    @ServerTimestamp val lastUpdated: Date? = null
)
