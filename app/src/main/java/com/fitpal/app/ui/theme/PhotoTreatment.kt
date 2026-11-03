package com.fitpal.app.ui.theme

import kotlin.math.roundToInt

/**
 * How heavily the user's own photo is covered behind Glow and Liquid glass (docs/redesign/12, #6):
 * blur, darkness (lightness on light Liquid), grain, and the look's own light over it, each 0–100.
 *
 * One "Photo strength" (0..1, Settings → Appearance → Background) moves them together: Light frost
 * at 0, the values the user tuned on the November review page at 0.5 (the default), Deep at 1.
 */
data class PhotoTreatment(val blur: Float, val dark: Float, val grain: Float, val glow: Float) {

    /** The blur rounded to steps of 5, so dragging the slider doesn't rebuild the blurred copy every frame. */
    val blurLevel: Int get() = ((blur / 5f).roundToInt() * 5).coerceIn(0, 100)

    /** The darkening (or lightening) overlay's alpha. */
    val darkAlpha: Float get() = (dark / 100f).coerceIn(0f, 0.95f)

    /** The grain overlay's alpha (overlay blend). */
    val grainAlpha: Float get() = (grain / 100f * 0.42f).coerceIn(0f, 0.42f)

    /** How strongly the look's light orbs are drawn over the photo (0–1). */
    val glowAmount: Float get() = (glow / 100f).coerceIn(0f, 1f)

    companion object {
        val LIGHT = PhotoTreatment(blur = 35f, dark = 48f, grain = 20f, glow = 30f)
        val DEFAULT = PhotoTreatment(blur = 65f, dark = 62f, grain = 39f, glow = 55f)
        val DEEP = PhotoTreatment(blur = 90f, dark = 76f, grain = 45f, glow = 70f)

        const val DEFAULT_STRENGTH = 0.5f

        fun at(strength: Float): PhotoTreatment {
            val s = strength.coerceIn(0f, 1f)
            return if (s <= 0.5f) lerp(LIGHT, DEFAULT, s / 0.5f) else lerp(DEFAULT, DEEP, (s - 0.5f) / 0.5f)
        }

        private fun lerp(a: PhotoTreatment, b: PhotoTreatment, t: Float) = PhotoTreatment(
            blur = a.blur + (b.blur - a.blur) * t,
            dark = a.dark + (b.dark - a.dark) * t,
            grain = a.grain + (b.grain - a.grain) * t,
            glow = a.glow + (b.glow - a.glow) * t
        )
    }
}
