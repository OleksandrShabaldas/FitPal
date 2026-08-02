package com.fitpal.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.DayScore

/** Frosted-glass surface: faint translucent fill + hairline border, clipped to [shape]. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp)): Modifier =
    this.clip(shape).background(GlassFill).border(1.dp, GlassBorder, shape)

/** A lighter glass for nested rows / secondary surfaces. */
fun Modifier.glassSoft(shape: Shape = RoundedCornerShape(18.dp)): Modifier =
    this.clip(shape).background(GlassFillSoft).border(1.dp, GlassBorderSoft, shape)

/**
 * Glass for panels that float *over* other content — dialogs, coach-marks, popups.
 *
 * Same look as [glass], but laid on an opaque surface first. Ordinary cards sit on the
 * screen's own dark backdrop, so a 10% white film is plenty; an overlay has whatever
 * happens to be underneath it, and that makes text hard to read. The opaque base keeps
 * the frosted look while guaranteeing legibility.
 */
fun Modifier.glassOverlay(shape: Shape = RoundedCornerShape(26.dp)): Modifier =
    this.clip(shape)
        .background(OverlaySurface)
        .background(GlassFill)
        .border(1.dp, GlassBorder, shape)

/** Frosted glass tinted toward an accent colour — gives a card a colour identity (water = blue,
 *  steps/exercise = green) while keeping the frosted look. */
fun Modifier.accentGlass(accent: Color, shape: Shape = RoundedCornerShape(26.dp)): Modifier =
    this.clip(shape)
        .background(GlassFill)
        .background(accent.copy(alpha = 0.07f))
        .border(1.dp, accent.copy(alpha = 0.32f), shape)

/** Colour for a daily-score tier (gold → green → blue → red). */
fun scoreColor(tier: DayScore.Tier): Color = when (tier) {
    DayScore.Tier.GOOD -> ScoreGood
    DayScore.Tier.FAIR -> ScoreFair
    DayScore.Tier.LOW -> ScoreLow
    DayScore.Tier.POOR -> ScorePoor
}
