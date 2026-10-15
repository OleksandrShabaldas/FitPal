package com.fitpal.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.DayScore

/*
 * The card surfaces. One set of modifiers, drawn per look:
 *  - Glow: a faint translucent film + hairline over the glowing backdrop (translucency, not blur).
 *  - Liquid glass: a thicker film with a bright rim light that follows how the phone is tilted.
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
        LookStyle.LIQUID -> 30f
        LookStyle.PAPER -> 16f
        LookStyle.MATERIAL -> 22f
    }
    private val scale: Float get() = LookState.appearance.roundness

    fun radius(glowRadius: Dp): Dp = (glowRadius.value * (base() / 26f) * scale).coerceAtLeast(0f).dp

    /** A main card. */
    val card: Shape get() = RoundedCornerShape(radius(26.dp))
    /** A nested row / secondary surface. */
    val soft: Shape get() = RoundedCornerShape(radius(18.dp))
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
    LookStyle.LIQUID -> this.clip(shape).background(GlassFill).liquidRim(shape, strength = 1f)
    LookStyle.PAPER -> this.paperShadow(shape, 2.dp).clip(shape).background(GlassFill).border(1.dp, GlassBorder, shape)
    LookStyle.MATERIAL -> this.clip(shape).background(GlassFill)
}

/** A lighter glass for nested rows / secondary surfaces. */
fun Modifier.glassSoft(shape: Shape = LookShapes.soft): Modifier = when (LookState.style) {
    LookStyle.GLOW -> this.clip(shape).background(GlassFillSoft).border(1.dp, GlassBorderSoft, shape)
    LookStyle.LIQUID -> this.clip(shape).background(GlassFillSoft).liquidRim(shape, strength = 0.6f)
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
    LookStyle.LIQUID -> this.clip(shape).background(OverlaySurface).liquidRim(shape, strength = 1f)
    LookStyle.PAPER -> this.paperShadow(shape, 6.dp).clip(shape).background(OverlaySurface).border(1.dp, GlassBorder, shape)
    LookStyle.MATERIAL -> this.clip(shape).background(OverlaySurface)
}

/** Glass tinted toward an accent colour — gives a card a colour identity (water = blue,
 *  steps/exercise = green) while keeping the look. */
fun Modifier.accentGlass(accent: Color, shape: Shape = LookShapes.card): Modifier {
    val light = LookState.colors.isLight
    val tinted = this.clip(shape)
        .background(GlassFill)
        .background(accent.copy(alpha = if (light) 0.10f else 0.07f))
    return when (LookState.style) {
        LookStyle.MATERIAL -> tinted
        LookStyle.LIQUID -> tinted.border(1.dp, accent.copy(alpha = 0.45f), shape)
        else -> tinted.border(1.dp, accent.copy(alpha = if (light) 0.45f else 0.32f), shape)
    }
}

/** A soft ink shadow for Paper sheets (invisible on the dark paper, a lift on the light one). */
private fun Modifier.paperShadow(shape: Shape, elevation: Dp): Modifier =
    if (!LookState.colors.isLight) this
    else this.shadow(elevation, shape, clip = false, ambientColor = Color(0x332B211B), spotColor = Color(0x402B211B))

/**
 * Liquid glass's edge: a bright rim whose light sits on the side the phone is tilted towards,
 * plus a soft inner sheen along the top. Drawn from [TiltState] in the draw phase.
 */
private fun Modifier.liquidRim(shape: Shape, strength: Float): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width = 1.2.dp.toPx())
    val light = LookState.colors.isLight
    onDrawWithContent {
        drawContent()
        val t = TiltState.tilt
        // The bright end of the rim follows the tilt; the opposite edge stays faint.
        val cx = size.width * (0.5f - t.x * 0.45f)
        val cy = size.height * (0.0f - t.y * 0.25f).coerceIn(-0.3f, 0.3f)
        val rim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (if (light) 0.95f else 0.85f) * strength),
                Color.White.copy(alpha = 0.18f * strength),
                Color.White.copy(alpha = 0.45f * strength)
            ),
            start = Offset(cx, cy),
            end = Offset(size.width - cx, size.height)
        )
        drawOutline(outline, rim, style = stroke)
        // A sheen just inside the top edge — what makes it read as thick glass.
        val sheen = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.16f * strength), Color.Transparent),
            startY = 0f, endY = size.height * 0.35f
        )
        drawOutline(outline, sheen)
    }
}

/** Colour for a daily-score tier (gold → green → blue → red). */
fun scoreColor(tier: DayScore.Tier): Color = when (tier) {
    DayScore.Tier.GOOD -> ScoreGood
    DayScore.Tier.FAIR -> ScoreFair
    DayScore.Tier.LOW -> ScoreLow
    DayScore.Tier.POOR -> ScorePoor
}
