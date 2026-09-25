package com.fitpal.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.Caffeine
import com.fitpal.app.domain.CaffeineDose
import com.fitpal.app.domain.CaffeineSettings
import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.domain.model.DietaryRuleStatus
import com.fitpal.app.ui.theme.CaffeineColor
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.DessertColor
import com.fitpal.app.ui.theme.FriedColor
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.InkBlack
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.SugaryDrinkColor
import kotlin.math.roundToInt

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

/** Everything the drawer on Home's main card shows: the enabled food limits + caffeine. */
data class HeroDrawerState(
    val rules: List<DietaryRuleStatus> = emptyList(),
    val caffeine: CaffeineView? = null
) {
    val isEmpty: Boolean get() = rules.isEmpty() && caffeine == null

    /**
     * The dot on the closed drawer's arrow: red once a limit is passed, amber when one is close,
     * none with headroom — so a closed drawer still never hides a limit you've hit.
     */
    val alertColor: Color?
        get() = when {
            rules.any { it.isOver } || caffeine?.overLimit == true -> MacroOver
            rules.any { it.isNear } || caffeine?.nearLimit == true -> Gold
            else -> null
        }
}

/**
 * The small arrow at the top of Home's main card that opens / closes the rings drawer. Deliberately
 * tiny; the dot beside it ([alertColor]) is what speaks up when something needs attention.
 */
@Composable
fun HeroDrawerHandle(open: Boolean, alertColor: Color?, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, animationSpec = tween(250), label = "drawerArrow")
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (open) "Hide food limits and caffeine" else "Show food limits and caffeine",
                    tint = CreamMuted,
                    modifier = Modifier.size(20.dp).rotate(rotation)
                )
            }
            if (alertColor != null && !open) {
                Canvas(modifier = Modifier.size(9.dp).align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(InkBlack, radius = size.minDimension / 2f, center = c)
                    drawCircle(alertColor, radius = size.minDimension * 0.34f, center = c)
                }
            }
        }
    }
}

/**
 * The drawer itself: one small ring per enabled food limit (kcal eaten vs the day's cap) plus a
 * caffeine ring. Caffeine's ring shows what's *still in your body* and drains as it wears off
 * (today); for a past day it shows that day's total. Tapping it opens the full caffeine view.
 */
@Composable
fun HeroDrawerRings(state: HeroDrawerState, nowMillis: Long, onOpenCaffeine: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        state.rules.forEach { status -> RuleRing(status) }
        state.caffeine?.let { c -> CaffeineRing(c, nowMillis, onOpenCaffeine) }
    }
}

@Composable
private fun RuleRing(status: DietaryRuleStatus) {
    val accent = ruleColor(status.kind)
    MiniRing(
        label = ruleShortName(status.kind),
        centerText = status.consumedKcal.toString(),
        footText = when {
            status.isOver -> "+${status.consumedKcal - status.limitKcal} over"
            status.isNear -> "${status.remainingKcal} left"
            else -> "of ${status.limitKcal}"
        },
        fraction = status.fraction,
        color = if (status.isOver) MacroOver else accent,
        emphasis = when {
            status.isOver -> MacroOver
            status.isNear -> Gold
            else -> null
        }
    )
}

@Composable
private fun CaffeineRing(c: CaffeineView, nowMillis: Long, onClick: () -> Unit) {
    val limit = c.settings.dailyLimitMg.coerceAtLeast(1)
    if (c.isToday) {
        val now = c.amountAt(nowMillis)
        MiniRing(
            label = "Caffeine",
            centerText = now.roundToInt().toString(),
            footText = if (c.overLimit) "over limit" else "mg in you",
            fraction = now / limit,
            color = if (c.overLimit) MacroOver else CaffeineColor,
            emphasis = when {
                c.overLimit -> MacroOver
                c.nearLimit -> Gold
                else -> null
            },
            onClick = onClick
        )
    } else {
        MiniRing(
            label = "Caffeine",
            centerText = c.dayTotalMg.roundToInt().toString(),
            footText = "mg that day",
            fraction = c.dayTotalMg / limit,
            color = if (c.overLimit) MacroOver else CaffeineColor,
            emphasis = if (c.overLimit) MacroOver else null,
            onClick = onClick
        )
    }
}

/** Short ring labels — the rings are narrow. */
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
