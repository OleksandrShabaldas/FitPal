package com.fitpal.app.ml

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.fitpal.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when Gemini's daily/per-minute free quota is used up (HTTP 429). */
class GeminiQuotaException(message: String) : Exception(message)

/** Thrown for any other reason the online model can't answer (no key, network, 4xx/5xx, blocked). */
class GeminiUnavailableException(message: String) : Exception(message)

/**
 * Minimal REST client for Google's Gemini API (generativelanguage v1beta), used by
 * [RemoteIngredientEngine]. Plain [HttpURLConnection] — no SDK, no extra Gradle deps —
 * mirroring the style of [com.fitpal.app.data.repository.BarcodeRepository].
 *
 * The API key is the user's own (pasted in Settings, stored by [SettingsRepository]); it's
 * read fresh on every call so changing it takes effect without restarting the app.
 *
 * Failures are deliberately typed so the router ([RoutingIngredientEngine]) can tell
 * "out of quota for today" (→ stop trying till tomorrow) apart from a one-off hiccup.
 */
@Singleton
class GeminiClient @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    /** True when the user has pasted a key — cheap pre-check before attempting a call. */
    fun hasKey(): Boolean = !settingsRepository.geminiApiKey.value.isNullOrBlank()

    /**
     * Send one prompt (plus optional images) and return the model's text reply.
     * When [jsonMode] is true we ask Gemini for strictly-JSON output (responseMimeType),
     * which is far more reliable than coaxing JSON out of a small on-device model.
     *
     * @throws GeminiQuotaException on HTTP 429 (free quota exhausted)
     * @throws GeminiUnavailableException on any other failure (no key, network, 4xx/5xx, empty/blocked reply)
     */
    suspend fun generate(
        prompt: String,
        images: List<Bitmap> = emptyList(),
        temperature: Float = 0.2f,
        jsonMode: Boolean = true,
        // Gemini 3 is a "thinking" model whose default ("medium") is slow. Food extraction doesn't
        // need deep reasoning, so default to "low" for much faster replies. null = leave it default.
        thinkingLevel: String? = "low",
        // Reports the live stage (encoding / sending / waiting / retrying) for the UI tooltip.
        onProgress: (String) -> Unit = {},
        // Reports WHICH model finally answered — the cascade below may have moved past the first
        // one — so the UI can name it on the badge instead of just saying "online".
        onModel: (String) -> Unit = {},
        // Which models to cascade through. Null = the main analysis models; the dietary-rule check
        // passes its own dedicated fast trio so it never eats the analysis models' free quota.
        models: List<String>? = null,
        // Fast mode for the lightweight dietary-rule check: a single attempt and short socket
        // timeouts, so a slow network can't stall a log (it falls back to the offline heuristic).
        fast: Boolean = false
    ): String {
        val key = settingsRepository.geminiApiKey.value?.trim()
        if (key.isNullOrBlank()) throw GeminiUnavailableException("No Gemini API key set")

        // Try each configured model in order, skipping ones already out of quota today. On a 429
        // mark that model spent and move to the next; only when all are spent do we report quota.
        val candidates = (models ?: settingsRepository.activeModels()).filter { !settingsRepository.isModelQuotaExhaustedToday(it) }
        if (candidates.isEmpty()) throw GeminiQuotaException("All AI models are out of free quota for today")

        var allQuota = true
        var lastFailure = "Gemini request failed"
        for ((index, model) in candidates.withIndex()) {
            if (index > 0) onProgress("Switching to fallback model: $model…")
            try {
                val text = requestModel(model, prompt, images, temperature, jsonMode, thinkingLevel, key, onProgress, fast)
                onModel(model)
                return text
            } catch (e: GeminiQuotaException) {
                settingsRepository.markModelQuotaExhausted(model)
                onProgress("$model is out of free quota — trying the next model…")
            } catch (e: GeminiUnavailableException) {
                allQuota = false
                lastFailure = e.message ?: "Gemini request failed"
                // A 404 (bad id) or persistent 5xx might still work on another model — keep going.
            }
        }
        if (allQuota) throw GeminiQuotaException("All AI models are out of free quota for today")
        throw GeminiUnavailableException(lastFailure)
    }

    /**
     * Test a single model directly (bypasses the quota cascade) — used by Settings'
     * "Test connection" so it can report each configured model separately.
     */
    suspend fun pingModel(model: String): String {
        val key = settingsRepository.geminiApiKey.value?.trim()
        if (key.isNullOrBlank()) throw GeminiUnavailableException("No Gemini API key set")
        return requestModel(
            model = model,
            prompt = "Reply with exactly: OK",
            images = emptyList(),
            temperature = 0f,
            jsonMode = false,
            thinkingLevel = "low",
            key = key,
            onProgress = {}
        )
    }

    /** One model's request, with transient-5xx/network retry + thinkingConfig self-heal. */
    private suspend fun requestModel(
        model: String,
        prompt: String,
        images: List<Bitmap>,
        temperature: Float,
        jsonMode: Boolean,
        thinkingLevel: String?,
        key: String,
        onProgress: (String) -> Unit,
        fast: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        if (images.isNotEmpty()) onProgress("Compressing and encoding the photo…")
        // Rebuildable so we can drop thinkingConfig and retry if a model rejects it (see below).
        var currentThinking = thinkingLevel
        var body = buildRequest(prompt, images, temperature, jsonMode, currentThinking).toString()
        val url = URL("$BASE_URL/models/$model:generateContent")
        // Retry transient failures (503 "high demand", other 5xx, network blips) with back-off
        // before giving up — those usually clear in a second or two. A 429 (quota) or a 4xx
        // (bad key/model/request) is definitive, so we don't waste retries on it. Fast mode does a
        // single attempt with short timeouts so it can't stall a log.
        val attempts = if (fast) 1 else MAX_ATTEMPTS
        var lastFailure = "Gemini request failed"
        repeat(attempts) { attempt ->
            var connection: HttpURLConnection? = null
            onProgress(if (attempt == 0) "Sending the request to the online AI…"
                       else "Retrying online (attempt ${attempt + 1}/$attempts)…")
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = if (fast) 8_000 else 30_000
                    // Generous for analysis: a multimodal "thinking" model analysing a photo can take
                    // a while. Fast mode keeps it short so a bad network falls back quickly instead.
                    readTimeout = if (fast) 12_000 else 180_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("x-goog-api-key", key)
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                // Fetching the response code blocks until the model has finished thinking + answering
                // — that's the long, silent minute(s) the user stares at. Tick a live elapsed timer +
                // rotating reassurance on a *separate* thread while this thread is blocked, so the UI
                // shows something is actually happening instead of one frozen "waiting…" line.
                // (Bind to a non-null local first — the heartbeat lambda captures it, and a captured
                // nullable `var` can't be smart-cast.)
                val conn = connection
                val code = awaitWithHeartbeat(onProgress) { conn.responseCode }
                when {
                    code == 429 -> {
                        val err = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                        // A per-DAY 429 is genuinely done until midnight — report quota so the caller
                        // benches this model for today. A per-MINUTE (RPM/TPM) 429 just means "too
                        // fast": treat it as transient so the model is NOT benched — the caller
                        // cascades to the next model (a separate per-minute bucket) or falls back for
                        // this one call, and the model is usable again the next minute. On the free
                        // tier (≈5 req/min) the per-minute limit is easy to hit in a burst, so wrongly
                        // benching for the whole day was making every later call fall to a fallback.
                        if (isPerDayQuota(err)) throw GeminiQuotaException("Gemini daily free quota exhausted")
                        Log.w(TAG, "Per-minute rate limit on $model (transient): ${err?.take(200)}")
                        throw GeminiUnavailableException("Gemini rate-limited (per-minute)")
                    }
                    code in 200..299 -> {
                        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                        return@withContext extractText(responseText)
                    }
                    code in 500..599 -> {
                        // Transient server overload — note it and let the loop retry.
                        val err = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                        lastFailure = if (code == 503) "Gemini is busy right now (HTTP 503)" else "Gemini server error (HTTP $code)"
                        if (attempt < attempts - 1) onProgress("Online AI is busy (HTTP $code) — waiting to retry…")
                        Log.w(TAG, "Transient HTTP $code (attempt ${attempt + 1}/$attempts): ${err?.take(300)}")
                    }
                    else -> {
                        // Definitive client error (400 bad request, 403 bad key, 404 bad model) — don't retry.
                        val err = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                        // Self-heal: if a model rejects thinkingConfig with a 400, drop it and retry
                        // (so the speed tweak can never break online for a model that lacks it).
                        if (code == 400 && currentThinking != null && err?.contains("think", ignoreCase = true) == true) {
                            Log.w(TAG, "Model rejected thinkingConfig — retrying without it.")
                            currentThinking = null
                            body = buildRequest(prompt, images, temperature, jsonMode, null).toString()
                            lastFailure = "Gemini HTTP 400 (thinkingConfig)"
                        } else {
                            throw GeminiUnavailableException("Gemini HTTP $code${if (err.isNullOrBlank()) "" else ": ${err.take(300)}"}")
                        }
                    }
                }
            } catch (e: GeminiQuotaException) {
                throw e
            } catch (e: GeminiUnavailableException) {
                throw e
            } catch (e: Exception) {
                // Network drop / timeout — transient, worth a retry.
                lastFailure = e.message ?: "Network error"
                if (attempt < attempts - 1) onProgress("Connection issue — waiting to retry…")
                Log.w(TAG, "Network error (attempt ${attempt + 1}/$attempts): ${e.message}")
            } finally {
                connection?.disconnect()
            }
            // Back off before the next attempt (skip after the last one).
            if (attempt < attempts - 1) delay(RETRY_BACKOFF_MS * (attempt + 1))
        }
        throw GeminiUnavailableException(lastFailure)
    }

    /**
     * Tell a per-DAY quota 429 (bench this model till midnight) from a per-MINUTE one (transient).
     * Google's 429 body names the quota it hit, e.g. `...RequestsPerDay...` vs `...PerMinute...`.
     * When the body is missing/unclear we assume per-minute — the safer guess, since benching a good
     * model for a whole day on a momentary rate limit is exactly the failure we're avoiding.
     */
    private fun isPerDayQuota(errorBody: String?): Boolean {
        if (errorBody.isNullOrBlank()) return false
        val s = errorBody.lowercase().replace(" ", "")
        return s.contains("perday") || s.contains("requestsperday")
    }

    /**
     * Run a blocking [block] (the `connection.responseCode` wait) while a heartbeat ticks progress on
     * a *separate* thread. The network read blocks its IO thread the whole time; the ticker runs on
     * [Dispatchers.Default] so it keeps updating an elapsed-time message regardless. Cancelled the
     * instant [block] returns (or throws), so it never outlives the wait.
     */
    private suspend fun <T> awaitWithHeartbeat(onProgress: (String) -> Unit, block: () -> T): T = coroutineScope {
        val start = System.currentTimeMillis()
        onProgress(waitingMessage(0))
        val ticker = launch(Dispatchers.Default) {
            while (isActive) {
                delay(HEARTBEAT_MS)
                onProgress(waitingMessage(((System.currentTimeMillis() - start) / 1000).toInt()))
            }
        }
        try {
            block()
        } finally {
            ticker.cancel()
        }
    }

    /**
     * The live "still working" line during the wait: a real elapsed-seconds count plus a phrase that
     * escalates with time, so a long wait reads as steady progress (and sets the expectation that a
     * deep look can take a minute) instead of a single frozen "waiting…".
     */
    private fun waitingMessage(seconds: Int): String = when {
        seconds < 8 -> "Waiting for the AI to reply… (${seconds}s)"
        seconds < 22 -> "The AI is working through it… (${seconds}s)"
        seconds < 45 -> "Still going — a careful look takes a moment… (${seconds}s)"
        seconds < 90 -> "Deep analysis — nearly there… (${seconds}s)"
        else -> "Almost done — thanks for hanging on… (${seconds}s)"
    }

    private fun buildRequest(
        prompt: String,
        images: List<Bitmap>,
        temperature: Float,
        jsonMode: Boolean,
        thinkingLevel: String?
    ): JSONObject {
        val parts = JSONArray().apply {
            put(JSONObject().put("text", prompt))
            images.forEach { bmp ->
                put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", encodeJpeg(bmp))
                    )
                )
            }
        }
        // No maxOutputTokens cap on purpose: Gemini 3 is a "thinking" model, so a tight cap
        // can be eaten by reasoning and leave the JSON answer empty/truncated. Let it use its
        // own default ceiling.
        val generationConfig = JSONObject()
            .put("temperature", temperature.toDouble())
        if (jsonMode) generationConfig.put("responseMimeType", "application/json")
        // Dial down reasoning for speed (Gemini 3 only). Harmless if the model ignores it.
        if (!thinkingLevel.isNullOrBlank()) {
            generationConfig.put("thinkingConfig", JSONObject().put("thinkingLevel", thinkingLevel))
        }

        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", generationConfig)
    }

    /** Pull the concatenated text out of the first candidate; throw if the reply is empty/blocked. */
    private fun extractText(responseBody: String): String {
        val root = runCatching { JSONObject(responseBody) }.getOrNull()
            ?: throw GeminiUnavailableException("Gemini returned non-JSON")

        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
        if (candidate == null) {
            val block = root.optJSONObject("promptFeedback")?.optString("blockReason")
            Log.w(TAG, "No candidate in response: ${responseBody.take(500)}")
            throw GeminiUnavailableException(
                if (block.isNullOrBlank()) "Gemini returned no answer" else "Gemini blocked the request ($block)"
            )
        }
        val finishReason = candidate.optString("finishReason").takeIf { it.isNotBlank() && it != "STOP" }
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")

        val text = buildString {
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    // Gemini 3 is a thinking model; skip any "thought" parts so reasoning text
                    // never leaks into the answer we parse as JSON.
                    if (part.optBoolean("thought", false)) continue
                    part.optString("text").let { if (it.isNotEmpty()) append(it) }
                }
            }
        }.trim()

        if (text.isEmpty()) {
            // Log the raw body so the real cause (finishReason MAX_TOKENS / SAFETY / RECITATION,
            // an unexpected shape, …) is visible in logcat under the "GeminiClient" tag.
            Log.w(TAG, "Empty answer (finishReason=$finishReason): ${responseBody.take(800)}")
            throw GeminiUnavailableException(
                "Gemini returned an empty answer" + (finishReason?.let { " (finishReason: $it)" } ?: "")
            )
        }
        return text
    }

    private fun encodeJpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        private const val TAG = "GeminiClient"
        // Gemini 3 models are only served on the v1beta endpoint. The model id itself is a
        // user setting (SettingsRepository.geminiModel) so it can be corrected without a rebuild.
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        // Retry transient overloads: 3 tries total, backing off 1.5s then 3s (~4.5s worst case
        // of extra waiting before we fall back to the on-device model).
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_BACKOFF_MS = 1_500L

        // How often the "still working…" progress line refreshes its elapsed count during the wait.
        private const val HEARTBEAT_MS = 2_000L
    }
}
