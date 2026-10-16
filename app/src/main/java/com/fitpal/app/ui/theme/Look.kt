package com.fitpal.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.json.JSONObject

/*
 * FitPal's looks — Style × Palette × Mode (Settings → Appearance).
 *
 * Every colour token in Color.kt (`Cream`, `GoldLight`, `GlassFill`, …) is a getter on the current
 * look, held in Compose snapshot state ([LookState]). That's deliberate: the tokens are read in
 * ~1,300 places — composables, Canvas draw lambdas, plain non-composable modifiers like `glass()` —
 * and a snapshot read works in all of them and recomposes/redraws on a change, so the old call sites
 * didn't have to change at all. Never copy a token into a top-level `val` (it would freeze the look
 * it was read in); use a getter.
 */

/** How the app is drawn — the surfaces, the backdrop, the nav, the type. */
enum class LookStyle(val label: String, val blurb: String, val hasLight: Boolean, val hasDark: Boolean) {
    GLOW("Glow", "Frosted glass over a warm glow — the original.", hasLight = false, hasDark = true),
    LIQUID("Liquid glass", "Thick glass over drifting colour, lit by how you hold the phone.", hasLight = true, hasDark = true),
    PAPER("Paper", "Ink on paper — calm, opaque and easy on the eyes.", hasLight = true, hasDark = true),
    MATERIAL("Material You", "Clean tonal surfaces that follow your wallpaper.", hasLight = true, hasDark = true);
}

/** Colour families for the Glow look (and the accent seed for the others). */
enum class LookPalette(val label: String, val swatch: Long, val swatchDeep: Long) {
    EMBER("Ember", 0xFFF3CE7C, 0xFF6B4426),
    OCEAN("Ocean", 0xFF6FE3D6, 0xFF0F5566),
    FOREST("Forest", 0xFFB9DE7E, 0xFF3A5A22),
    BERRY("Berry", 0xFFF59BC4, 0xFF6A1F48),
    AURORA("Aurora", 0xFF8FF0C9, 0xFF3A2A80),
    GRAPHITE("Graphite", 0xFFE6E6EA, 0xFF34343C);
}

/** Light, dark, or follow the phone. Styles that only exist in one mode ignore it. */
enum class LookMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Match phone") }

/** Everything the user picks in Settings → Appearance. Stored as one JSON object. */
data class Appearance(
    val style: LookStyle = LookStyle.GLOW,
    val palette: LookPalette = LookPalette.EMBER,
    val mode: LookMode = LookMode.SYSTEM,
    /** Material You: take the colours from the wallpaper (Android 12+) instead of the palette. */
    val dynamicColor: Boolean = true,
    /** 0.4–1.6 — how bright the backdrop's light orbs are. */
    val glow: Float = 1f,
    /** The fine film-grain texture over the backdrop. */
    val grain: Boolean = true,
    /** 0.5–2 — how solid the cards are (1 = the look's own). */
    val cardOpacity: Float = 1f,
    /** 0.4–1.4 — corner rounding (1 = the look's own). */
    val roundness: Float = 1f,
    /** Tighter spacing in lists and cards. */
    val compact: Boolean = false,
    /** Stop decorative motion (drifting blobs, tilt light, card wiggle). */
    val reduceMotion: Boolean = false
) {
    fun toJson(): String = JSONObject()
        .put("style", style.name).put("palette", palette.name).put("mode", mode.name)
        .put("dynamic", dynamicColor).put("glow", glow.toDouble()).put("grain", grain)
        .put("cardOpacity", cardOpacity.toDouble()).put("roundness", roundness.toDouble())
        .put("compact", compact).put("reduceMotion", reduceMotion)
        .toString()

    companion object {
        fun fromJson(json: String?): Appearance {
            if (json.isNullOrBlank()) return Appearance()
            return runCatching {
                val o = JSONObject(json)
                Appearance(
                    style = LookStyle.entries.firstOrNull { it.name == o.optString("style") } ?: LookStyle.GLOW,
                    palette = LookPalette.entries.firstOrNull { it.name == o.optString("palette") } ?: LookPalette.EMBER,
                    mode = LookMode.entries.firstOrNull { it.name == o.optString("mode") } ?: LookMode.SYSTEM,
                    dynamicColor = o.optBoolean("dynamic", true),
                    glow = o.optDouble("glow", 1.0).toFloat().coerceIn(0.4f, 1.6f),
                    grain = o.optBoolean("grain", true),
                    cardOpacity = o.optDouble("cardOpacity", 1.0).toFloat().coerceIn(0.5f, 2f),
                    roundness = o.optDouble("roundness", 1.0).toFloat().coerceIn(0.4f, 1.4f),
                    compact = o.optBoolean("compact", false),
                    reduceMotion = o.optBoolean("reduceMotion", false)
                )
            }.getOrDefault(Appearance())
        }
    }
}

