package com.fitpal.app.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.fitpal.app.ui.theme.AccentActivity
import com.fitpal.app.ui.theme.FastingColor
import com.fitpal.app.ui.theme.CaffeineColor
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.DessertColor
import com.fitpal.app.ui.theme.GoldLight

/**
 * One settings category: a sub-screen opened from the hub ([SettingsCategoryScreen]). The [id] is
 * part of the navigation route — `fasting` and `caffeine` are deep-linked from home-screen widgets,
 * so ids are never renamed, only added.
 */
data class SettingsCategoryInfo(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tint: Color = GoldLight
)

/** A titled group of categories on the hub. */
data class SettingsGroup(val title: String, val categories: List<SettingsCategoryInfo>)

/** Pseudo-ids for hub rows that open their own screen instead of a settings category. */
const val SETTINGS_WIDGETS = "widgets"
const val SETTINGS_FEEDBACK = "feedback"

private val FEATURES get() = SettingsCategoryInfo("features", "Features", "Fasting, caffeine, food limits, the Trail — on or off", Icons.Default.ToggleOn)
private val PROFILE get() = SettingsCategoryInfo("profile", "Profile & goals", "You, your goal, calorie and macro targets", Icons.Default.Person)
private val LOGGING get() = SettingsCategoryInfo("presets", "Logging & meal times", "Quick-add amounts and when each meal counts", Icons.Default.Restaurant)
private val FASTING get() = SettingsCategoryInfo("fasting", "Fasting", "Your eating window, the timer and how strict it is", Icons.Default.HourglassEmpty, FastingColor)
private val LIMITS get() = SettingsCategoryInfo("rules", "Food limits", "Daily caps for dessert, fried food and sugary drinks", Icons.Default.Icecream, DessertColor)
private val CAFFEINE get() = SettingsCategoryInfo("caffeine", "Caffeine", "What's still in your body and when it wears off", Icons.Default.Coffee, CaffeineColor)
private val REMINDERS get() = SettingsCategoryInfo("reminders", "Reminders", "Meal and weigh-in nudges, and your AI overviews", Icons.Default.Notifications)
private val ACTIVITY get() = SettingsCategoryInfo("activity", "Activity & devices", "Steps, Samsung Health, your watch and Calistapp", Icons.Default.FitnessCenter, AccentActivity)
private val AI get() = SettingsCategoryInfo("ai", "AI models", "Your Gemini key, online models and the offline model", Icons.Default.AutoAwesome)
private val APPEARANCE get() = SettingsCategoryInfo("appearance", "Appearance", "Looks, colours, light or dark, and the app icon", Icons.Default.Palette)
private val SCREENS get() = SettingsCategoryInfo("personalize", "Home & screens", "Arrange and resize cards, the bottom bar, presets", Icons.Default.Dashboard, CreamMuted)
private val WIDGETS get() = SettingsCategoryInfo(SETTINGS_WIDGETS, "Home-screen widgets", "Calories, water, fasting and more at a glance", Icons.Default.Widgets, CreamMuted)
private val DATA get() = SettingsCategoryInfo("data", "Data & backup", "Export, import or clear your data", Icons.Default.Backup, CreamMuted)
private val ABOUT get() = SettingsCategoryInfo("about", "Updates & about", "App updates, version, and the intro again", Icons.Default.Info, CreamMuted)

/** The hub, top to bottom. A getter, so the rows' tints follow the current look. */
val SETTINGS_GROUPS get() = listOf(
    SettingsGroup("Features", listOf(FEATURES)),
    SettingsGroup("You", listOf(PROFILE)),
    SettingsGroup("Tracking", listOf(LOGGING, FASTING, LIMITS, CAFFEINE)),
    SettingsGroup("Notifications", listOf(REMINDERS)),
    SettingsGroup("Connections & AI", listOf(ACTIVITY, AI)),
    SettingsGroup("App", listOf(APPEARANCE, SCREENS, WIDGETS, DATA, ABOUT))
)

/** The real settings categories (the widget gallery row on the hub is its own screen). */
val SETTINGS_CATEGORIES: List<SettingsCategoryInfo> get() =
    SETTINGS_GROUPS.flatMap { it.categories }.filter { it.id != SETTINGS_WIDGETS }

