package com.fitpal.app.ml

import android.content.Context
import android.graphics.Bitmap
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.domain.DailyTargets
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.UserProfile
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device engine powered by the multimodal Gemma 3n model via MediaPipe. This is the
 * OFFLINE fallback: it always works (once the model is downloaded), with no network.
 *
 * - Text: [describeMeal] uses plain text generation for Describe-to-AI.
 * - Vision: [analyzeImage] opens a vision-enabled session, sends the photo, and
 *   gets back every food on the plate.
 * - Dish: [identifyDish] names the dish from loose ingredients.
 * - Insights: [analyzeMealInsights] scores the meal and suggests improvements.
 *
 * The prompts it sends ([FoodPrompts]) and the parsing of replies ([FoodJsonParser]) are
 * shared with the online engine ([RemoteIngredientEngine]) so both speak one JSON contract.
 *
 * The model is loaded once (slow, ~15-40s the first time) and reused. All access
 * is serialized because the runtime handles one request at a time.
 * Constructed in AppModule as a singleton.
 */
class LlmIngredientEngine(
    private val context: Context,
    private val modelManager: ModelManager
) : IngredientEngine {

    private var llm: LlmInference? = null
    private val mutex = Mutex()

    private suspend fun ensureLoaded() {
        if (llm != null) return
        mutex.withLock {
            if (llm == null) {
                val file = modelManager.fileFor(ModelId.LLM)
                check(file.exists()) { "AI model not downloaded" }
                val options = LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    // Roomier context: the longer prompts + full 13-micronutrient JSON for a
                    // multi-item plate can exceed 2048 and get truncated (which loses foods).
                    .setMaxTokens(4096)
                    .setMaxNumImages(1)
                    .build()
                llm = withContext(Dispatchers.IO) {
                    LlmInference.createFromOptions(context, options)
                }
            }
        }
    }

    // ---- Text path: Describe-to-AI -> foods ----

    override suspend fun describeMeal(text: String): List<DetectedFood> {
        ensureLoaded()
        val engine = llm ?: return emptyList()
        val prompt = FoodPrompts.describe(text)
        val raw = mutex.withLock {
            withContext(Dispatchers.Default) {
                val sessionOptions = LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.15f)
                    .build()
                LlmInferenceSession.createFromOptions(engine, sessionOptions).use { session ->
                    session.addQueryChunk(prompt)
                    session.generateResponse()
                }
            }
        }
        return FoodJsonParser.parseFoods(raw)
    }

    // ---- Vision path: photo -> every food on the plate ----

    override suspend fun analyzeImage(
        bitmap: Bitmap,
        note: String,
        onProgress: (String) -> Unit
    ): List<DetectedFood> {
        // The slow model-into-memory load only happens the first time per app run.
        if (llm == null) onProgress("Loading the AI — first time only, this can take a moment…")
        ensureLoaded()
        val engine = llm ?: return emptyList()
        onProgress("Looking at your food…")
        val raw = mutex.withLock {
            withContext(Dispatchers.Default) {
                val sessionOptions = LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.2f)
                    .setGraphOptions(
                        GraphOptions.builder().setEnableVisionModality(true).build()
                    )
                    .build()
                LlmInferenceSession.createFromOptions(engine, sessionOptions).use { session ->
                    session.addQueryChunk(FoodPrompts.vision(note))
                    session.addImage(BitmapImageBuilder(bitmap).build())
                    session.generateResponse()
                }
            }
        }
        onProgress("Reading the ingredients…")
        return FoodJsonParser.parseFoods(raw)
    }

    // ---- Second pass: loose ingredients -> candidate dishes ----

    override suspend fun identifyDish(
        ingredients: List<Ingredient>,
        image: Bitmap?,
        note: String
    ): List<DetectedFood> {
        if (ingredients.isEmpty()) return emptyList()
        ensureLoaded()
        val engine = llm ?: return emptyList()
        val raw = mutex.withLock {
            withContext(Dispatchers.Default) {
                val builder = LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.2f)
                // When we have the photo, name the dish from what's actually on the plate.
                if (image != null) {
                    builder.setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
                }
                LlmInferenceSession.createFromOptions(engine, builder.build()).use { session ->
                    session.addQueryChunk(FoodPrompts.dish(ingredients, note))
                    if (image != null) session.addImage(BitmapImageBuilder(image).build())
                    session.generateResponse()
                }
            }
        }
        return FoodJsonParser.parseDishCandidates(raw, ingredients)
    }

    // ---- Third pass: health insights ----

    override suspend fun analyzeMealInsights(foods: List<DetectedFood>): MealInsights {
        ensureLoaded()
        val engine = llm ?: return FoodJsonParser.EMPTY_INSIGHTS
        val raw = mutex.withLock {
            withContext(Dispatchers.Default) {
                val sessionOptions = LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.3f)
                    .build()
                LlmInferenceSession.createFromOptions(engine, sessionOptions).use { session ->
                    session.addQueryChunk(FoodPrompts.insights(foods))
                    session.generateResponse()
                }
            }
        }
        return FoodJsonParser.parseInsights(raw)
    }

    // ---- AI nutrition review ----

    override suspend fun generateNutritionReview(
        period: String,
        rows: List<DailyNutritionRow>,
        totalDays: Int,
        profile: UserProfile,
        targets: DailyTargets?,
        weights: List<WeightEntryEntity>,
        foodLog: String,
        extraContext: String
    ): String {
        ensureLoaded()
        val engine = llm ?: return "AI model not loaded."
        val prompt = FoodPrompts.review(period, rows, totalDays, profile, targets, weights, foodLog, extraContext)
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                val sessionOptions = LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.5f)
                    .build()
                LlmInferenceSession.createFromOptions(engine, sessionOptions).use { session ->
                    session.addQueryChunk(prompt)
                    session.generateResponse()
                }
            }
        }
    }

    override suspend fun generateRawText(prompt: String): String {
        val engine = llm ?: throw IllegalStateException("Model not loaded")
        return withContext(Dispatchers.Default) {
            val sessionOptions = LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTemperature(0.5f)
                .build()
            LlmInferenceSession.createFromOptions(engine, sessionOptions).use { session ->
                session.addQueryChunk(prompt)
                session.generateResponse()
            }
        }
    }

    override fun close() {
        llm?.close()
        llm = null
    }
}
