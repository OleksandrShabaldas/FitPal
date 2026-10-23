package com.fitpal.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.DayScore

/*
 * The card surfaces. One set of modifiers, drawn per look:
 *  - Glow: a faint translucent film + hairline over the glowing backdrop (translucency, not blur).
 *  - Liquid glass: a film with lensing edges, a rim and a specular that follow the phone's tilt,
 *    frosted over a photo backdrop (see LiquidGlass.kt).
 *  - Paper: an opaque sheet with an ink hairline and a soft shadow.
 *  - Material You: a tonal surface — no border, no film.
 * They stay plain (non-composable) modifiers; the look is read from LookState.
 */

/** Gap between cards in the main lists: tighter with Appearance → Compact. */
val CardGap: androidx.compose.ui.unit.Dp get() = if (LookState.appearance.compact) 8.dp else 12.dp

/** Corner radii for the current look × the user's roundness knob. */
object LookShapes {
    private fun base(): Float = when (LookState.style) {
        LookStyle.GLOW -> 26f
        LookStyle.LIQUID -> 28f
        LookStyle.PAPER -> 16f
        // Material's card corner (its "large" shape); nested rows land on 12, its "medium".
        LookStyle.MATERIAL -> 16f
    }
    private val scale: Float get() = LookState.appearance.roundness

    fun radius(glowRadius: Dp): Dp = (glowRadius.value * (base() / 26f) * scale).coerceAtLeast(0f).dp

    /** A main card. */
    val card: Shape get() = RoundedCornerShape(radius(26.dp))
    /**
     * A nested row / secondary surface. Liquid glass makes it concentric with the card it sits in,
     * the way iOS does: the card's radius minus the usual 8 dp inset (a 28 dp card → 20 dp rows).
     */
    val soft: Shape get() =
        if (LookState.style == LookStyle.LIQUID) RoundedCornerShape((radius(26.dp) - 8.dp).coerceAtLeast(6.dp))
        else RoundedCornerShape(radius(18.dp))
}

/**
 * The phone's tilt for Liquid glass's rim light: x/y in −1..1 (0,0 = held flat-ish upright).
 * Fed by [com.fitpal.app.ui.component.TiltLight] while Liquid glass is on; read only inside draw
 * phases, so a tilt redraws the rims without recomposing anything.
 */
object TiltState {
    var tilt by mutableStateOf(Offset.Zero)
}

/** Frosted-glass surface: faint translucent fill + hairline border, clipped to [shape]. */
fun Modifier.glass(shape: Shape = LookShapes.card): Modifier = when (LookState.style) {
    LookStyle.GLOW -> this.clip(shape).background(GlassFill).border(1.dp, GlassBorder, shape)
    LookStyle.LIQUID -> this.clip(shape).liquidGlass(shape, strength = 1f, lens = true) { GlassFill }
    LookStyle.PAPER -> this.paperShadow(shape, 2.dp).clip(shape).background(GlassFill).border(1.dp, GlassBorder, shape)
    LookStyle.MATERIAL -> this.clip(shape).background(GlassFill)
}

/** A lighter glass for nested rows / secondary surfaces. */
fun Modifier.glassSoft(shape: Shape = LookShapes.soft): Modifier = when (LookState.style) {
    LookStyle.GLOW -> this.clip(shape).background(GlassFillSoft).border(1.dp, GlassBorderSoft, shape)
    LookStyle.LIQUID -> this.clip(shape).liquidGlass(shape, strength = 0.6f, lens = false) { GlassFillSoft }
    LookStyle.PAPER -> this.clip(shape).background(GlassFillSoft).border(1.dp, GlassBorderSoft, shape)
    LookStyle.MATERIAL -> this.clip(shape).background(GlassFillSoft)
}

/**
 * Glass for panels that float *over* other content — dialogs, coach-marks, popups.
 *
 * Same look as [glass], but laid on an opaque surface first. Ordinary cards sit on the
 * screen's own backdrop, so a light film is plenty; an overlay has whatever happens to be
 * underneath it, and that makes text hard to read. The opaque base keeps the look while
 * guaranteeing legibility.
 */
fun Modifier.glassOverlay(shape: Shape = LookShapes.card): Modifier = when (LookState.style) {
    LookStyle.GLOW -> this.clip(shape).background(OverlaySurface).background(GlassFill).border(1.dp, GlassBorder, shape)
    LookStyle.LIQUID -> this.clip(shape).liquidGlass(shape, strength = 1f, lens = true) { OverlaySurface }
    LookStyle.PAPER -> this.paperShadow(shape, 6.dp).clip(shape).background(OverlaySurface).border(1.dp, GlassBorder, shape)
    LookStyle.MATERIAL -> this.clip(shape).background(OverlaySurface)
}

/** Glass tinted toward an accent colour — gives a card a colour identity (water = blue,
 *  steps/exercise = green) while keeping the look. */
fun Modifier.accentGlass(accent: Color, shape: Shape = LookShapes.card): Modifier {
    val light = LookState.colors.isLight
    if (LookState.style == LookStyle.LIQUID) {
        return this.clip(shape)
            .liquidGlass(shape, strength = 1f, lens = true) { GlassFill }
            .background(accent.copy(alpha = if (light) 0.10f else 0.07f))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
    }
    val tinted = this.clip(shape)
        .background(GlassFill)
        .background(accent.copy(alpha = if (light) 0.10f else 0.07f))
    return when (LookState.style) {
        LookStyle.MATERIAL -> tinted
        else -> tinted.border(1.dp, accent.copy(alpha = if (light) 0.45f else 0.32f), shape)
    }
}

/** A soft ink shadow for Paper sheets (invisible on the dark paper, a lift on the light one). */
private fun Modifier.paperShadow(shape: Shape, elevation: Dp): Modifier =
    if (!LookState.colors.isLight) this
    else this.shadow(elevation, shape, clip = false, ambientColor = Color(0x332B211B), spotColor = Color(0x402B211B))

/** Colour for a daily-score tier (gold → green → blue → red). */
fun scoreColor(tier: DayScore.Tier): Color = when (tier) {
    DayScore.Tier.GOOD -> ScoreGood
    DayScore.Tier.FAIR -> ScoreFair
    DayScore.Tier.LOW -> ScoreLow
    DayScore.Tier.POOR -> ScorePoor
}
