package com.citymemory.domain.model

/**
 * Achievement definitions.
 *
 * Nothing here is persisted. Every achievement is a pure function of the
 * current place list, evaluated on each emission — so unlocking is impossible
 * to miss, impossible to double-award, and survives any data change for free.
 */
enum class AchievementId {
    FIRST_EXPLORATION,
    EXPLORER,
    TOURIST,
    FOODIE,
    CITY_EXPLORER,
}

data class Achievement(
    val id: AchievementId,
    val title: String,
    val description: String,
    /** Progress toward [target] in the achievement's own unit. */
    val progress: Int,
    val target: Int,
) {
    val isUnlocked: Boolean get() = progress >= target

    val fraction: Float
        get() = if (target == 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
}
