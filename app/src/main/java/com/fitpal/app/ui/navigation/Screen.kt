package com.fitpal.app.ui.navigation

import android.net.Uri

/**
 * All screens in the app. Used for navigation routing.
 */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object AddFood : Screen("add_food")
    data object Camera : Screen("camera")
    data object Gallery : Screen("gallery")
    data object ManualEntry : Screen("manual_entry")
    data object CustomFood : Screen("custom_food?barcode={barcode}") {
        const val ARG_BARCODE = "barcode"
        /** Optionally pre-link the custom food to a scanned barcode (so the next scan finds it). */
        fun buildRoute(barcode: String? = null): String =
            if (barcode == null) "custom_food" else "custom_food?barcode=$barcode"
    }
    data object DescribeFood : Screen("describe_food")
    data object LogExercise : Screen("log_exercise")
    data object Barcode : Screen("barcode")
    data object Analytics : Screen("analytics")
    data object Collection : Screen("collection")
    data object Garden : Screen("garden")
    data object Settings : Screen("settings")
    data object ModelSetup : Screen("model_setup")

    /** Detail view for a logged meal entry, navigated from Home. */
    data object EntryDetail : Screen("entry_detail/{entryId}") {
        const val ARG_ENTRY_ID = "entryId"
        fun buildRoute(entryId: Long): String = "entry_detail/$entryId"
    }

    /** Multi-dish view of a whole logged meal (one photo/describe generation), from Home. */
    data object MealGroup : Screen("meal_group/{mealLogId}") {
        const val ARG_MEAL_LOG_ID = "mealLogId"
        fun buildRoute(mealLogId: Long): String = "meal_group/$mealLogId"
    }

    /** Reorder / show-hide the widgets of a given screen (Home, Analytics, …). */
    data object CustomizeWidgets : Screen("customize_widgets/{screenId}") {
        const val ARG_SCREEN_ID = "screenId"
        fun buildRoute(screenId: String): String = "customize_widgets/$screenId"
    }

    /** Water detail for a day: sources breakdown, entries, and editable quick-add presets. */
    data object WaterDetail : Screen("water_detail/{date}") {
        const val ARG_DATE = "date"
        fun buildRoute(date: String): String = "water_detail/$date"
    }

    /**
     * Calories in vs. out for a period, opened from the Analytics "Calorie balance" card.
     * range = WEEK/MONTH, anchor = the ISO date the period is built around (so it opens on
     * exactly the week/30 days Analytics was showing).
     */
    data object CalorieDetail : Screen("calorie_detail/{range}/{anchor}") {
        const val ARG_RANGE = "range"
        const val ARG_ANCHOR = "anchor"
        fun buildRoute(range: String, anchor: String): String = "calorie_detail/$range/$anchor"
    }

    /** Full detail of a saved (gallery) food — image, ingredients, AI analysis — with log/delete. */
    data object GalleryFoodDetail : Screen("gallery_food/{foodId}") {
        const val ARG_FOOD_ID = "foodId"
        fun buildRoute(foodId: Long): String = "gallery_food/$foodId"
    }

    /** Analysis of a logged exercise — breakdown, intensity, AI tips. */
    data object ExerciseDetail : Screen("exercise_detail/{id}") {
        const val ARG_ID = "id"
        fun buildRoute(id: Long): String = "exercise_detail/$id"
    }

    /** One category of settings opened from the Settings hub (Android-style sub-screen). */
    data object SettingsCategory : Screen("settings_category/{category}") {
        const val ARG_CATEGORY = "category"
        fun buildRoute(category: String): String = "settings_category/$category"
    }

    /**
     * A saved AI overview for a period. period = daily/weekly/monthly,
     * periodKey identifies which one (a date, a week-start date, or "YYYY-MM").
     */
    data object AiReview : Screen("ai_review/{period}/{periodKey}") {
        const val ARG_PERIOD = "period"
        const val ARG_PERIOD_KEY = "periodKey"
        fun buildRoute(period: String, periodKey: String): String = "ai_review/$period/$periodKey"
    }

    /**
     * Analysis screen takes an optional image URI to analyze.
     * Navigating with no URI (e.g. from the camera stub) is allowed.
     */
    data object Analysis : Screen("analysis?imageUri={imageUri}") {
        const val ARG_IMAGE_URI = "imageUri"

        /** Build the navigation route, optionally with an image to analyze. */
        fun buildRoute(imageUri: String? = null): String =
            if (imageUri == null) "analysis"
            else "analysis?imageUri=${Uri.encode(imageUri)}"
    }
}