/** A soft light in the backdrop: colour, centre (fractions of the screen), radius (of the short side), alpha. */
data class BackdropOrb(val color: Color, val cx: Float, val cy: Float, val rFrac: Float, val alpha: Float)

/** One backdrop "world" (Today / Activity / Trends / Garden) in the current look. */
data class BackdropSpec(val base: List<Color>, val orbs: List<BackdropOrb>)

/** Every colour the UI reads, for one look. Names follow the original dark tokens. */
data class FitPalColors(
    val isLight: Boolean,
    val cream: Color, val creamDim: Color, val creamMuted: Color, val creamFaint: Color,
    val inkBlack: Color, val ink: Color,
    val glassFill: Color, val glassFillSoft: Color, val glassBorder: Color, val glassBorderSoft: Color,
    val overlaySurface: Color,
    val gold: Color, val goldLight: Color,
    /** Icon/text on a gold (accent) fill — e.g. the nav's "+". */
    val onGold: Color,
    val scoreGood: Color, val scoreFair: Color, val scoreLow: Color, val scorePoor: Color,
    val loggedBest: Color, val loggedGood: Color, val loggedMid: Color, val loggedLow: Color, val loggedWorst: Color,
    val protein: Color, val fat: Color, val carb: Color, val fiber: Color, val macroOver: Color,
    val calorie: Color,
    val dessert: Color, val fried: Color, val sugaryDrink: Color, val caffeine: Color,
    val accentActivity: Color, val accentTrends: Color, val accentGarden: Color,
    /** Fasting's own colour (night violet) — water keeps the blue, so the two never read as one thing. */
    val fasting: Color,
    /** Faint fill for empty bar tracks, dividers and chips (white film on dark, ink film on light). */
    val track: Color,
    /** The backdrop's darkening film (0 on light looks). */
    val backdropShade: Float,
    val backdrops: Map<BackdropWorld, BackdropSpec>
)

/** The per-screen backdrop worlds (mirrors BackdropTheme in GradientBackdrop). */
enum class BackdropWorld { TODAY, ACTIVITY, TRENDS, GARDEN }

/** The look in effect: what was picked, plus everything derived from it. */
data class Look(
    val appearance: Appearance,
    val style: LookStyle,
    val colors: FitPalColors
) {
    val isLight: Boolean get() = colors.isLight

    companion object {
        val DEFAULT = Look(Appearance(), LookStyle.GLOW, Palettes.glow(LookPalette.EMBER))
    }
}

/**
 * The current look, app-wide. [FitPalActivity] sets it from settings before the first frame (so a
 * cold start never flashes the wrong colours) and again whenever Appearance changes.
 */
object LookState {
    var look: Look by mutableStateOf(Look.DEFAULT)
        private set

    val colors: FitPalColors get() = look.colors
    val style: LookStyle get() = look.style
    val appearance: Appearance get() = look.appearance

    /** Resolve [appearance] (mode, dynamic colour) against the phone and make it current. */
    fun apply(appearance: Appearance, context: Context, systemDark: Boolean) {
        look = resolve(appearance, context, systemDark)
    }

    fun resolve(appearance: Appearance, context: Context?, systemDark: Boolean): Look {
        val style = appearance.style
        val wantLight = when {
            !style.hasLight -> false
            !style.hasDark -> true
            else -> when (appearance.mode) {
                LookMode.LIGHT -> true
                LookMode.DARK -> false
                LookMode.SYSTEM -> !systemDark
            }
        }
        val base = when (style) {
            LookStyle.GLOW -> Palettes.glow(appearance.palette)
            LookStyle.LIQUID -> if (wantLight) Palettes.liquidLight(appearance.palette) else Palettes.liquidDark(appearance.palette)
            LookStyle.PAPER -> if (wantLight) Palettes.paperLight(appearance.palette) else Palettes.paperDark(appearance.palette)
            LookStyle.MATERIAL -> {
                val dynamic = if (appearance.dynamicColor && context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (wantLight) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
                } else null
                Palettes.material(appearance.palette, wantLight, dynamic)
            }
        }
        return Look(appearance, style, base.tuned(appearance))
    }

