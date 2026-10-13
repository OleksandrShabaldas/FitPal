package com.fitpal.app.ml

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

// The staggered model race behind GeminiClient's hedged calls. Kept apart from the HTTP client (and
// free of Android) so its timing rules can be reasoned about — and exercised — on their own.

/**
 * Run [attempt] across [candidates] as a staggered race: the first model starts at once; a
 * failure moves straight on to the next model (the usual cascade); and if the newest attempt is
 * still silent after [hedgeAfterMs], one extra attempt starts in parallel on the next model (or
 * the same one again when it's the only model — a fresh request often lands on a faster server).
 * The first answer wins and every other attempt is cancelled, which closes its connection.
 *
 * Only the attempt that started first narrates progress (so the elapsed-seconds count keeps
 * climbing instead of jumping back to 0); if it fails, the newest attempt still running takes over.
 *
 * @return the model that answered, and its reply.
 */
internal suspend fun raceModels(
    candidates: List<String>,
    hedgeAfterMs: Long,
    onProgress: (String) -> Unit,
    /** Called when a model reports its daily quota is spent (so it's benched till midnight). */
    onQuotaExhausted: (String) -> Unit,
    attempt: suspend (model: String, progress: (String) -> Unit) -> String
): Pair<String, String> = coroutineScope {
    // An attempt's result, or (result == null) the hedge timer going off. Both arrive on one
    // channel so the loop below only ever does a plain receive — nothing can be dropped by a
    // timeout racing an answer.
    class Outcome(val id: Int, val model: String, val result: Result<String>?)
    val outcomes = Channel<Outcome>(Channel.UNLIMITED)
    val running = mutableMapOf<Int, Pair<String, Job>>()
    val narrator = AtomicInteger(0)
    var nextCandidate = 0
    var launched = 0
    var hedged = false
    var timer: Job? = null
    var timerGeneration = 0
    var allQuota = true
    var lastFailure = "Gemini request failed"

    fun start(model: String) {
        val id = launched++
        running[id] = model to launch {
            val result = try {
                Result.success(attempt(model) { msg -> if (narrator.get() == id) onProgress(msg) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            outcomes.send(Outcome(id, model, result))
        }
    }

    /** (Re)start the hedge countdown for the attempt just started. Ticks carry negative ids. */
    fun armHedge() {
        if (hedged) return
        timer?.cancel()
        val generation = ++timerGeneration
        timer = launch {
            delay(hedgeAfterMs)
            outcomes.send(Outcome(-generation, "", null))
        }
    }

    start(candidates[nextCandidate++])
    armHedge()
    try {
        while (true) {
            val outcome = outcomes.receive()
            val result = outcome.result

            if (result == null) {
                // A stale tick (from before a cascade re-armed the timer) or nothing left to hedge.
                if (hedged || outcome.id != -timerGeneration || running.isEmpty()) continue
                // The newest attempt has been silent for hedgeAfterMs — ask another model too:
                // the next untried one, or the same model again if every other one is used up.
                hedged = true
                val model = if (nextCandidate < candidates.size) candidates[nextCandidate++]
                    else running.getValue(running.keys.max()).first
                onProgress("Taking longer than usual — also asking $model…")
                start(model)
                continue
            }

            running.remove(outcome.id)
            result.getOrNull()?.let { text -> return@coroutineScope outcome.model to text }

            val error = result.exceptionOrNull()
            if (error is GeminiQuotaException) {
                onQuotaExhausted(outcome.model)
                onProgress("${outcome.model} is out of free quota — trying the next model…")
            } else {
                allQuota = false
                lastFailure = error?.message ?: lastFailure
            }
            // Hand the progress line to the newest attempt still running.
            if (narrator.get() == outcome.id) running.keys.maxOrNull()?.let { narrator.set(it) }

            if (nextCandidate < candidates.size) {
                val model = candidates[nextCandidate++]
                if (running.isEmpty()) {
                    narrator.set(launched)
                    onProgress("Switching to fallback model: $model…")
                }
                start(model)
                if (running.size == 1) armHedge()
            } else if (running.isEmpty()) {
                if (allQuota) throw GeminiQuotaException("All AI models are out of free quota for today")
                throw GeminiUnavailableException(lastFailure)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException()
    } finally {
        timer?.cancel()
        running.values.forEach { it.second.cancel() }
    }
}
