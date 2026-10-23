package com.fitpal.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnalyticsLayoutTest {

    private fun cards(saved: List<WidgetSetting>) = ScreenWidgets.resolveCards(ScreenWidgets.ANALYTICS, saved)

    @Test fun tilesAreOffForNewInstalls() =
        assertFalse(cards(emptyList()).first { it.key == "tiles" }.enabled)

    @Test fun tilesGoOffForExistingLayouts() {
        val saved = listOf(WidgetSetting("tiles"), WidgetSetting("weight"))
        assertFalse(cards(ScreenWidgets.migrateAnalyticsTilesOff(saved)).first { it.key == "tiles" }.enabled)
    }

    @Test fun sectionsKeepTheirOrderWhateverTheSavedOrder() {
        // An old layout with habits first and the AI reviews (now on the Coach tab) in the middle.
        val saved = listOf("heatmap", "ai", "water", "tiles", "calories", "macros", "balance").map { WidgetSetting(it) }
        val keys = ScreenWidgets.bySection(cards(saved)).map { it.key }
        assertEquals("tiles", keys.first())
        assertFalse("ai" in keys)
        assertEquals("habits", ScreenWidgets.bySection(cards(saved)).last().def.section)
        // Within Energy the user's order stays: calories before balance.
        assertEquals(listOf("calories", "balance", "maintenance"), keys.filter { it in setOf("calories", "balance", "maintenance") })
        val sectionOrder = ScreenWidgets.bySection(cards(saved)).mapNotNull { it.def.section }.distinct()
        assertEquals(ScreenWidgets.analyticsSections.map { it.first }, sectionOrder)
    }
}
