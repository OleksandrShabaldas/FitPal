package com.fitpal.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.FatColor
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.MacroOver

private data class Nutrient(val label: String, val value: Float, val unit: String, val target: Float, val isLimit: Boolean = false)

/** The essential vitamins/minerals tracked, each with a rough adult daily target. */
private fun nutrientsOf(micros: Micronutrients) = listOf(
    Nutrient("Vitamin A", micros.vitaminAMcg, "mcg", 700f),
    Nutrient("Vitamin C", micros.vitaminCMg, "mg", 75f),
    Nutrient("Vitamin D", micros.vitaminDMcg, "mcg", 15f),
    Nutrient("Vitamin E", micros.vitaminEMg, "mg", 15f),
    Nutrient("Vitamin B6", micros.vitaminB6Mg, "mg", 1.3f),
    Nutrient("Vitamin B12", micros.vitaminB12Mcg, "mcg", 2.4f),
    Nutrient("Folate", micros.folateMcg, "mcg", 400f),
    Nutrient("Calcium", micros.calciumMg, "mg", 1000f),
    Nutrient("Iron", micros.ironMg, "mg", 12f),
    Nutrient("Magnesium", micros.magnesiumMg, "mg", 400f),
    Nutrient("Zinc", micros.zincMg, "mg", 11f),
    Nutrient("Potassium", micros.potassiumMg, "mg", 3400f),
    Nutrient("Sodium", micros.sodiumMg, "mg", 2300f, isLimit = true)
)

/** Short amount label: a decimal for small values (e.g. B12 2.4), whole numbers otherwise. */
private fun fmtAmt(v: Float): String = when {
    v <= 0f -> "0"
    v < 10f -> "%.1f".format(v)
    else -> v.toInt().toString()
}

/**
 * Shows the essential vitamins/minerals as small bars filled toward a rough daily
 * target, laid out in two columns. Every nutrient is always shown — even at 0 — so
 * you can see at a glance what you still need today. [compact] drops the amount line
 * (just bar + %) so all of them fit in a fixed-height card like the home hero.
 * [showAll] = false trims to only the nutrients actually present (>0) — used on a single
 * meal, where listing the ones it doesn't contain is just noise.
 * Sodium is treated as a limit (turns red when high), not a goal.
 */
@Composable
fun MicronutrientBars(
    micros: Micronutrients,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showAll: Boolean = true
) {
    val nutrients = nutrientsOf(micros).let { all -> if (showAll) all else all.filter { it.value > 0f } }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
    ) {
        nutrients.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NutrientCell(pair[0], compact, modifier = Modifier.weight(1f))
                if (pair.size > 1) {
                    NutrientCell(pair[1], compact, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NutrientCell(n: Nutrient, compact: Boolean, modifier: Modifier = Modifier) {
    val ratio = if (n.target > 0f) n.value / n.target else 0f
    val over = n.isLimit && ratio > 1f
    val fillColor = when {
        over -> MacroOver
        n.isLimit -> FatColor
        else -> GoldLight
    }
    val pct = (ratio * 100f).toInt()
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(n.label, style = MaterialTheme.typography.bodySmall, color = Cream)
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.labelSmall,
                color = if (over) MacroOver else CreamMuted
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(fillColor)
            )
        }
        if (!compact) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${fmtAmt(n.value)} / ${fmtAmt(n.target)} ${n.unit}${if (n.isLimit) " max" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = CreamMuted
            )
        }
    }
}