    /** The user's knobs on top of the look's own colours: glow strength and card opacity. */
    private fun FitPalColors.tuned(a: Appearance): FitPalColors {
        fun Color.scaleAlpha(k: Float) = copy(alpha = (alpha * k).coerceIn(0f, 1f))
        return copy(
            glassFill = glassFill.scaleAlpha(a.cardOpacity),
            glassFillSoft = glassFillSoft.scaleAlpha(a.cardOpacity),
            backdrops = backdrops.mapValues { (_, spec) ->
                spec.copy(orbs = spec.orbs.map { it.copy(alpha = (it.alpha * a.glow).coerceIn(0f, 1f)) })
            }
        )
    }
}

/** The colour sets behind each style. */
object Palettes {
    // Semantic colours shared by the dark looks (macros, scores, limits read the same everywhere).
    private val darkSemantic = Semantic(
        scoreFair = Color(0xFF8CC152), scoreLow = Color(0xFF5BA8E0), scorePoor = Color(0xFFE0584A),
        loggedBest = Color(0xFF5BA8E0), loggedGood = Color(0xFF8CC152), loggedMid = Color(0xFFEAC63F),
        loggedLow = Color(0xFFE58A3C), loggedWorst = Color(0xFFE0584A),
        protein = Color(0xFFF0997B), fat = Color(0xFFF2C14E), carb = Color(0xFFA6CF6E), fiber = Color(0xFFB89E8A),
        macroOver = Color(0xFFE0584A),
        dessert = Color(0xFFE9A6C3), fried = Color(0xFFE59A5B), sugaryDrink = Color(0xFF7FC4E8), caffeine = Color(0xFFC9A27E),
        accentActivity = Color(0xFF5FD0C4), accentTrends = Color(0xFF6FA8FF), accentGarden = Color(0xFF8CC152),
        fasting = Color(0xFFA592FF)
    )

    // Deeper versions of the same hues so they read on cream/white (the widgets' Paper tones).
    private val lightSemantic = Semantic(
        scoreFair = Color(0xFF4C8A2C), scoreLow = Color(0xFF3A70D6), scorePoor = Color(0xFFC03A2D),
        loggedBest = Color(0xFF3A70D6), loggedGood = Color(0xFF4C8A2C), loggedMid = Color(0xFFB58A12),
        loggedLow = Color(0xFFC0651E), loggedWorst = Color(0xFFC03A2D),
        protein = Color(0xFFC45E3E), fat = Color(0xFFB08312), carb = Color(0xFF5A8C28), fiber = Color(0xFF866B56),
        macroOver = Color(0xFFC03A2D),
        dessert = Color(0xFFB8527F), fried = Color(0xFFB8622A), sugaryDrink = Color(0xFF2F86B8), caffeine = Color(0xFF87603C),
        accentActivity = Color(0xFF1E8C80), accentTrends = Color(0xFF3A70D6), accentGarden = Color(0xFF4C8A2C),
        fasting = Color(0xFF6A55D0)
    )

    private data class Semantic(
        val scoreFair: Color, val scoreLow: Color, val scorePoor: Color,
        val loggedBest: Color, val loggedGood: Color, val loggedMid: Color, val loggedLow: Color, val loggedWorst: Color,
        val protein: Color, val fat: Color, val carb: Color, val fiber: Color, val macroOver: Color,
        val dessert: Color, val fried: Color, val sugaryDrink: Color, val caffeine: Color,
        val accentActivity: Color, val accentTrends: Color, val accentGarden: Color,
        val fasting: Color
    )

