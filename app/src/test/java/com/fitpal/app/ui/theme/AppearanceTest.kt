package com.fitpal.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {

    @Test
    fun newFieldsSurviveTheRoundTrip() {
        val a = Appearance(
            style = LookStyle.MATERIAL, dynamicColor = false, materialPalette = MaterialPalette.PURPLE,
            photo = true, photoDim = 0.5f, photoVersion = 1234L
        )
        val back = Appearance.fromJson(a.toJson())
        assertEquals(MaterialPalette.PURPLE, back.materialPalette)
        assertTrue(back.photo)
        assertEquals(0.5f, back.photoDim, 0.0001f)
        assertEquals(1234L, back.photoVersion)
        assertFalse(back.dynamicColor)
    }

    @Test
    fun olderSettingsKeepTheirNearestMaterialColour() {
        // Before 2.14 Material You borrowed the Glow palette's colour.
        val old = """{"style":"MATERIAL","palette":"OCEAN","mode":"DARK","dynamic":false}"""
        val a = Appearance.fromJson(old)
        assertEquals(MaterialPalette.TEAL, a.materialPalette)
        assertFalse(a.photo)
        assertEquals(LookStyle.MATERIAL, a.style)
    }

    @Test
    fun photoDimIsClamped() {
        val a = Appearance.fromJson("""{"photo":true,"photoDim":3.0}""")
        assertEquals(0.8f, a.photoDim, 0.0001f)
    }

    @Test
    fun resetKeepsTheLookAndTheBackground() {
        val a = Appearance(
            style = LookStyle.LIQUID, palette = LookPalette.BERRY, photo = true, photoDim = 0.2f, photoVersion = 9L,
            glow = 1.5f, cardOpacity = 0.6f, roundness = 0.5f, compact = true, reduceMotion = true
        )
        val r = a.withoutTuning()
        assertEquals(LookStyle.LIQUID, r.style)
        assertEquals(LookPalette.BERRY, r.palette)
        assertTrue(r.photo)
        assertEquals(9L, r.photoVersion)
        assertEquals(1f, r.glow, 0.0001f)
        assertEquals(1f, r.cardOpacity, 0.0001f)
        assertFalse(r.compact)
        assertFalse(r.reduceMotion)
    }
}
