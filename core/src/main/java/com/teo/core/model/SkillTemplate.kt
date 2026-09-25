package com.teo.core.model

enum class SkillUnit { MINUTES, REPS, COUNT }

/** A catalog entry, not Firestore-backed — same list for every family, like AppIconRegistry.
 *  [id] is the natural key used as the AssignedSkill/SkillProgressEntry document id. */
data class SkillTemplate(
    val id: String,
    val title: String,
    val category: String,
    val emoji: String,
    val unit: SkillUnit,
    val defaultTargetPerDay: Int,
    val defaultRewardMinutes: Int
)

/** Ready-made daily quotas a parent can assign without typing a custom task — see AssignedSkill. */
object SkillTemplateCatalog {
    const val CATEGORY_SKILLS = "Навыки"
    const val CATEGORY_ACTIVITY = "Физическая активность"
    const val CATEGORY_CHORES = "Домашние дела"

    val all: List<SkillTemplate> = listOf(
        SkillTemplate("logic", "Логика", CATEGORY_SKILLS, "🧩", SkillUnit.MINUTES, 20, 15),
        SkillTemplate("math", "Математика", CATEGORY_SKILLS, "🔢", SkillUnit.MINUTES, 20, 15),
        SkillTemplate("reading", "Чтение", CATEGORY_SKILLS, "📖", SkillUnit.MINUTES, 20, 15),
        SkillTemplate("english", "Английский язык", CATEGORY_SKILLS, "🔤", SkillUnit.MINUTES, 15, 15),
        SkillTemplate("writing", "Письмо", CATEGORY_SKILLS, "✍️", SkillUnit.MINUTES, 15, 10),
        SkillTemplate("music", "Музыка", CATEGORY_SKILLS, "🎵", SkillUnit.MINUTES, 20, 15),

        SkillTemplate("squats", "Приседания", CATEGORY_ACTIVITY, "🏋️", SkillUnit.REPS, 20, 10),
        SkillTemplate("jumps", "Прыжки", CATEGORY_ACTIVITY, "🤸", SkillUnit.REPS, 30, 10),
        SkillTemplate("pushups", "Отжимания", CATEGORY_ACTIVITY, "💪", SkillUnit.REPS, 10, 10),
        SkillTemplate("situps", "Пресс", CATEGORY_ACTIVITY, "🔥", SkillUnit.REPS, 20, 10),
        SkillTemplate("stretching", "Растяжка", CATEGORY_ACTIVITY, "🧘", SkillUnit.MINUTES, 10, 10),
        SkillTemplate("steps", "Шаги", CATEGORY_ACTIVITY, "🚶", SkillUnit.COUNT, 3000, 15),
        SkillTemplate("walk", "Прогулка на улице", CATEGORY_ACTIVITY, "🌳", SkillUnit.MINUTES, 20, 15),

        SkillTemplate("clean_room", "Убрать комнату", CATEGORY_CHORES, "🧹", SkillUnit.COUNT, 1, 15),
        SkillTemplate("dishes", "Помыть посуду", CATEGORY_CHORES, "🍽️", SkillUnit.COUNT, 1, 10),
        SkillTemplate("make_bed", "Заправить кровать", CATEGORY_CHORES, "🛏️", SkillUnit.COUNT, 1, 5),
        SkillTemplate("trash", "Вынести мусор", CATEGORY_CHORES, "🗑️", SkillUnit.COUNT, 1, 5),
        SkillTemplate("water_plants", "Полить цветы", CATEGORY_CHORES, "🌱", SkillUnit.COUNT, 1, 5),
        SkillTemplate("cooking_help", "Помочь с готовкой", CATEGORY_CHORES, "👩‍🍳", SkillUnit.COUNT, 1, 10),
        SkillTemplate("feed_pet", "Покормить питомца", CATEGORY_CHORES, "🐾", SkillUnit.COUNT, 1, 5)
    )

    fun byId(id: String): SkillTemplate? = all.firstOrNull { it.id == id }
}