fun settingsCategoryTitle(id: String): String =
    SETTINGS_CATEGORIES.firstOrNull { it.id == id }?.title ?: "Settings"

/**
 * One searchable setting. Tapping it opens [category] scrolled to the section [anchor] (the ids the
 * category screen wraps its sections with); [keywords] are the words someone might type for it.
 */
data class SettingsSearchEntry(
    val title: String,
    val category: String,
    val anchor: String?,
    val keywords: String = ""
) {
    /** Where it lives, for the result's second line ("Reminders"). */
    val where: String
        get() = when (category) {
            SETTINGS_WIDGETS -> "Home & screens"
            SETTINGS_FEEDBACK -> "Settings"
            else -> settingsCategoryTitle(category)
        }
}

val SETTINGS_SEARCH_INDEX = listOf(
    // Features
    SettingsSearchEntry("Features", "features", "features", "features turn on off enable disable optional fasting caffeine food limits trail garden"),
    SettingsSearchEntry("The Trail", "features", "features", "trail garden challenges streak grow turn off hide"),
    SettingsSearchEntry("Turn on fasting", "features", "features", "fasting turn on intermittent eating window"),
    SettingsSearchEntry("Turn on caffeine", "features", "features", "caffeine turn on coffee tracker"),
    SettingsSearchEntry("Turn on food limits", "features", "features", "food limits turn on dessert fried sugary drinks cap"),
    // Profile & goals
    SettingsSearchEntry("Your profile", "profile", "profile", "sex age height weight current weight fitness goal lose gain maintain muscle"),
    SettingsSearchEntry("Daily calorie goal", "profile", "calorie_goal", "calorie target override budget kcal limit"),
    SettingsSearchEntry("Macro targets", "profile", "macros", "protein fat carbs carbohydrates fibre fiber target grams"),
    SettingsSearchEntry("Your food profile", "profile", "food-profile", "food profile allergies allergy intolerance diet vegetarian vegan pescatarian keto halal kosher cooking skill kitchen cuisines budget won't eat love meal planner"),
    SettingsSearchEntry("AI coach and meal planner", "features", "features", "coach meal planner recipes plan week day turn on off reviews overview"),
    SettingsSearchEntry("Your day", "profile", "your_day", "wake up waking bedtime bed time sleep morning night day starts ends pace"),
    SettingsSearchEntry("About your situation", "profile", "situation", "personal context limitations allergies vegetarian vegan halal budget parents school work coach ai overview"),
    // Logging & meal times
    SettingsSearchEntry("Quick-add amounts", "presets", "presets", "meal presets drink presets portion serving grams ml quick add"),
    SettingsSearchEntry("Step goal", "activity", "steps_goal", "step goal daily steps walking target automatic average"),
    SettingsSearchEntry("Water goal", "presets", "water", "water goal hydration drink daily ml litres liters quick add water buttons"),
    SettingsSearchEntry("Meal times", "presets", "meal_times", "breakfast lunch dinner snack time window"),
    // Fasting
    SettingsSearchEntry("Intermittent fasting", "fasting", "fasting", "fast eating window 16:8 18:6 omad timer ring bar intensity intense strict warn notify notification drinks drink coffee black coffee tea water diet soda zero calorie protein shake break"),
    // Food limits
    SettingsSearchEntry("Food limits", "rules", "rules", "dessert sweets fried fast food sugary drinks soda cola daily limit cap rules warn"),
    // Caffeine
    SettingsSearchEntry("Caffeine tracker", "caffeine", "caffeine", "coffee tea energy drink half-life half life sleep mg limit"),
    // Reminders
    SettingsSearchEntry("Daily reminder", "reminders", "reminder_daily", "log your day nudge notification"),
    SettingsSearchEntry("Meal reminders", "reminders", "reminder_meals", "breakfast lunch dinner notification nudge"),
    SettingsSearchEntry("Weigh-in reminder", "reminders", "reminder_meals", "weight weigh in scale days weekdays monday notification"),
    SettingsSearchEntry("AI overview reminders", "reminders", "reminder_overviews", "daily overview weekly overview coach review sunday notification"),
    SettingsSearchEntry("Background reliability", "reminders", "reliability", "alarms exact alarm battery optimisation optimization doze reminders not firing stopped"),
    // Activity & devices
    SettingsSearchEntry("Step calories", "activity", "steps", "steps trim tracker over count burned"),
    SettingsSearchEntry("Samsung Health", "activity", "health", "health connect steps sync connect"),
    SettingsSearchEntry("Wear OS watch", "activity", "watch", "galaxy watch wear reconnect sync tile"),
    SettingsSearchEntry("Sync with Calistapp", "activity", "calistapp", "calistapp workouts exercise steps"),
    // AI
    SettingsSearchEntry("Online AI", "ai", "online_ai", "gemini api key model fallback test connection quota quick check models flash lite"),
    SettingsSearchEntry("On-device AI model", "ai", "ondevice", "offline gemma download model european foods open food facts food database import"),
    // Appearance
    SettingsSearchEntry("Look", "appearance", "look", "theme style glow liquid glass paper material you dark light mode night colours colors palette ocean forest berry aurora graphite ember wallpaper dynamic"),
    SettingsSearchEntry("App icon", "appearance", "app_icon", "icon launcher apple carrot sprout ring logo home screen icon change"),
    SettingsSearchEntry("Fine-tune the look", "appearance", "look_tuning", "glow grain texture opacity transparency corners rounding compact spacing density reduce motion animation"),
    // Home & screens
    SettingsSearchEntry("Trackers on Home", "personalize", "trackers", "strip today strip trackers home cards caffeine on home fasting ring fasting bar drawer water steps weight food limits dessert fried drinks placement small card bigger rings numbers"),
    SettingsSearchEntry("Analytics summary tiles", "personalize", "customize", "summary tiles analytics average calories steps weight change logged score cards sections energy nutrition body habits"),
    SettingsSearchEntry("Customize screens", "personalize", "customize", "reorder cards show hide home analytics add food layout size small medium large style widgets arrange"),
    SettingsSearchEntry("Bottom bar", "personalize", "nav_bar", "tabs navigation nav bar tab bar labels plus button camera describe docked floating trail"),
    SettingsSearchEntry("Layout presets", "personalize", "layout_presets", "preset minimal quick logger data nerd coach original layout reset"),
    SettingsSearchEntry("Compact empty meals", "personalize", "home_display", "home empty meals slim"),
    SettingsSearchEntry("Home-screen widgets", SETTINGS_WIDGETS, null, "widget launcher home screen glance"),
    // Data & backup
    SettingsSearchEntry("Back up and restore", "data", "backup", "export import backup restore json file"),
    SettingsSearchEntry("Clear all data", "data", "backup", "delete reset erase wipe"),
    SettingsSearchEntry("Hidden foods", "data", "backup", "restore hidden foods search"),
    // Updates & about
    SettingsSearchEntry("App updates", "about", "updates", "update version install github watch app"),
    SettingsSearchEntry("Replay the intro", "about", "about", "onboarding welcome tutorial intro version about"),
    SettingsSearchEntry("Send feedback", SETTINGS_FEEDBACK, null, "feedback bug idea email contact developer")
)

/**
 * Settings matching [query], best first: every word typed must appear in the entry's title, its
 * keywords or its category name. Title matches (especially at the start) rank above keyword ones.
 */
fun searchSettings(query: String): List<SettingsSearchEntry> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return SETTINGS_SEARCH_INDEX.mapNotNull { entry ->
        val title = entry.title.lowercase()
        val haystack = "$title ${entry.keywords.lowercase()} ${entry.where.lowercase()}"
        if (!words.all { haystack.contains(it) }) return@mapNotNull null
        var score = 0
        words.forEach { w ->
            score += when {
                title.startsWith(w) -> 3
                title.split(' ', '-').any { it.startsWith(w) } -> 2
                title.contains(w) -> 1
                else -> 0
            }
        }
        Pair(entry, score)
    }.sortedByDescending { it.second }.map { it.first }
}
