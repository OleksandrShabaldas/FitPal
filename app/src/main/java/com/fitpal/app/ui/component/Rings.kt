package com.fitpal.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitpal.app.domain.DayScore
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.CarbColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.FatColor
import com.fitpal.app.ui.theme.FiberColor
import com.fitpal.app.ui.theme.InterFamily
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.ProteinColor
import com.fitpal.app.ui.theme.ScorePoor
import com.fitpal.app.ui.theme.scoreColor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Home hero ring. Length = calories used out of the goal; the centre shows
 * calories LEFT; the colour is warm gold (red when over budget). Going over budget
 * completes the ring, then restarts a red overshoot arc from the top.
 *
 * Tapping the centre flips it to today's 0–100 BALANCE score (tier-coloured) with a short hint at
 * what's pulling it down — surfacing the score that otherwise only flavours the header line.
 */
@Composable
fun CalorieRing(
    consumed: Int,
    goal: Int,
    tier: DayScore.Tier,
    modifier: Modifier = Modifier,
    diameter: Dp = 196.dp,
    balanceScore: Int? = null,
    balanceHint: String = ""
) {
    var showBalance by remember { mutableStateOf(false) }
    val canFlip = balanceScore != null
    val over = goal > 0 && consumed > goal
    // The ring is a calorie indicator: a steady warm gold as you fill it, turning red only when
    // you go over budget. (It used to take the day's balance-score colour, which could turn the
    // ring blue on a "low" day — confusing for what reads as a calorie ring.)
    val ringColor = if (over) ScorePoor else CalorieColor
    val frac = if (goal > 0) consumed.toFloat() / goal else 0f
    val left = goal - consumed
    val trackColor = Color.White.copy(alpha = 0.08f)
    val animFrac by animateFloatAsState(targetValue = frac, animationSpec = tween(700), label = "calFrac")
    val animNum by animateIntAsState(targetValue = if (goal > 0) abs(left) else consumed, animationSpec = tween(700), label = "calNum")

    // Scale the stroke + centre number with the ring's size so it stays balanced when made compact.
    // The number sits a little smaller than the ring (0.225·d) so the centre reads calm, not crammed.
    val strokeDp = diameter * 0.077f
    val numberSp = (diameter.value * 0.225f).sp
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeDp.toPx()
            val inset = sw / 2f
            val arcSize = Size(size.width - sw, size.height - sw)
            val topLeft = Offset(inset, inset)
            drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            val sweep = animFrac.coerceIn(0f, 1f) * 360f
            drawArc(ringColor, -90f, sweep, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (over) {
                val osweep = (animFrac - 1f).coerceIn(0f, 1f) * 360f
                drawArc(ScorePoor, -90f, osweep, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        val centerModifier = if (canFlip) Modifier.clickable { showBalance = !showBalance } else Modifier
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = centerModifier) {
            if (showBalance && balanceScore != null) {
                Text(
                    text = balanceScore.toString(),
                    style = TextStyle(
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = numberSp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = scoreColor(tier)
                )
                Text(
                    text = "balance · ${tier.label.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scoreColor(tier).copy(alpha = 0.9f)
                )
                if (balanceHint.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = balanceHint,
                        style = MaterialTheme.typography.labelMedium,
                        color = CreamFaint,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = animNum.toString(),
                    style = TextStyle(
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = numberSp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (over) ScorePoor else Cream
                )
                Text(
                    text = when {
                        goal <= 0 -> "kcal"
                        over -> "kcal over"
                        else -> "kcal left"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) ScorePoor.copy(alpha = 0.9f) else CreamMuted
                )
                if (goal > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$consumed of $goal",
                        style = MaterialTheme.typography.labelMedium,
                        color = CreamFaint,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/** A single row of four macro goal rings. */
@Composable
fun MacroRingsRow(
    protein: Float, proteinTarget: Float,
    fat: Float, fatTarget: Float,
    carbs: Float, carbTarget: Float,
    fiber: Float, fiberTarget: Float,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MacroRing("Protein", protein, proteinTarget, ProteinColor, isCap = false)
        MacroRing("Fat", fat, fatTarget, FatColor, isCap = true)
        MacroRing("Carbs", carbs, carbTarget, CarbColor, isCap = true)
        MacroRing("Fiber", fiber, fiberTarget, FiberColor, isCap = false)
    }
}

/**
 * One macro ring. [isCap] = a "stay-under" limit (fat/carbs) which turns red when
 * exceeded; otherwise it's a "reach" goal (protein/fiber) where more is fine.
 */
@Composable
private fun MacroRing(name: String, current: Float, target: Float, color: Color, isCap: Boolean) {
    val over = isCap && target > 0f && current > target
    val arcColor = if (over) MacroOver else color
    val frac = if (target > 0f) current / target else 0f
    val animFrac by animateFloatAsState(targetValue = frac, animationSpec = tween(600), label = "macroFrac")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = CreamMuted)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 4.dp.toPx()
                val inset = sw / 2f
                val arcSize = Size(size.width - sw, size.height - sw)
                val tl = Offset(inset, inset)
                drawArc(Color.White.copy(alpha = 0.09f), -90f, 360f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                val sweep = animFrac.coerceIn(0f, 1f) * 360f
                drawArc(arcColor, -90f, sweep, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text(
                text = current.roundToInt().toString(),
                style = MaterialTheme.typography.titleSmall,
                color = if (over) MacroOver else Cream
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (target > 0f) "of ${target.roundToInt()}g" else "—",
            style = MaterialTheme.typography.labelMedium,
            color = if (over) MacroOver.copy(alpha = 0.9f) else CreamFaint
        )
    }
}
