package com.fitpal.app.domain.model

/**
 * Micronutrient amounts — used both "per 100 g" on an ingredient and as
 * "actual" on a logged item (scaled by portion).  All units match standard
 * nutrition-label conventions.
 */
data class Micronutrients(
    val vitaminAMcg: Float = 0f,   // mcg  (vision, immune)
    val vitaminCMg: Float = 0f,    // mg   (immune, antioxidant)
    val vitaminDMcg: Float = 0f,   // mcg  (bones, immune)
    val calciumMg: Float = 0f,     // mg   (bones, muscles)
    val ironMg: Float = 0f,        // mg   (blood, energy)
    val potassiumMg: Float = 0f,   // mg   (heart, muscles)
    val sodiumMg: Float = 0f,      // mg   (electrolytes — watch for excess)
    val vitaminB12Mcg: Float = 0f, // mcg  (nerves, blood — vegans often low)
    val folateMcg: Float = 0f,     // mcg  (cell growth, B9)
    val vitaminB6Mg: Float = 0f,   // mg   (metabolism, brain)
    val magnesiumMg: Float = 0f,   // mg   (muscles, sleep)
    val zincMg: Float = 0f,        // mg   (immune, healing)
    val vitaminEMg: Float = 0f     // mg   (antioxidant, skin)
) {
    /** Scale these per-100 g values to a specific portion. */
    fun scaledTo(grams: Float): Micronutrients {
        val f = grams / 100f
        return Micronutrients(
            vitaminAMcg = vitaminAMcg * f,
            vitaminCMg = vitaminCMg * f,
            vitaminDMcg = vitaminDMcg * f,
            calciumMg = calciumMg * f,
            ironMg = ironMg * f,
            potassiumMg = potassiumMg * f,
            sodiumMg = sodiumMg * f,
            vitaminB12Mcg = vitaminB12Mcg * f,
            folateMcg = folateMcg * f,
            vitaminB6Mg = vitaminB6Mg * f,
            magnesiumMg = magnesiumMg * f,
            zincMg = zincMg * f,
            vitaminEMg = vitaminEMg * f
        )
    }

    operator fun plus(other: Micronutrients) = Micronutrients(
        vitaminAMcg = vitaminAMcg + other.vitaminAMcg,
        vitaminCMg = vitaminCMg + other.vitaminCMg,
        vitaminDMcg = vitaminDMcg + other.vitaminDMcg,
        calciumMg = calciumMg + other.calciumMg,
        ironMg = ironMg + other.ironMg,
        potassiumMg = potassiumMg + other.potassiumMg,
        sodiumMg = sodiumMg + other.sodiumMg,
        vitaminB12Mcg = vitaminB12Mcg + other.vitaminB12Mcg,
        folateMcg = folateMcg + other.folateMcg,
        vitaminB6Mg = vitaminB6Mg + other.vitaminB6Mg,
        magnesiumMg = magnesiumMg + other.magnesiumMg,
        zincMg = zincMg + other.zincMg,
        vitaminEMg = vitaminEMg + other.vitaminEMg
    )

    /** True when every field is zero — no point displaying. */
    val isEmpty: Boolean
        get() = vitaminAMcg == 0f && vitaminCMg == 0f && vitaminDMcg == 0f &&
            calciumMg == 0f && ironMg == 0f && potassiumMg == 0f && sodiumMg == 0f &&
            vitaminB12Mcg == 0f && folateMcg == 0f && vitaminB6Mg == 0f &&
            magnesiumMg == 0f && zincMg == 0f && vitaminEMg == 0f
}
