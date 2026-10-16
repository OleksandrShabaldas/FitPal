package com.fitpal.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * FitPal's colour tokens — read from the current look (see Look.kt / LookState).
 *
 * The names come from the original dark design ("Cream" is the primary text colour, "Gold" the
 * accent, "InkBlack" the deepest base) and keep their *role* in every look: on Paper light, `Cream`
 * is dark ink and `GlassFill` an opaque sheet. They're getters over Compose snapshot state, so they
 * work anywhere (composables, Canvas draw lambdas, plain modifiers) and update when the look changes.
 * Don't copy one into a top-level `val` — it would freeze the look it was first read in.
 */

private val c get() = LookState.colors

// ---- Text ----
val Cream: Color get() = c.cream             // primary text
val CreamDim: Color get() = c.creamDim       // ~80%
val CreamMuted: Color get() = c.creamMuted   // ~55% — secondary text
val CreamFaint: Color get() = c.creamFaint   // ~50% — hints / captions

// ---- Base surfaces ----
val InkBlack: Color get() = c.inkBlack       // deepest base (the backdrop's floor)
val Ink: Color get() = c.ink

// ---- Glass (translucent surfaces over the backdrop; opaque sheets on Paper/Material) ----
val GlassFill: Color get() = c.glassFill
val GlassFillSoft: Color get() = c.glassFillSoft
val GlassBorder: Color get() = c.glassBorder
val GlassBorderSoft: Color get() = c.glassBorderSoft
// Opaque base under floating panels (dialogs, coach-marks) — see Modifier.glassOverlay().
val OverlaySurface: Color get() = c.overlaySurface

// ---- Accent ("Gold" in the original Today look; the look's own accent elsewhere) ----
val Gold: Color get() = c.gold
val GoldLight: Color get() = c.goldLight
/** Icons/text drawn on a gold fill (the nav's "+", gold buttons). */
val OnGold: Color get() = c.onGold
/** Faint fill for empty bar tracks, dividers and chips. */
val TrackColor: Color get() = c.track

/**
 * A see-through film at [alpha] that shows on the current base: white on dark looks, ink on light
 * ones (where a white film would vanish). Use for tracks, dividers and empty cells instead of
 * `Color.White.copy(alpha = …)`.
 */
fun filmColor(alpha: Float): Color =
    if (c.isLight) c.cream.copy(alpha = (alpha * 1.15f).coerceAtMost(1f)) else Color.White.copy(alpha = alpha)

// ---- Daily-score tiers (drive the Home ring colour) ----
val ScoreGood: Color get() = c.scoreGood
val ScoreFair: Color get() = c.scoreFair
val ScoreLow: Color get() = c.scoreLow
val ScorePoor: Color get() = c.scorePoor

// ---- Logged-days 5-band scale (Analytics "Logged days" widgets only) ----
val LoggedBest: Color get() = c.loggedBest
val LoggedGood: Color get() = c.loggedGood
val LoggedMid: Color get() = c.loggedMid
val LoggedLow: Color get() = c.loggedLow
val LoggedWorst: Color get() = c.loggedWorst

// ---- Macro breakdown ----
val ProteinColor: Color get() = c.protein
val FatColor: Color get() = c.fat
val CarbColor: Color get() = c.carb
val FiberColor: Color get() = c.fiber
val MacroOver: Color get() = c.macroOver

// ---- Calorie figure ----
val CalorieColor: Color get() = c.calorie

// ---- Food-limit + caffeine rings (the Home hero drawer) ----
val DessertColor: Color get() = c.dessert
val FriedColor: Color get() = c.fried
val SugaryDrinkColor: Color get() = c.sugaryDrink
val CaffeineColor: Color get() = c.caffeine

// ---- Per-domain accents ----
val AccentToday: Color get() = c.goldLight
val AccentActivity: Color get() = c.accentActivity
val AccentTrends: Color get() = c.accentTrends
/** Fasting's identity colour (timer, ring, streak) — its own hue, not water's blue. */
val FastingColor: Color get() = c.fasting
val AccentGarden: Color get() = c.accentGarden
val AccentRecords: Color get() = c.goldLight

// ---- Legacy meal-insight score colours (used by MealInsightsSection / Analysis) ----
val ScoreGreen: Color get() = c.scoreFair
val ScoreYellow: Color get() = c.gold
val ScoreRed: Color get() = c.scorePoor
