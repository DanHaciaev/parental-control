package com.teo.parent.requests

import androidx.annotation.DrawableRes
import com.teo.core.R as CoreR
import com.teo.parent.R

/** Maps SkillTemplateCatalog ids to the bundled Twemoji-derived drawable — see EmojiIcon.kt for why. */
object SkillIconRegistry {
    private val mapping: Map<String, Int> = mapOf(
        "logic" to CoreR.drawable.nest_ic_puzzle,
        "math" to CoreR.drawable.nest_ic_hash,
        "reading" to CoreR.drawable.nest_ic_book_open,
        "english" to CoreR.drawable.nest_ic_case_sensitive,
        "writing" to CoreR.drawable.nest_ic_pen_line,
        "music" to CoreR.drawable.nest_ic_music,
        "squats" to CoreR.drawable.nest_ic_dumbbell,
        "jumps" to CoreR.drawable.nest_ic_activity,
        "pushups" to CoreR.drawable.nest_ic_biceps_flexed,
        "situps" to CoreR.drawable.nest_ic_flame,
        "stretching" to CoreR.drawable.nest_ic_flower,
        "steps" to CoreR.drawable.nest_ic_footprints,
        "walk" to CoreR.drawable.nest_ic_tree_pine,
        "clean_room" to CoreR.drawable.nest_ic_brush,
        "dishes" to CoreR.drawable.nest_ic_utensils,
        "make_bed" to CoreR.drawable.nest_ic_bed_double,
        "trash" to CoreR.drawable.nest_ic_trash_2,
        "water_plants" to CoreR.drawable.nest_ic_sprout,
        "cooking_help" to CoreR.drawable.nest_ic_chef_hat,
        "feed_pet" to CoreR.drawable.nest_ic_paw_print
    )

    @DrawableRes
    fun forTemplateId(id: String): Int = mapping[id] ?: CoreR.drawable.nest_ic_square_check
}
