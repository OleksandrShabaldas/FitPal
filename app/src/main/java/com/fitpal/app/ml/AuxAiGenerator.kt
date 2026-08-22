package com.fitpal.app.ml

import android.util.Log
import com.fitpal.app.data.repository.SettingsRepository
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the app's **non-essential** AI extras — the meal coaching tip and the review check-in
 * question — on the dedicated **fast/lite** model trio ([SettingsRepository.activeFastModels], the
 * same flash-lite models the dietary classifier uses), never on the main analysis models.
 *
 * Why this exists: on the free tier the main analysis models are capped very low (≈5 req/min,
 * 20/day), and photo analysis + per-item insights already lean on them. If these "nice to have"
 * extras also competed for that quota, a couple of meals would exhaust it and everything would
 * cascade to fallbacks (or stall). The lite trio has far more headroom (≈15/min, 500/day) and is on
 * a separate quota, so the showcase review keeps the premium models to itself.
 *
 * No thinking, single fast attempt: these are one short sentence / one short question — they never
 * needed deep reasoning, and a quick attempt means they can never stall a log or a review. Fails
 * open and quiet: any problem returns null and the caller simply shows no tip / asks no question.
 */
@Singleton
class AuxAiGenerator @Inject constructor(
    private val gemini: GeminiClient,
    private val settings: SettingsRepository,
    private val networkMonitor: NetworkMonitor
) {
    /** Whether the lite trio can run right now (key + master switch + network + some daily quota left). */
    fun canUseOnline(): Boolean =
        settings.onlineAiEnabled.value &&
            gemini.hasKey() &&
            networkMonitor.isOnline() &&
            settings.activeFastModels().any { !settings.isModelQuotaExhaustedToday(it) }

    /** Generate on the lite trio, or null on any failure/timeout. [jsonMode] asks for strict JSON. */
    suspend fun generate(prompt: String, jsonMode: Boolean = true): String? =
        withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                gemini.generate(
                    prompt = prompt,
                    temperature = 0.3f,
                    jsonMode = jsonMode,
                    // Lite models are non-thinking — don't send a thinkingConfig they'd reject.
                    thinkingLevel = null,
                    models = settings.activeFastModels(),
                    // Single attempt + short socket timeouts, so an extra can never stall the app.
                    fast = true
                )
            }.onFailure { Log.d(TAG, "Aux generate failed (ignored): ${it.message}") }.getOrNull()
        }

    private companion object {
        const val TAG = "AuxAiGenerator"
        // Outer bound just above GeminiClient's fast-mode socket timeouts (~8s connect + 12s read).
        const val TIMEOUT_MS = 24_000L
    }
}
