package com.fitpal.app.widget

import android.appwidget.AppWidgetProvider
import androidx.annotation.StringRes
import com.fitpal.app.R

/**
 * The home-screen widget family. Each kind is its own entry in the launcher's widget picker (and in
 * the in-app gallery, Settings → Home-screen widgets), resizes into a few layouts, and can wear any
 * [WidgetStyle]. [defaultWidthDp]×[defaultHeightDp] is the size it's drawn at for previews. Its name
 * and one-liner are string resources, shared with the launcher's picker.
 */
enum class WidgetKind(
    val id: String,
    @StringRes val label: Int,
    @StringRes val description: Int,
    val theme: WidgetTheme,
    val defaultWidthDp: Int,
    val defaultHeightDp: Int,
    val provider: Class<out AppWidgetProvider>
) {
    TODAY(
        "today", R.string.widget_label_today, R.string.widget_today_description,
        WidgetTheme.WARM, 170, 170, TodayWidgetProvider::class.java
    ),
    QUICK_LOG(
        "quick_log", R.string.widget_label_quick_log, R.string.widget_quick_log_description,
        WidgetTheme.WARM, 320, 90, FitPalWidgetProvider::class.java
    ),
    MACROS(
        "macros", R.string.widget_label_macros, R.string.widget_macros_description,
        WidgetTheme.WARM, 320, 110, MacrosWidgetProvider::class.java
    ),
    WATER(
        "water", R.string.widget_label_water, R.string.widget_water_description,
        WidgetTheme.BLUE, 170, 170, WaterWidgetProvider::class.java
    ),
    FASTING(
        "fasting", R.string.widget_label_fasting, R.string.widget_fasting_description,
        WidgetTheme.BLUE, 170, 170, FastingWidgetProvider::class.java
    ),
    WEEK(
        "week", R.string.widget_label_week, R.string.widget_week_description,
        WidgetTheme.BLUE, 320, 170, WeekWidgetProvider::class.java
    ),
    WEIGHT(
        "weight", R.string.widget_label_weight, R.string.widget_weight_description,
        WidgetTheme.GREEN, 170, 170, WeightWidgetProvider::class.java
    ),
    CAFFEINE(
        "caffeine", R.string.widget_label_caffeine, R.string.widget_caffeine_description,
        WidgetTheme.WARM, 170, 170, CaffeineWidgetProvider::class.java
    );

    companion object {
        fun fromId(id: String?): WidgetKind? = entries.firstOrNull { it.id == id }
    }
}

/** The glow behind a widget — the app's own backdrop families (Today warm, Trends blue, Garden green). */
enum class WidgetTheme { WARM, BLUE, GREEN }

/**
 * How a widget is dressed. Chosen per widget when it's added (and again from its long-press menu, or
 * the in-app gallery). Every look draws the same content — only the card and ink change.
 */
enum class WidgetStyle(val id: String, val label: String, val blurb: String) {
    GLOW("glow", "Glow", "FitPal's own look — a warm glow behind dark glass."),
    GLASS("glass", "Glass", "Smoky and see-through, so your wallpaper shows."),
    CLEAR("clear", "Clear", "No card at all — just the numbers on your wallpaper."),
    PAPER("paper", "Paper", "Light and warm, for bright wallpapers.");

    /** Light card with dark ink (everything else is light ink on dark or on the wallpaper). */
    val isLight: Boolean get() = this == PAPER

    companion object {
        val DEFAULT = GLOW
        fun fromId(id: String?): WidgetStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * What a colour *means* on a widget. Dark looks use the app's own tokens; [WidgetStyle.PAPER] uses
 * deeper versions of the same hues so they still read on cream.
 */
enum class Tone(val onDark: Int, val onLight: Int) {
    CALORIE(0xFFF3CE7C.toInt(), 0xFFB5822A.toInt()),
    PROTEIN(0xFFF0997B.toInt(), 0xFFC45E3E.toInt()),
    FAT(0xFFF2C14E.toInt(), 0xFFB08312.toInt()),
    CARBS(0xFFA6CF6E.toInt(), 0xFF5A8C28.toInt()),
    FIBER(0xFFB89E8A.toInt(), 0xFF866B56.toInt()),
    WATER(0xFF6FA8FF.toInt(), 0xFF3A70D6.toInt()),
    FASTING(0xFF6FA8FF.toInt(), 0xFF3A70D6.toInt()),
    EATING(0xFFF3CE7C.toInt(), 0xFFB5822A.toInt()),
    OVER(0xFFE0584A.toInt(), 0xFFC03A2D.toInt()),
    CAFFEINE(0xFFC9A27E.toInt(), 0xFF87603C.toInt()),
    GOOD(0xFF8CC152.toInt(), 0xFF4C8A2C.toInt()),
    GOLD(0xFFE8A93C.toInt(), 0xFFB9771A.toInt());

    fun on(style: WidgetStyle): Int = if (style.isLight) onLight else onDark
}
