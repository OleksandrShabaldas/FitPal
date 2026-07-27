package com.fitpal.app.domain.model

/**
 * AI-generated insights about a meal — shown on the Analysis screen after the
 * food has been identified.  Not stored long-term; computed on the fly.
 */
data class MealInsights(
    /** 1-10 overall healthiness rating. */
    val healthScore: Int,
    /** What drove the score up or down. */
    val scoreFactors: List<ScoreFactor>,
    /** Healthier alternatives the user could swap in (empty if score is 10). */
    val healthSwaps: List<HealthSwap>,
    /** Plain-language description of how this meal affects energy levels. */
    val energyImpact: String,
    /** Plain-language description of how this meal may affect mood. */
    val moodImpact: String,
    /** "Eat this alongside …" recommendations (empty if nothing useful). */
    val pairingRecommendations: List<String>,
    /** Quick 1-5 rating of energy impact (1 = crash/heavy, 5 = light & energizing). 0 = unknown. */
    val energyScore: Int = 0,
    /** Quick 1-5 rating of mood impact (1 = may drag you down, 5 = mood-lifting). 0 = unknown. */
    val moodScore: Int = 0
)

data class ScoreFactor(
    val description: String,
    /** True = positive contributor ("Good protein"), false = negative ("High sodium"). */
    val isPositive: Boolean
)

data class HealthSwap(
    /** The ingredient to replace, e.g. "White rice". */
    val original: String,
    /** The healthier substitute, e.g. "Brown rice". */
    val suggestion: String,
    /** Why it's healthier, e.g. "More fiber, slower energy release". */
    val benefit: String
)
