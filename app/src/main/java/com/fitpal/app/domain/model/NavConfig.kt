package com.fitpal.app.domain.model

import org.json.JSONArray
import org.json.JSONObject

/** A main destination that can sit in the bottom bar. */
enum class NavTab(val label: String) {
    HOME("Home"), ANALYTICS("Analytics"), COLLECTION("Collection"), TRAIL("Trail"), SETTINGS("Settings")
}

/** What the big "+" in the middle of the bar does (a long press always opens the full menu). */
enum class PlusAction(val label: String, val blurb: String) {
    MENU("Add menu", "All the ways to log"),
    CAMERA("Camera", "Straight to the photo"),
    DESCRIBE("Describe", "Straight to typing it")
}

/** How the bar sits: the look decides, floating over the content, or docked to the bottom edge. */
enum class NavStyle(val label: String) { AUTO("Match the look"), FLOATING("Floating"), DOCKED("Docked") }

/**
 * The bottom bar (Settings → Home & screens → Bottom bar): which tabs, in what order, whether they
 * show labels, what "+" does, and how the bar sits. Home and Settings are always there — Home is
 * where the app starts and Settings is how you'd change this back.
 */
data class NavConfig(
    val tabs: List<NavTab> = DEFAULT_TABS,
    val labels: Boolean = false,
    val plus: PlusAction = PlusAction.MENU,
    val style: NavStyle = NavStyle.AUTO
) {
    /** The tabs as shown: required ones present, no repeats, at most [MAX_TABS]. */
    val shownTabs: List<NavTab>
        get() {
            val list = tabs.distinct().toMutableList()
            if (NavTab.HOME !in list) list.add(0, NavTab.HOME)
            if (NavTab.SETTINGS !in list) list.add(NavTab.SETTINGS)
            while (list.size > MAX_TABS) {
                val drop = list.lastOrNull { it != NavTab.HOME && it != NavTab.SETTINGS } ?: break
                list.remove(drop)
            }
            return list
        }

    fun toJson(): String = JSONObject()
        .put("tabs", JSONArray(tabs.map { it.name }))
        .put("labels", labels).put("plus", plus.name).put("style", style.name)
        .toString()

    companion object {
        const val MAX_TABS = 4
        val DEFAULT_TABS = listOf(NavTab.HOME, NavTab.ANALYTICS, NavTab.COLLECTION, NavTab.SETTINGS)

        fun fromJson(json: String?): NavConfig {
            if (json.isNullOrBlank()) return NavConfig()
            return runCatching {
                val o = JSONObject(json)
                val arr = o.optJSONArray("tabs")
                val tabs = if (arr == null) DEFAULT_TABS
                else (0 until arr.length()).mapNotNull { i -> NavTab.entries.firstOrNull { it.name == arr.optString(i) } }
                NavConfig(
                    tabs = tabs.ifEmpty { DEFAULT_TABS },
                    labels = o.optBoolean("labels", false),
                    plus = PlusAction.entries.firstOrNull { it.name == o.optString("plus") } ?: PlusAction.MENU,
                    style = NavStyle.entries.firstOrNull { it.name == o.optString("style") } ?: NavStyle.AUTO
                )
            }.getOrDefault(NavConfig())
        }
    }
}
