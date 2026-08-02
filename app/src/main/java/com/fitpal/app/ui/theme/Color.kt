package com.fitpal.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * FitPal palette — dark-only "premium" redesign.
 *
 * A warm sunset base with a gold "Today" accent, a four-tier daily-score scale,
 * and dark-tuned macro colours. Per-domain accents give each section its own
 * colour world (see the table in REDESIGN_PLAN.md).
 */

// ---- Text on dark ----
val Cream = Color(0xFFF5ECE0)        // primary text
val CreamDim = Color(0xCCF5ECE0)     // ~80%
val CreamMuted = Color(0x8CF5ECE0)   // ~55% — secondary text
val CreamFaint = Color(0x80F5ECE0)   // ~50% — hints / captions

// ---- Base surfaces ----
val InkBlack = Color(0xFF0E0B0A)     // deepest base
val Ink = Color(0xFF120D0B)

// ---- Glass (translucent surfaces over the backdrop) ----
val GlassFill = Color(0x1AFFFFFF)        // ~10% white — stronger so cards read on the glow
val GlassFillSoft = Color(0x14FFFFFF)    // ~8% white
val GlassBorder = Color(0x33FFFFFF)      // ~20% white hairline — more defined edge
val GlassBorderSoft = Color(0x24FFFFFF)  // ~14% white hairline
// Opaque base under floating panels (dialogs, coach-marks). Glass alone is see-through,
// and over arbitrary content that makes text unreadable — see Modifier.glassOverlay().
val OverlaySurface = Color(0xF01A1310)   // ~94% warm near-black

// ---- Gold "Today" accent ----
val Gold = Color(0xFFE8A93C)
val GoldLight = Color(0xFFF3CE7C)

// ---- Daily-score tiers (drive the Home ring colour) ----
val ScoreGood = Color(0xFFF3CE7C)   // gold
val ScoreFair = Color(0xFF8CC152)   // green
val ScoreLow = Color(0xFF5BA8E0)    // blue
val ScorePoor = Color(0xFFE0584A)   // red

// ---- Logged-days 5-band scale (Analytics "Logged days" widgets only) ----
// A conventional best→worst gradient, kept separate from the gold-based ScoreTier
// colours so it only affects the logged-days heatmap + mini-heatmap tile.
val LoggedBest = Color(0xFF5BA8E0)    // blue   — best  (≥80)
val LoggedGood = Color(0xFF8CC152)    // green  (65–79)
val LoggedMid = Color(0xFFEAC63F)     // yellow (50–64)
val LoggedLow = Color(0xFFE58A3C)     // orange (35–49)
val LoggedWorst = Color(0xFFE0584A)   // red    — worst (<35)

// ---- Macro breakdown (dark-tuned) ----
val ProteinColor = Color(0xFFF0997B)  // warm coral
val FatColor = Color(0xFFF2C14E)      // honey
val CarbColor = Color(0xFFA6CF6E)     // fresh green
val FiberColor = Color(0xFFB89E8A)    // tan
val MacroOver = Color(0xFFE0584A)     // exceeded a "stay-under" limit

// ---- Calorie figure ----
val CalorieColor = Color(0xFFF3CE7C)  // warm gold

// ---- Per-domain accents ----
val AccentToday = Color(0xFFF3CE7C)
val AccentActivity = Color(0xFF5FD0C4)
val AccentTrends = Color(0xFF6FA8FF)
val AccentGarden = Color(0xFF8CC152)
val AccentRecords = Color(0xFFF3CE7C)

// ---- Legacy meal-insight score colours (used by MealInsightsSection / Analysis) ----
val ScoreGreen = Color(0xFF8CC152)
val ScoreYellow = Color(0xFFE8A93C)
val ScoreRed = Color(0xFFE0584A)