    private fun build(
        isLight: Boolean,
        text: Color,
        inkBlack: Color, ink: Color,
        glassFill: Color, glassFillSoft: Color, glassBorder: Color, glassBorderSoft: Color,
        overlay: Color,
        gold: Color, goldLight: Color, onGold: Color,
        backdrops: Map<BackdropWorld, BackdropSpec>,
        backdropShade: Float,
        s: Semantic = if (isLight) lightSemantic else darkSemantic
    ) = FitPalColors(
        isLight = isLight,
        cream = text, creamDim = text.copy(alpha = 0.80f), creamMuted = text.copy(alpha = if (isLight) 0.64f else 0.55f),
        creamFaint = text.copy(alpha = if (isLight) 0.50f else 0.50f),
        inkBlack = inkBlack, ink = ink,
        glassFill = glassFill, glassFillSoft = glassFillSoft, glassBorder = glassBorder, glassBorderSoft = glassBorderSoft,
        overlaySurface = overlay,
        gold = gold, goldLight = goldLight, onGold = onGold,
        scoreGood = goldLight, scoreFair = s.scoreFair, scoreLow = s.scoreLow, scorePoor = s.scorePoor,
        loggedBest = s.loggedBest, loggedGood = s.loggedGood, loggedMid = s.loggedMid, loggedLow = s.loggedLow, loggedWorst = s.loggedWorst,
        protein = s.protein, fat = s.fat, carb = s.carb, fiber = s.fiber, macroOver = s.macroOver,
        calorie = goldLight,
        dessert = s.dessert, fried = s.fried, sugaryDrink = s.sugaryDrink, caffeine = s.caffeine,
        accentActivity = s.accentActivity, accentTrends = s.accentTrends, accentGarden = s.accentGarden,
        fasting = s.fasting,
        track = if (isLight) text.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.10f),
        backdropShade = backdropShade,
        backdrops = backdrops
    )

    // ---- The fixed worlds that keep their identity in every Glow palette ----
    private fun worlds(today: BackdropSpec, inkBlack: Color, monochrome: Boolean = false): Map<BackdropWorld, BackdropSpec> {
        if (monochrome) return BackdropWorld.entries.associateWith { today }
        return mapOf(
            BackdropWorld.TODAY to today,
            BackdropWorld.ACTIVITY to BackdropSpec(
                listOf(Color(0xFF14403B), Color(0xFF0E2C2A), Color(0xFF11201F), inkBlack),
                listOf(BackdropOrb(Color(0xFF6FE0D0), 0.85f, 0.04f, 0.40f, 0.34f), BackdropOrb(Color(0xFF3FA9C0), 0.05f, 0.40f, 0.34f, 0.24f))
            ),
            BackdropWorld.TRENDS to BackdropSpec(
                listOf(Color(0xFF33438C), Color(0xFF232C5E), Color(0xFF171C36), inkBlack),
                listOf(
                    BackdropOrb(Color(0xFF8AB0FF), 0.85f, 0.03f, 0.46f, 0.55f),
                    BackdropOrb(Color(0xFF9B7FE0), 0.00f, 0.34f, 0.40f, 0.40f),
                    BackdropOrb(Color(0xFF6E90E0), 0.90f, 0.66f, 0.42f, 0.28f)
                )
            ),
            BackdropWorld.GARDEN to BackdropSpec(
                listOf(Color(0xFF24401F), Color(0xFF182C16), Color(0xFF121E10), inkBlack),
                listOf(BackdropOrb(Color(0xFFB6E06E), 0.82f, 0.04f, 0.40f, 0.34f), BackdropOrb(Color(0xFF5FB85A), 0.06f, 0.40f, 0.34f, 0.22f))
            )
        )
    }

    private fun todayWorld(base: List<Long>, orbs: List<Long>, inkBlack: Color) = BackdropSpec(
        base.map { Color(it) } + inkBlack,
        listOf(
            BackdropOrb(Color(orbs[0]), 0.85f, 0.02f, 0.42f, 0.50f),
            BackdropOrb(Color(orbs[1]), 0.05f, 0.30f, 0.34f, 0.30f),
            BackdropOrb(Color(orbs[2]), 0.90f, 0.70f, 0.40f, 0.22f)
        )
    )

    // ================= GLOW =================
    fun glow(p: LookPalette): FitPalColors {
        data class G(val text: Long, val inkBlack: Long, val ink: Long, val overlay: Long, val gold: Long, val goldLight: Long, val onGold: Long, val base: List<Long>, val orbs: List<Long>)
        val g = when (p) {
            LookPalette.EMBER -> G(0xFFF5ECE0, 0xFF0E0B0A, 0xFF120D0B, 0xF01A1310, 0xFFE8A93C, 0xFFF3CE7C, 0xFF3A2406,
                listOf(0xFF5A3A22, 0xFF3A261A, 0xFF241814), listOf(0xFFF3C46E, 0xFFE89A4E, 0xFFB9603A))
            LookPalette.OCEAN -> G(0xFFE8F4F4, 0xFF06111A, 0xFF08151E, 0xF00A1A24, 0xFF3CC7BA, 0xFF6FE3D6, 0xFF04302B,
                listOf(0xFF0F4C5C, 0xFF0E3346, 0xFF0A2030), listOf(0xFF6FE3D6, 0xFF3F8FD6, 0xFF2A6FA0))
            LookPalette.FOREST -> G(0xFFEEF2E2, 0xFF0A110B, 0xFF0C150D, 0xF0111C12, 0xFF8CC152, 0xFFB9DE7E, 0xFF1E3008,
                listOf(0xFF31502A, 0xFF22381E, 0xFF162414), listOf(0xFFB9DE7E, 0xFF6FB85A, 0xFF3F8A4A))
            LookPalette.BERRY -> G(0xFFF8E9F0, 0xFF12090F, 0xFF160B13, 0xF01E0F19, 0xFFE46AA3, 0xFFF59BC4, 0xFF3D0A22,
                listOf(0xFF5E1F44, 0xFF40163A, 0xFF28102A), listOf(0xFFF59BC4, 0xFFC45AD6, 0xFF8A3A9E))
            LookPalette.AURORA -> G(0xFFEAEFFA, 0xFF090A17, 0xFF0C0D1C, 0xF0121428, 0xFF4FD1A0, 0xFF8FF0C9, 0xFF063A2A,
                listOf(0xFF2E2470, 0xFF1C2A55, 0xFF101A30), listOf(0xFF8FF0C9, 0xFF8A7BFF, 0xFF3FB0C8))
            LookPalette.GRAPHITE -> G(0xFFF2F2F4, 0xFF0E0E10, 0xFF121214, 0xF019191C, 0xFFC8C8CE, 0xFFEDEDF0, 0xFF1A1A1E,
                listOf(0xFF34343C, 0xFF26262C, 0xFF1A1A1E), listOf(0xFFD8D8E0, 0xFF9A9AA6, 0xFF6E6E7A))
        }
        val inkBlack = Color(g.inkBlack)
        val today = todayWorld(g.base, g.orbs, inkBlack)
        return build(
            isLight = false, text = Color(g.text), inkBlack = inkBlack, ink = Color(g.ink),
            glassFill = Color(0x1AFFFFFF), glassFillSoft = Color(0x14FFFFFF),
            glassBorder = Color(0x33FFFFFF), glassBorderSoft = Color(0x24FFFFFF),
            overlay = Color(g.overlay), gold = Color(g.gold), goldLight = Color(g.goldLight), onGold = Color(g.onGold),
            backdrops = worlds(today, inkBlack, monochrome = p == LookPalette.GRAPHITE),
            backdropShade = 0.22f
        )
    }

    // ================= LIQUID GLASS =================
    private fun liquidBlobs(p: LookPalette, light: Boolean): List<Color> = when (p) {
        LookPalette.EMBER -> listOf(0xFFFF9F5A, 0xFFFF5F8A, 0xFF8A6BFF)
        LookPalette.OCEAN -> listOf(0xFF2FD3F0, 0xFF4D7CFF, 0xFF34E0B0)
        LookPalette.FOREST -> listOf(0xFF8BE36B, 0xFF2FC9A0, 0xFFE5E06A)
        LookPalette.BERRY -> listOf(0xFFFF5FA2, 0xFFB45CFF, 0xFFFF9F7A)
        LookPalette.AURORA -> listOf(0xFF7B5CFF, 0xFFFF5FA2, 0xFF2FD3F0)
        LookPalette.GRAPHITE -> listOf(0xFF9AA6B8, 0xFFC8C8D6, 0xFF6E7A8E)
    }.map { c -> Color(c).let { if (light) lerp(it, Color.White, 0.35f) else it } }

    private fun liquidWorlds(p: LookPalette, light: Boolean, inkBlack: Color): Map<BackdropWorld, BackdropSpec> {
        val blobs = liquidBlobs(p, light)
        val spec = BackdropSpec(
            listOf(inkBlack, inkBlack),
            listOf(
                BackdropOrb(blobs[0], 0.80f, 0.10f, 0.55f, if (light) 0.85f else 0.75f),
                BackdropOrb(blobs[1], 0.10f, 0.42f, 0.50f, if (light) 0.80f else 0.70f),
                BackdropOrb(blobs[2], 0.70f, 0.78f, 0.48f, if (light) 0.80f else 0.65f)
            )
        )
        return BackdropWorld.entries.associateWith { spec }
    }

    fun liquidDark(p: LookPalette): FitPalColors {
        val inkBlack = Color(0xFF0B0A18)
        val accent = when (p) {
            LookPalette.GRAPHITE -> Color(0xFFE6E6EA)
            else -> lerp(liquidBlobs(p, false)[0], Color.White, 0.45f)
        }
        return build(
            isLight = false, text = Color(0xFFFFFFFF), inkBlack = inkBlack, ink = Color(0xFF100E20),
            glassFill = Color(0x26FFFFFF), glassFillSoft = Color(0x1AFFFFFF),
            glassBorder = Color(0x80FFFFFF), glassBorderSoft = Color(0x40FFFFFF),
            overlay = Color(0xE6181630), gold = accent, goldLight = lerp(accent, Color.White, 0.25f), onGold = Color(0xFF16142C),
            backdrops = liquidWorlds(p, false, inkBlack), backdropShade = 0.10f
        )
    }

    fun liquidLight(p: LookPalette): FitPalColors {
        val inkBlack = Color(0xFFEEF0F7)
        val accent = when (p) {
            LookPalette.EMBER -> Color(0xFFD9641E)
            LookPalette.OCEAN -> Color(0xFF1F6FE0)
            LookPalette.FOREST -> Color(0xFF2E8A3E)
            LookPalette.BERRY -> Color(0xFFC2306E)
            LookPalette.AURORA -> Color(0xFF5B3FE0)
            LookPalette.GRAPHITE -> Color(0xFF3A3A44)
        }
        return build(
            isLight = true, text = Color(0xFF16161A), inkBlack = inkBlack, ink = Color(0xFFF6F7FB),
            glassFill = Color(0x8CFFFFFF), glassFillSoft = Color(0x66FFFFFF),
            glassBorder = Color(0xE6FFFFFF), glassBorderSoft = Color(0xB3FFFFFF),
            overlay = Color(0xF2F7F8FC), gold = accent, goldLight = accent, onGold = Color(0xFFFFFFFF),
            backdrops = liquidWorlds(p, true, inkBlack), backdropShade = 0f
        )
    }

    // ================= PAPER =================
    private fun paperAccent(p: LookPalette, light: Boolean): Color = Color(
        when (p) {
            LookPalette.EMBER -> if (light) 0xFFB4532A else 0xFFD9A55B
            LookPalette.OCEAN -> if (light) 0xFF1F6A80 else 0xFF7FC4D0
            LookPalette.FOREST -> if (light) 0xFF4E7A2A else 0xFFA9C98A
            LookPalette.BERRY -> if (light) 0xFF9E3A62 else 0xFFE0A0BC
            LookPalette.AURORA -> if (light) 0xFF4E3FA0 else 0xFFB4A8F0
            LookPalette.GRAPHITE -> if (light) 0xFF2B211B else 0xFFEDE5D8
        }
    )

    private fun flatWorlds(base: Color, tint: Color): Map<BackdropWorld, BackdropSpec> {
        val spec = BackdropSpec(listOf(base, base), listOf(BackdropOrb(tint, 0.85f, 0.0f, 0.6f, 0.06f)))
        return BackdropWorld.entries.associateWith { spec }
    }

    fun paperLight(p: LookPalette): FitPalColors {
        val paper = Color(0xFFF3EDE2)
        val accent = paperAccent(p, true)
        return build(
            isLight = true, text = Color(0xFF2B211B), inkBlack = paper, ink = Color(0xFFFBF8F2),
            glassFill = Color(0xFFFBF8F2), glassFillSoft = Color(0xFFF7F2E9),
            glassBorder = Color(0xFFE0D5C2), glassBorderSoft = Color(0xFFE9E0D0),
            overlay = Color(0xFFFBF8F2), gold = accent, goldLight = accent, onGold = Color(0xFFFBF8F2),
            backdrops = flatWorlds(paper, accent), backdropShade = 0f
        )
    }

    fun paperDark(p: LookPalette): FitPalColors {
        val paper = Color(0xFF1C1A17)
        val accent = paperAccent(p, false)
        return build(
            isLight = false, text = Color(0xFFEDE5D8), inkBlack = paper, ink = Color(0xFF211E1B),
            glassFill = Color(0xFF27231F), glassFillSoft = Color(0xFF2B2722),
            glassBorder = Color(0xFF3B342D), glassBorderSoft = Color(0xFF332D27),
            overlay = Color(0xFF27231F), gold = accent, goldLight = accent, onGold = Color(0xFF1C1A17),
            backdrops = flatWorlds(paper, accent), backdropShade = 0f
        )
    }

    // ================= MATERIAL YOU =================
    private fun materialSeed(p: LookPalette, light: Boolean): Triple<Color, Color, Color> = when (p) {
        // primary, primaryContainer-ish (cards), onPrimary
        LookPalette.EMBER -> if (light) Triple(Color(0xFF8B5000), Color(0xFFF7EDE4), Color(0xFFFFFFFF)) else Triple(Color(0xFFFFB86E), Color(0xFF2A2119), Color(0xFF4A2800))
        LookPalette.OCEAN -> if (light) Triple(Color(0xFF006A62), Color(0xFFE6F2F0), Color(0xFFFFFFFF)) else Triple(Color(0xFF4FDBCE), Color(0xFF16221F), Color(0xFF003732))
        LookPalette.FOREST -> if (light) Triple(Color(0xFF3B6939), Color(0xFFEBF2E4), Color(0xFFFFFFFF)) else Triple(Color(0xFFA1D39A), Color(0xFF1C211A), Color(0xFF0A390F))
        LookPalette.BERRY -> if (light) Triple(Color(0xFF8E4A6C), Color(0xFFF8EAF0), Color(0xFFFFFFFF)) else Triple(Color(0xFFFFB0D2), Color(0xFF261C21), Color(0xFF561D3C))
        LookPalette.AURORA -> if (light) Triple(Color(0xFF6750A4), Color(0xFFF3EDF7), Color(0xFFFFFFFF)) else Triple(Color(0xFFD0BCFF), Color(0xFF211F26), Color(0xFF381E72))
        LookPalette.GRAPHITE -> if (light) Triple(Color(0xFF4A4A55), Color(0xFFF0F0F3), Color(0xFFFFFFFF)) else Triple(Color(0xFFC6C6D0), Color(0xFF1F1F23), Color(0xFF2E2E36))
    }

    fun material(p: LookPalette, light: Boolean, dynamic: androidx.compose.material3.ColorScheme?): FitPalColors {
        val (seedPrimary, seedCard, seedOn) = materialSeed(p, light)
        val primary = dynamic?.primary ?: seedPrimary
        val surface = dynamic?.surface ?: if (light) Color(0xFFFEF7FF) else Color(0xFF141218)
        val card = dynamic?.surfaceContainer ?: seedCard
        val cardSoft = dynamic?.surfaceContainerHigh ?: lerp(seedCard, if (light) Color.Black else Color.White, 0.04f)
        val text = dynamic?.onSurface ?: if (light) Color(0xFF1D1B20) else Color(0xFFE6E0E9)
        val outline = dynamic?.outlineVariant ?: if (light) Color(0xFFCAC4D0) else Color(0xFF49454F)
        return build(
            isLight = light, text = text, inkBlack = surface, ink = surface,
            glassFill = card, glassFillSoft = cardSoft,
            glassBorder = outline.copy(alpha = 0f), glassBorderSoft = outline.copy(alpha = 0f),
            overlay = dynamic?.surfaceContainerHigh ?: cardSoft,
            gold = primary, goldLight = primary, onGold = dynamic?.onPrimary ?: seedOn,
            backdrops = flatWorlds(surface, primary), backdropShade = 0f
        )
    }
}
