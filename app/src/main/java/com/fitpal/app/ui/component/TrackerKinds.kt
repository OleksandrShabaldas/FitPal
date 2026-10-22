package com.fitpal.app.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.fitpal.app.domain.Caffeine
import com.fitpal.app.domain.CaffeineDose
import com.fitpal.app.domain.CaffeineSettings
import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.ui.theme.DessertColor
import com.fitpal.app.ui.theme.FriedColor
import com.fitpal.app.ui.theme.SugaryDrinkColor

/**
 * The caffeine tracker's data for the day Home is showing. [doses] cover that day AND the day before
 * (a late coffee is still in you after midnight); [dayDoses] / [dayTotalMg] are just the viewed day.
 */
data class CaffeineView(
    val settings: CaffeineSettings,
    val doses: List<CaffeineDose>,
    val dayDoses: List<CaffeineDose>,
    val dayTotalMg: Float,
    val isToday: Boolean
) {
    /** In the body at [atMillis] (mg). */
    fun amountAt(atMillis: Long): Float = Caffeine.amountAt(doses, atMillis, settings.halfLifeHours)
    val overLimit: Boolean get() = settings.dailyLimitMg > 0 && dayTotalMg > settings.dailyLimitMg
    val nearLimit: Boolean get() = !overLimit && settings.dailyLimitMg > 0 && dayTotalMg >= settings.dailyLimitMg * 0.85f
}

/** Short food-limit names — strip items and chips are narrow. */
fun ruleShortName(kind: DietaryRuleKind): String = when (kind) {
    DietaryRuleKind.DESSERT -> "Dessert"
    DietaryRuleKind.FRIED -> "Fried"
    DietaryRuleKind.SUGARY_DRINK -> "Drinks"
}

/** Each food limit's identity colour (it turns red once passed). */
fun ruleColor(kind: DietaryRuleKind): Color = when (kind) {
    DietaryRuleKind.DESSERT -> DessertColor
    DietaryRuleKind.FRIED -> FriedColor
    DietaryRuleKind.SUGARY_DRINK -> SugaryDrinkColor
}

/** Each food limit's icon. */
fun ruleIcon(kind: DietaryRuleKind): ImageVector = when (kind) {
    DietaryRuleKind.DESSERT -> Icons.Default.Icecream
    DietaryRuleKind.FRIED -> Icons.Default.Fastfood
    DietaryRuleKind.SUGARY_DRINK -> Icons.Default.LocalDrink
}
