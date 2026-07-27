package com.fitpal.app.domain.model

/**
 * The user's body stats — drives BMR calculation and goal-based recommendations.
 * Stored in SharedPreferences via SettingsRepository.
 */
data class UserProfile(
    val sex: Sex = Sex.MALE,
    val ageYears: Int = 25,
    val heightCm: Float = 175f,
    /** Goal drives the macro split and AI suggestions. */
    val fitnessGoal: FitnessGoal = FitnessGoal.RECOMP
)

enum class Sex(val label: String) {
    MALE("Male"),
    FEMALE("Female");

    companion object {
        fun fromString(s: String): Sex = entries.firstOrNull { it.name == s } ?: MALE
    }
}

/**
 * What the user is trying to achieve — each has a different calorie/macro strategy.
 */
enum class FitnessGoal(val label: String, val description: String) {
    LOSE_FAT("Lose fat", "Calorie deficit, high protein, moderate carbs"),
    RECOMP("Recomp", "Lose fat while building muscle — slight deficit, very high protein"),
    BUILD_MUSCLE("Build muscle", "Calorie surplus, high protein, high carbs"),
    MAINTAIN("Maintain", "Stay at current weight — eat at maintenance");

    companion object {
        fun fromString(s: String): FitnessGoal = entries.firstOrNull { it.name == s } ?: RECOMP
    }
}
