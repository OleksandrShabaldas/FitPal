package com.fitpal.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeStripMigrationTest {

    private fun placements(list: List<WidgetSetting>) =
        ScreenWidgets.resolveCards(ScreenWidgets.HOME, list).associate { it.key to it.placement }

    @Test fun neverCustomizedKeepsTheOldHomeWithTheStripUnderTheHero() {
        val migrated = ScreenWidgets.migrateHomeToStrip(emptyList())
        assertEquals(
            listOf("hero", "strip", "limit_dessert", "limit_fried", "limit_drink", "ai_review", "meals", "water", "steps", "exercise", "weight"),
            migrated.filter { it.enabled }.map { it.key }.filter { it != "caffeine" && it != "fasting" }
        )
        val p = placements(migrated)
        assertEquals(TrackerPlacement.STRIP, p["caffeine"])
        assertEquals(TrackerPlacement.MAIN, p["fasting"])
        assertEquals(TrackerPlacement.CARD_M, p["water"])
        assertEquals(TrackerPlacement.STRIP, p["limit_fried"])
    }

    @Test fun cardsKeepTheirSize() {
        val saved = listOf(
            WidgetSetting("hero"), WidgetSetting("water", size = CardSize.S), WidgetSetting("weight", enabled = false),
            WidgetSetting("caffeine", size = CardSize.S), WidgetSetting("fasting", size = CardSize.M)
        )
        val p = placements(ScreenWidgets.migrateHomeToStrip(saved))
        assertEquals(TrackerPlacement.CARD_S, p["water"])
        assertEquals(TrackerPlacement.OFF_HOME, p["weight"])
        assertEquals(TrackerPlacement.CARD_S, p["caffeine"])
        assertEquals(TrackerPlacement.CARD_M, p["fasting"])
    }

    @Test fun alreadyMigratedIsLeftAlone() {
        val layout = ScreenWidgets.defaultLayout(ScreenWidgets.HOME)
        assertSame(layout, ScreenWidgets.migrateHomeToStrip(layout))
    }

    @Test fun newUserDefaults() {
        val p = placements(ScreenWidgets.defaultLayout(ScreenWidgets.HOME))
        assertEquals(TrackerPlacement.MAIN, p["fasting"])
        assertEquals(TrackerPlacement.CARD_S, p["water"])
        assertEquals(TrackerPlacement.CARD_S, p["steps"])
        assertEquals(TrackerPlacement.STRIP, p["caffeine"])
        assertEquals(TrackerPlacement.STRIP, p["weight"])
        assertEquals(TrackerPlacement.STRIP, p["limit_dessert"])
    }

    @Test fun hiddenTrackerComesBackWhereItWas() {
        var list = ScreenWidgets.defaultLayout(ScreenWidgets.HOME)
        list = LayoutEdits.place(list, "water", TrackerPlacement.STRIP)
        list = LayoutEdits.place(list, "water", TrackerPlacement.OFF_HOME)
        assertEquals(TrackerPlacement.OFF_HOME, placements(list)["water"])
        list = LayoutEdits.show(list, "water")
        assertEquals(TrackerPlacement.STRIP, placements(list)["water"])
    }
}
