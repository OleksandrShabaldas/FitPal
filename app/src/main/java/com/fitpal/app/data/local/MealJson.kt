package com.fitpal.app.data.local

import com.fitpal.app.domain.model.HealthSwap
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.domain.model.ScoreFactor
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiny JSON (de)serializer for the bits of a logged meal we want to cache:
 *  - the list of ingredients that made up a dish (so the detail screen can
 *    show and edit them later, just like the analysis screen),
 *  - the AI health insights (so we don't have to regenerate them every time).
 *
 * Uses Android's built-in org.json — no extra library needed. All decoders are
 * defensive: a null/blank/corrupt string returns an empty result rather than
 * throwing, so a bad cache never crashes the screen.
 */
object MealJson {

    // ---------------- Ingredients ----------------

    fun encodeIngredients(ingredients: List<Ingredient>): String {
        val arr = JSONArray()
        ingredients.forEach { ing ->
            val o = JSONObject()
            o.put("name", ing.name)
            o.put("grams", ing.grams.toDouble())
            o.put("kcal100", ing.caloriesPer100g.toDouble())
            o.put("pro100", ing.proteinPer100g.toDouble())
            o.put("fat100", ing.fatPer100g.toDouble())
            o.put("carb100", ing.carbsPer100g.toDouble())
            o.put("fib100", ing.fiberPer100g.toDouble())
            o.put("water100", ing.waterMlPer100g.toDouble())
            val m = ing.microsPer100g
            o.put("vitA", m.vitaminAMcg.toDouble())
            o.put("vitC", m.vitaminCMg.toDouble())
            o.put("vitD", m.vitaminDMcg.toDouble())
            o.put("ca", m.calciumMg.toDouble())
            o.put("fe", m.ironMg.toDouble())
            o.put("k", m.potassiumMg.toDouble())
            o.put("na", m.sodiumMg.toDouble())
            o.put("b12", m.vitaminB12Mcg.toDouble())
            o.put("fol", m.folateMcg.toDouble())
            o.put("b6", m.vitaminB6Mg.toDouble())
            o.put("mg", m.magnesiumMg.toDouble())
            o.put("zn", m.zincMg.toDouble())
            o.put("vitE", m.vitaminEMg.toDouble())
            arr.put(o)
        }
        return arr.toString()
    }

    fun decodeIngredients(json: String?): List<Ingredient> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Ingredient(
                    name = o.optString("name"),
                    grams = o.optDouble("grams", 0.0).toFloat(),
                    caloriesPer100g = o.optDouble("kcal100", 0.0).toFloat(),
                    proteinPer100g = o.optDouble("pro100", 0.0).toFloat(),
                    fatPer100g = o.optDouble("fat100", 0.0).toFloat(),
                    carbsPer100g = o.optDouble("carb100", 0.0).toFloat(),
                    fiberPer100g = o.optDouble("fib100", 0.0).toFloat(),
                    waterMlPer100g = o.optDouble("water100", 0.0).toFloat(),
                    microsPer100g = Micronutrients(
                        vitaminAMcg = o.optDouble("vitA", 0.0).toFloat(),
                        vitaminCMg = o.optDouble("vitC", 0.0).toFloat(),
                        vitaminDMcg = o.optDouble("vitD", 0.0).toFloat(),
                        calciumMg = o.optDouble("ca", 0.0).toFloat(),
                        ironMg = o.optDouble("fe", 0.0).toFloat(),
                        potassiumMg = o.optDouble("k", 0.0).toFloat(),
                        sodiumMg = o.optDouble("na", 0.0).toFloat(),
                        vitaminB12Mcg = o.optDouble("b12", 0.0).toFloat(),
                        folateMcg = o.optDouble("fol", 0.0).toFloat(),
                        vitaminB6Mg = o.optDouble("b6", 0.0).toFloat(),
                        magnesiumMg = o.optDouble("mg", 0.0).toFloat(),
                        zincMg = o.optDouble("zn", 0.0).toFloat(),
                        vitaminEMg = o.optDouble("vitE", 0.0).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------------- Insights ----------------

    fun encodeInsights(insights: MealInsights): String {
        val o = JSONObject()
        o.put("score", insights.healthScore)
        o.put("factors", JSONArray().apply {
            insights.scoreFactors.forEach { f ->
                put(JSONObject().apply {
                    put("text", f.description)
                    put("pos", f.isPositive)
                })
            }
        })
        o.put("swaps", JSONArray().apply {
            insights.healthSwaps.forEach { s ->
                put(JSONObject().apply {
                    put("from", s.original)
                    put("to", s.suggestion)
                    put("why", s.benefit)
                })
            }
        })
        o.put("energy", insights.energyImpact)
        o.put("mood", insights.moodImpact)
        o.put("energyScore", insights.energyScore)
        o.put("moodScore", insights.moodScore)
        o.put("pairings", JSONArray().apply {
            insights.pairingRecommendations.forEach { put(it) }
        })
        return o.toString()
    }

    fun decodeInsights(json: String?): MealInsights? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            val factors = o.optJSONArray("factors")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val f = arr.optJSONObject(i) ?: return@mapNotNull null
                    ScoreFactor(f.optString("text"), f.optBoolean("pos", true))
                }
            } ?: emptyList()
            val swaps = o.optJSONArray("swaps")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val s = arr.optJSONObject(i) ?: return@mapNotNull null
                    HealthSwap(s.optString("from"), s.optString("to"), s.optString("why"))
                }
            } ?: emptyList()
            val pairings = o.optJSONArray("pairings")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
            } ?: emptyList()
            MealInsights(
                healthScore = o.optInt("score", 5),
                scoreFactors = factors,
                healthSwaps = swaps,
                energyImpact = o.optString("energy"),
                moodImpact = o.optString("mood"),
                pairingRecommendations = pairings,
                energyScore = o.optInt("energyScore", 0),
                moodScore = o.optInt("moodScore", 0)
            )
        } catch (e: Exception) {
            null
        }
    }
}
