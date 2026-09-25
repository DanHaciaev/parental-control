package com.teo.parent.requests

import androidx.annotation.DrawableRes
import com.teo.parent.R

/** Maps SkillTemplateCatalog ids to the bundled Twemoji-derived drawable — see EmojiIcon.kt for why. */
object SkillIconRegistry {
    private val mapping: Map<String, Int> = mapOf(
        "logic" to R.drawable.ic_emoji_puzzle,
        "math" to R.drawable.ic_emoji_numbers,
        "reading" to R.drawable.ic_emoji_book,
        "english" to R.drawable.ic_emoji_abc,
        "writing" to R.drawable.ic_emoji_writing_hand,
        "music" to R.drawable.ic_emoji_music_note,
        "squats" to R.drawable.ic_emoji_weightlifter,
        "jumps" to R.drawable.ic_emoji_jumping,
        "pushups" to R.drawable.ic_emoji_flexed_biceps,
        "situps" to R.drawable.ic_emoji_fire,
        "stretching" to R.drawable.ic_emoji_lotus,
        "steps" to R.drawable.ic_emoji_walking,
        "walk" to R.drawable.ic_emoji_tree,
        "clean_room" to R.drawable.ic_emoji_broom,
        "dishes" to R.drawable.ic_emoji_plate,
        "make_bed" to R.drawable.ic_emoji_bed,
        "trash" to R.drawable.ic_emoji_trash,
        "water_plants" to R.drawable.ic_emoji_seedling,
        "cooking_help" to R.drawable.ic_emoji_cooking,
        "feed_pet" to R.drawable.ic_emoji_paw
    )

    @DrawableRes
    fun forTemplateId(id: String): Int = mapping[id] ?: R.drawable.ic_emoji_check
}
