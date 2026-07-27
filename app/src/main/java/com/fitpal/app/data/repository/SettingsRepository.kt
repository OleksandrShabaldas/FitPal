package com.fitpal.app.data.repository

import android.content.Context
import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.domain.model.Sex
import com.fitpal.app.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple persisted app settings backed by SharedPreferences.
 * Exposes values as StateFlows so the UI updates immediately when they change.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("fitpal_settings", Context.MODE_PRIVATE)

    // --- Daily calorie goal (manual override — 0 = use BMR-derived target) ---

    private val _dailyCalorieGoal = MutableStateFlow(
        prefs.getInt(KEY_CALORIE_GOAL, 0)
    )
    val dailyCalorieGoal: StateFlow<Int> = _dailyCalorieGoal

    fun setDailyCalorieGoal(value: Int) {
        val clamped = value.coerceIn(0, 20000)
        prefs.edit().putInt(KEY_CALORIE_GOAL, clamped).apply()
        _dailyCalorieGoal.value = clamped
    }

    // --- Step-calorie trim (compensate for trackers that over-count steps) ---
    // Stored as a percentage to shave off the raw step-calorie estimate (0–50%).

    private val _stepCalorieReductionPercent = MutableStateFlow(
        prefs.getInt(KEY_STEP_CAL_REDUCTION, 15)
    )
    val stepCalorieReductionPercent: StateFlow<Int> = _stepCalorieReductionPercent

    fun setStepCalorieReductionPercent(value: Int) {
        val clamped = value.coerceIn(0, 50)
        prefs.edit().putInt(KEY_STEP_CAL_REDUCTION, clamped).apply()
        _stepCalorieReductionPercent.value = clamped
    }

    // --- User Profile (body stats + goal) ---

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile

    fun setUserProfile(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_SEX, profile.sex.name)
            .putInt(KEY_AGE, profile.ageYears)
            .putFloat(KEY_HEIGHT, profile.heightCm)
            .putString(KEY_FITNESS_GOAL, profile.fitnessGoal.name)
            .apply()
        _userProfile.value = profile
    }

    private fun loadProfile(): UserProfile = UserProfile(
        sex = Sex.fromString(prefs.getString(KEY_SEX, Sex.MALE.name) ?: Sex.MALE.name),
        ageYears = prefs.getInt(KEY_AGE, 25),
        heightCm = prefs.getFloat(KEY_HEIGHT, 175f),
        fitnessGoal = FitnessGoal.fromString(
            prefs.getString(KEY_FITNESS_GOAL, FitnessGoal.RECOMP.name) ?: FitnessGoal.RECOMP.name
        )
    )

    // --- Hugging Face token (used once to download the gated Gemma model) ---

    private val _hfToken = MutableStateFlow(prefs.getString(KEY_HF_TOKEN, null))
    val hfToken: StateFlow<String?> = _hfToken

    fun setHfToken(token: String) {
        val clean = token.trim()
        prefs.edit().putString(KEY_HF_TOKEN, clean).apply()
        _hfToken.value = clean.ifEmpty { null }
    }

    // --- Online AI (Gemini) — optional; falls back to the on-device model when off/unavailable ---

    private val _geminiApiKey = MutableStateFlow(prefs.getString(KEY_GEMINI_KEY, null))
    /** The user's own Gemini API key (pasted in Settings). Null/blank = online AI can't run. */
    val geminiApiKey: StateFlow<String?> = _geminiApiKey

    fun setGeminiApiKey(token: String) {
        val clean = token.trim()
        prefs.edit().putString(KEY_GEMINI_KEY, clean).apply()
        _geminiApiKey.value = clean.ifEmpty { null }
        // A fresh key deserves a fresh chance — clear any "out of quota" back-off.
        clearGeminiQuotaExhausted()
    }

    private val _onlineAiEnabled = MutableStateFlow(prefs.getBoolean(KEY_ONLINE_AI_ENABLED, true))
    /** Master switch for using the online model at all. On by default; the router still needs a key. */
    val onlineAiEnabled: StateFlow<Boolean> = _onlineAiEnabled

    fun setOnlineAiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_AI_ENABLED, enabled).apply()
        _onlineAiEnabled.value = enabled
    }

    // --- Personal context / limitations the AI overview should respect (free text) ---
    // e.g. "living with parents", "lunches at school", "vegetarian", "on a tight budget".

    private val _personalContext = MutableStateFlow(prefs.getString(KEY_PERSONAL_CONTEXT, "") ?: "")
    val personalContext: StateFlow<String> = _personalContext

    fun setPersonalContext(value: String) {
        val clean = value.take(600)
        prefs.edit().putString(KEY_PERSONAL_CONTEXT, clean).apply()
        _personalContext.value = clean
    }

    // --- Watch-reported daily steps (date -> count), sent by the Wear companion ---
    // The watch reads its own pedometer via Health Services and reports it over the Data Layer,
    // so step totals don't depend on Samsung Health writing watch data into Health Connect.
    // Kept to the most recent few days; the day's synced steps = max(Health Connect, this).

    fun setWatchSteps(dateIso: String, steps: Int) {
        if (steps <= 0) return
        val map = watchStepsMap()
        // Watch daily totals are monotonic — never let a stale batch lower the day's count.
        if (steps <= (map[dateIso] ?: 0)) return
        map[dateIso] = steps
        val pruned = map.entries.sortedByDescending { it.key }.take(5)
        val obj = JSONObject()
        pruned.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_WATCH_STEPS, obj.toString()).apply()
    }

    /** The watch's reported step count for a day, or 0 if it never reported. */
    fun watchStepsFor(dateIso: String): Int = watchStepsMap()[dateIso] ?: 0

    private fun watchStepsMap(): MutableMap<String, Int> {
        val raw = prefs.getString(KEY_WATCH_STEPS, null) ?: return mutableMapOf()
        return try {
            val o = JSONObject(raw)
            buildMap { o.keys().forEach { k -> put(k, o.optInt(k)) } }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    // --- Long-term habit notes the AI maintains across overviews (kept short to save tokens) ---
    // Distilled by the review model itself (a "HABITS:" line it appends), fed back into future reviews.

    private val _habitSummary = MutableStateFlow(prefs.getString(KEY_HABIT_SUMMARY, "") ?: "")
    val habitSummary: StateFlow<String> = _habitSummary

    fun setHabitSummary(value: String) {
        val clean = value.trim().take(1200)
        prefs.edit().putString(KEY_HABIT_SUMMARY, clean).apply()
        _habitSummary.value = clean
    }

    // --- Daily log reminder (off by default; time stored as minutes since midnight) ---

    private val _reminderEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDER_ENABLED, false))
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
        _reminderEnabled.value = enabled
    }

    private val _reminderMinutes = MutableStateFlow(prefs.getInt(KEY_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES))
    /** Reminder time as minutes since midnight (e.g. 20:00 = 1200). */
    val reminderMinutes: StateFlow<Int> = _reminderMinutes

    fun setReminderMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(0, 24 * 60 - 1)
        prefs.edit().putInt(KEY_REMINDER_MINUTES, clamped).apply()
        _reminderMinutes.value = clamped
    }

    // --- Extra reminders (meals, weigh-in, AI overviews) — generic, keyed by ReminderKind ---

    private fun loadReminders(): Map<String, com.fitpal.app.domain.model.ReminderState> =
        com.fitpal.app.domain.model.ReminderKind.entries.associate { k ->
            k.key to com.fitpal.app.domain.model.ReminderState(
                enabled = prefs.getBoolean("rem_${k.key}_on", k.defaultEnabled),
                minutes = prefs.getInt("rem_${k.key}_min", k.defaultMinutes)
            )
        }

    private val _reminders = MutableStateFlow(loadReminders())
    /** Enabled + time for every extra reminder, keyed by [com.fitpal.app.domain.model.ReminderKind.key]. */
    val reminders: StateFlow<Map<String, com.fitpal.app.domain.model.ReminderState>> = _reminders

    fun reminderStateFor(kind: com.fitpal.app.domain.model.ReminderKind): com.fitpal.app.domain.model.ReminderState =
        _reminders.value[kind.key] ?: com.fitpal.app.domain.model.ReminderState(kind.defaultEnabled, kind.defaultMinutes)

    fun setReminder(kind: com.fitpal.app.domain.model.ReminderKind, enabled: Boolean, minutes: Int) {
        prefs.edit()
            .putBoolean("rem_${kind.key}_on", enabled)
            .putInt("rem_${kind.key}_min", minutes.coerceIn(0, 24 * 60 - 1))
            .apply()
        _reminders.value = loadReminders()
    }

    // --- Meal time windows (when a logged food counts as breakfast/lunch/dinner; else snack) ---

    private fun loadMealWindows() = com.fitpal.app.domain.model.MealWindows(
        breakfastStart = prefs.getInt(KEY_BFAST_START, 5 * 60),
        breakfastEnd = prefs.getInt(KEY_BFAST_END, 11 * 60),
        lunchStart = prefs.getInt(KEY_LUNCH_START, 11 * 60),
        lunchEnd = prefs.getInt(KEY_LUNCH_END, 16 * 60),
        dinnerStart = prefs.getInt(KEY_DINNER_START, 16 * 60),
        dinnerEnd = prefs.getInt(KEY_DINNER_END, 21 * 60)
    )

    private val _mealWindows = MutableStateFlow(loadMealWindows())
    /** The user's breakfast/lunch/dinner time windows. */
    val mealWindows: StateFlow<com.fitpal.app.domain.model.MealWindows> = _mealWindows

    fun setMealWindows(w: com.fitpal.app.domain.model.MealWindows) {
        prefs.edit()
            .putInt(KEY_BFAST_START, w.breakfastStart)
            .putInt(KEY_BFAST_END, w.breakfastEnd)
            .putInt(KEY_LUNCH_START, w.lunchStart)
            .putInt(KEY_LUNCH_END, w.lunchEnd)
            .putInt(KEY_DINNER_START, w.dinnerStart)
            .putInt(KEY_DINNER_END, w.dinnerEnd)
            .apply()
        _mealWindows.value = w
    }

    /** The meal category for the current time of day, per the user's [mealWindows]. */
    fun mealTypeForNow(): String =
        _mealWindows.value.mealTypeForMinutes(java.time.LocalTime.now().let { it.hour * 60 + it.minute })

    // --- Per-macro target presets ("auto" = derive from goal + weight) ---

    private fun loadMacroSelection() = com.fitpal.app.domain.MacroSelection(
        protein = prefs.getString(KEY_MACRO_PROTEIN, "auto") ?: "auto",
        fat = prefs.getString(KEY_MACRO_FAT, "auto") ?: "auto",
        carbs = prefs.getString(KEY_MACRO_CARBS, "auto") ?: "auto",
        fiber = prefs.getString(KEY_MACRO_FIBER, "auto") ?: "auto"
    )

    private val _macroSelection = MutableStateFlow(loadMacroSelection())
    val macroSelection: StateFlow<com.fitpal.app.domain.MacroSelection> = _macroSelection

    fun setMacroTarget(macro: com.fitpal.app.domain.Macro, key: String) {
        val cur = _macroSelection.value
        val next = when (macro) {
            com.fitpal.app.domain.Macro.PROTEIN -> cur.copy(protein = key)
            com.fitpal.app.domain.Macro.FAT -> cur.copy(fat = key)
            com.fitpal.app.domain.Macro.CARBS -> cur.copy(carbs = key)
            com.fitpal.app.domain.Macro.FIBER -> cur.copy(fiber = key)
        }
        val prefKey = when (macro) {
            com.fitpal.app.domain.Macro.PROTEIN -> KEY_MACRO_PROTEIN
            com.fitpal.app.domain.Macro.FAT -> KEY_MACRO_FAT
            com.fitpal.app.domain.Macro.CARBS -> KEY_MACRO_CARBS
            com.fitpal.app.domain.Macro.FIBER -> KEY_MACRO_FIBER
        }
        prefs.edit().putString(prefKey, key).apply()
        _macroSelection.value = next
    }

    // --- First-run onboarding ---

    private val _hasOnboarded = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    /** True once the user has completed (or skipped) first-run setup. */
    val hasOnboarded: StateFlow<Boolean> = _hasOnboarded

    fun setOnboarded() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
        _hasOnboarded.value = true
    }

    // --- Daily water goal (ml). 0 = auto from body weight (~35 ml/kg). ---

    private val _waterGoalOverrideMl = MutableStateFlow(prefs.getInt(KEY_WATER_GOAL, 0))
    /** Manual hydration target in ml; 0 means derive it from weight (35 ml/kg). */
    val waterGoalOverrideMl: StateFlow<Int> = _waterGoalOverrideMl

    fun setWaterGoalOverrideMl(value: Int) {
        val clamped = value.coerceIn(0, 10000)
        prefs.edit().putInt(KEY_WATER_GOAL, clamped).apply()
        _waterGoalOverrideMl.value = clamped
    }

    // --- Home: collapse empty meal panels into a slim "add" row (off = always show all four) ---

    private val _compactEmptyMeals = MutableStateFlow(prefs.getBoolean(KEY_COMPACT_EMPTY_MEALS, false))
    /** When true, empty Breakfast/Lunch/Dinner/Snack panels shrink to a one-line add row on Home. */
    val compactEmptyMeals: StateFlow<Boolean> = _compactEmptyMeals

    fun setCompactEmptyMeals(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COMPACT_EMPTY_MEALS, enabled).apply()
        _compactEmptyMeals.value = enabled
    }

    /**
     * Up to three Gemini models tried in order. When one hits its daily free quota (429), the next
     * is tried, then the third, before falling back to on-device. All editable because Google
     * renames/retires model ids often.
     */
    private val _geminiModel = MutableStateFlow(
        prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL)?.ifBlank { DEFAULT_GEMINI_MODEL } ?: DEFAULT_GEMINI_MODEL
    )
    val geminiModel: StateFlow<String> = _geminiModel
    private val _geminiModel2 = MutableStateFlow(prefs.getString(KEY_GEMINI_MODEL2, DEFAULT_GEMINI_MODEL2) ?: DEFAULT_GEMINI_MODEL2)
    val geminiModel2: StateFlow<String> = _geminiModel2
    private val _geminiModel3 = MutableStateFlow(prefs.getString(KEY_GEMINI_MODEL3, DEFAULT_GEMINI_MODEL3) ?: DEFAULT_GEMINI_MODEL3)
    val geminiModel3: StateFlow<String> = _geminiModel3

    fun setGeminiModel(model: String) {
        val clean = model.trim().ifEmpty { DEFAULT_GEMINI_MODEL }
        prefs.edit().putString(KEY_GEMINI_MODEL, clean).apply()
        _geminiModel.value = clean
        clearGeminiQuotaExhausted()
    }

    fun setGeminiModel2(model: String) {
        val clean = model.trim()
        prefs.edit().putString(KEY_GEMINI_MODEL2, clean).apply()
        _geminiModel2.value = clean
        clearGeminiQuotaExhausted()
    }

    fun setGeminiModel3(model: String) {
        val clean = model.trim()
        prefs.edit().putString(KEY_GEMINI_MODEL3, clean).apply()
        _geminiModel3.value = clean
        clearGeminiQuotaExhausted()
    }

    /** The active models in fallback order (blank slots dropped, duplicates removed). */
    fun activeModels(): List<String> =
        listOf(_geminiModel.value, _geminiModel2.value, _geminiModel3.value)
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    // --- Per-model daily free-quota back-off (a map of model id -> the date it ran out). ---

    fun markModelQuotaExhausted(model: String) {
        val map = quotaMap().apply { put(model, java.time.LocalDate.now().toString()) }
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_GEMINI_QUOTA_MAP, obj.toString()).apply()
    }

    fun isModelQuotaExhaustedToday(model: String): Boolean =
        quotaMap()[model] == java.time.LocalDate.now().toString()

    /** True if at least one active model still has free quota today. */
    fun anyModelHasQuotaToday(): Boolean = activeModels().any { !isModelQuotaExhaustedToday(it) }

    private fun clearGeminiQuotaExhausted() {
        prefs.edit().remove(KEY_GEMINI_QUOTA_MAP).apply()
    }

    private fun quotaMap(): MutableMap<String, String> {
        val raw = prefs.getString(KEY_GEMINI_QUOTA_MAP, null) ?: return mutableMapOf()
        return try {
            val o = JSONObject(raw)
            buildMap { o.keys().forEach { k -> put(k, o.optString(k)) } }.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    // --- Serving presets (quick-tap portion sizes) — separate for meals (g) & drinks (ml) ---

    private val _mealPresets = MutableStateFlow(loadPresets(KEY_MEAL_PRESETS, DEFAULT_MEAL_PRESETS))
    val mealPresets: StateFlow<List<ServingPreset>> = _mealPresets

    fun setMealPresets(presets: List<ServingPreset>) {
        prefs.edit().putString(KEY_MEAL_PRESETS, serializePresets(presets)).apply()
        _mealPresets.value = presets
    }

    private val _drinkPresets = MutableStateFlow(loadPresets(KEY_DRINK_PRESETS, DEFAULT_DRINK_PRESETS))
    val drinkPresets: StateFlow<List<ServingPreset>> = _drinkPresets

    fun setDrinkPresets(presets: List<ServingPreset>) {
        prefs.edit().putString(KEY_DRINK_PRESETS, serializePresets(presets)).apply()
        _drinkPresets.value = presets
    }

    // --- Water quick-add amounts (ml) shown on the Water widget, editable from its detail screen ---
    private val _waterPresets = MutableStateFlow(loadWaterPresets())
    val waterPresets: StateFlow<List<Int>> = _waterPresets

    fun setWaterPresets(values: List<Int>) {
        val clean = values.filter { it > 0 }.distinct().sorted().ifEmpty { DEFAULT_WATER_PRESETS }
        prefs.edit().putString(KEY_WATER_PRESETS, clean.joinToString(",")).apply()
        _waterPresets.value = clean
    }

    private fun loadWaterPresets(): List<Int> {
        val raw = prefs.getString(KEY_WATER_PRESETS, null) ?: return DEFAULT_WATER_PRESETS
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
            .ifEmpty { DEFAULT_WATER_PRESETS }
    }

    private fun loadPresets(key: String, default: List<ServingPreset>): List<ServingPreset> {
        val raw = prefs.getString(key, null) ?: return default
        return try {
            val parsed = deserializePresets(raw)
            parsed.ifEmpty { default }
        } catch (e: Exception) {
            default
        }
    }

    private fun serializePresets(presets: List<ServingPreset>): String {
        val arr = JSONArray()
        presets.forEach { p ->
            arr.put(JSONObject().put("label", p.label).put("grams", p.grams.toDouble()))
        }
        return arr.toString()
    }

    private fun deserializePresets(raw: String): List<ServingPreset> {
        val arr = JSONArray(raw)
        val result = mutableListOf<ServingPreset>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val label = obj.optString("label").trim()
            val grams = obj.optDouble("grams", 0.0).toFloat()
            if (label.isNotEmpty() && grams > 0f) result.add(ServingPreset(label, grams))
        }
        return result
    }

    // --- Per-screen widget layout (order + show/hide) ---

    private val _widgetLayouts = MutableStateFlow(loadWidgetLayouts())
    /** Map of screenId -> saved widget order/visibility. Merge with the code registry to render. */
    val widgetLayouts: StateFlow<Map<String, List<com.fitpal.app.domain.model.WidgetSetting>>> = _widgetLayouts

    fun setWidgetLayout(screenId: String, settings: List<com.fitpal.app.domain.model.WidgetSetting>) {
        val updated = _widgetLayouts.value.toMutableMap().apply { put(screenId, settings) }
        prefs.edit().putString(KEY_WIDGET_LAYOUTS, serializeWidgetLayouts(updated)).apply()
        _widgetLayouts.value = updated
    }

    private fun loadWidgetLayouts(): Map<String, List<com.fitpal.app.domain.model.WidgetSetting>> {
        val raw = prefs.getString(KEY_WIDGET_LAYOUTS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { screenId ->
                    val arr = obj.optJSONArray(screenId) ?: return@forEach
                    val list = ArrayList<com.fitpal.app.domain.model.WidgetSetting>(arr.length())
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val key = o.optString("key").takeIf { it.isNotBlank() } ?: continue
                        list.add(com.fitpal.app.domain.model.WidgetSetting(key, o.optBoolean("on", true)))
                    }
                    put(screenId, list)
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun serializeWidgetLayouts(map: Map<String, List<com.fitpal.app.domain.model.WidgetSetting>>): String {
        val obj = JSONObject()
        map.forEach { (screenId, list) ->
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("key", it.key).put("on", it.enabled)) }
            obj.put(screenId, arr)
        }
        return obj.toString()
    }

    // --- Analytics: spotlighted widgets + switchable-card view choices ---
    // Persisted so a long-press highlight and the last-picked card mode survive a restart.

    private val _analyticsSpotlight = MutableStateFlow(
        prefs.getStringSet(KEY_ANALYTICS_SPOTLIGHT, emptySet())?.toSet() ?: emptySet()
    )
    val analyticsSpotlight: StateFlow<Set<String>> = _analyticsSpotlight

    fun setAnalyticsSpotlight(set: Set<String>) {
        prefs.edit().putStringSet(KEY_ANALYTICS_SPOTLIGHT, set).apply()
        _analyticsSpotlight.value = set
    }

    private val _analyticsViews = MutableStateFlow(loadViews())
    val analyticsViews: StateFlow<Map<String, Int>> = _analyticsViews

    fun setAnalyticsView(title: String, index: Int) {
        val updated = _analyticsViews.value.toMutableMap().apply { put(title, index) }
        prefs.edit().putString(KEY_ANALYTICS_VIEWS, serializeViews(updated)).apply()
        _analyticsViews.value = updated
    }

    private fun loadViews(): Map<String, Int> {
        val raw = prefs.getString(KEY_ANALYTICS_VIEWS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap { obj.keys().forEach { k -> put(k, obj.optInt(k, 0)) } }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun serializeViews(map: Map<String, Int>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    companion object {
        private const val KEY_CALORIE_GOAL = "daily_calorie_goal"
        private const val KEY_STEP_CAL_REDUCTION = "step_cal_reduction_pct"
        private const val KEY_HF_TOKEN = "hf_token"
        private const val KEY_GEMINI_KEY = "gemini_api_key"
        private const val KEY_ONLINE_AI_ENABLED = "online_ai_enabled"
        private const val KEY_PERSONAL_CONTEXT = "personal_context"
        private const val KEY_HABIT_SUMMARY = "habit_summary"
        private const val KEY_WATCH_STEPS = "watch_steps_by_date"
        private const val KEY_COMPACT_EMPTY_MEALS = "compact_empty_meals"
        private const val KEY_WATER_GOAL = "water_goal_ml"
        private const val KEY_ONBOARDED = "has_onboarded"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_MINUTES = "reminder_minutes"
        private const val DEFAULT_REMINDER_MINUTES = 20 * 60  // 8:00 pm
        private const val KEY_BFAST_START = "meal_breakfast_start"
        private const val KEY_BFAST_END = "meal_breakfast_end"
        private const val KEY_LUNCH_START = "meal_lunch_start"
        private const val KEY_LUNCH_END = "meal_lunch_end"
        private const val KEY_DINNER_START = "meal_dinner_start"
        private const val KEY_DINNER_END = "meal_dinner_end"
        private const val KEY_MACRO_PROTEIN = "macro_protein"
        private const val KEY_MACRO_FAT = "macro_fat"
        private const val KEY_MACRO_CARBS = "macro_carbs"
        private const val KEY_MACRO_FIBER = "macro_fiber"

        /** Auto hydration target: ~35 ml of water per kg of body weight. */
        const val WATER_ML_PER_KG = 35f
        fun autoWaterGoal(weightKg: Float): Int = (WATER_ML_PER_KG * (if (weightKg > 0f) weightKg else 70f)).toInt()
        private const val KEY_GEMINI_QUOTA_MAP = "gemini_quota_exhausted_map"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_GEMINI_MODEL2 = "gemini_model_2"
        private const val KEY_GEMINI_MODEL3 = "gemini_model_3"
        // The plain "gemini-3-flash" id can 404 depending on account/region; the "-preview"
        // id is the reliably-available Gemini 3 Flash. Editable in Settings if this changes.
        const val DEFAULT_GEMINI_MODEL = "gemini-3-flash-preview"
        const val DEFAULT_GEMINI_MODEL2 = "gemini-flash-latest"
        const val DEFAULT_GEMINI_MODEL3 = "gemini-2.5-flash"
        private const val KEY_SEX = "profile_sex"
        private const val KEY_AGE = "profile_age"
        private const val KEY_HEIGHT = "profile_height_cm"
        private const val KEY_FITNESS_GOAL = "fitness_goal"

        // Reuse the old key for meals so existing custom presets carry over.
        private const val KEY_MEAL_PRESETS = "serving_presets"
        private const val KEY_DRINK_PRESETS = "drink_presets"
        private const val KEY_WATER_PRESETS = "water_presets"
        private val DEFAULT_WATER_PRESETS = listOf(200, 330, 500)

        private const val KEY_ANALYTICS_SPOTLIGHT = "analytics_spotlight"
        private const val KEY_ANALYTICS_VIEWS = "analytics_views"
        private const val KEY_WIDGET_LAYOUTS = "widget_layouts"

        private val DEFAULT_MEAL_PRESETS = listOf(
            ServingPreset("Small", 100f),
            ServingPreset("Medium", 200f),
            ServingPreset("Large", 350f)
        )

        private val DEFAULT_DRINK_PRESETS = listOf(
            ServingPreset("Glass", 250f),
            ServingPreset("Can", 330f),
            ServingPreset("Bottle", 500f)
        )
    }
}
