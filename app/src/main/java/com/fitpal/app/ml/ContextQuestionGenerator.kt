package com.fitpal.app.ml

import com.fitpal.app.data.repository.ContextNoteRepository
import com.fitpal.app.domain.model.ContextQuestion
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether to ask the user a check-in question before a review, and generates it. The
 * question and its answer chips are written by the AI from the day/week's actual data, so they're
 * specific ("Today ran higher than usual — what happened?") rather than a fixed form.
 *
 * Deliberately quiet: only online (the on-device model asks poor, generic questions), only for a
 * daily review when the day is genuinely unusual, or a weekly review (always, as a reflection), and
 * never twice for the same period.
 */
@Singleton
class ContextQuestionGenerator @Inject constructor(
    private val nutritionAnalytics: NutritionAnalytics,
    private val contextNoteRepository: ContextNoteRepository,
    private val pipeline: FoodAnalysisPipeline
) {
    /**
     * Return a question to ask before generating the [period]/[periodKey] review, or null to skip
     * straight to the review. Never throws — any failure just means "don't ask".
     */
    suspend fun maybeGenerate(period: String, periodKey: String): ContextQuestion? {
        // The prompt needs the capable cloud model to produce specific, safe questions.
        if (!pipeline.canUseOnline()) return null

        val isDaily = period.equals("daily", ignoreCase = true)
        val isWeekly = period.equals("weekly", ignoreCase = true)
        if (!isDaily && !isWeekly) return null   // monthly: don't interrupt

        // Never re-ask for a period the user already answered (or skipped).
        if (contextNoteRepository.getForPeriod(period, periodKey) != null) return null

        val brief = runCatching { nutritionAnalytics.computeBrief(briefRefDate(period, periodKey), isDaily) }
            .getOrNull() ?: return null

        // Not enough history to know what "unusual" even is.
        if (brief.daysWithData < 3) return null
        if (isDaily) {
            if (brief.isSparse) return null
            if (!shouldAskDaily(brief)) return null
        }

        val summary = brief.toPromptBlock().ifBlank { return null }
        val prompt = FoodPrompts.contextQuestion(summary, if (isDaily) "day" else "week")
        val response = runCatching { pipeline.generateRawTextWithSource(prompt) }.getOrNull()?.first
            ?: return null
        return FoodJsonParser.parseContextQuestion(response)
    }

    /** A daily review is worth a question only when the day actually stands out. */
    private fun shouldAskDaily(brief: NutritionBrief): Boolean {
        brief.todayVsAvg?.let { c ->
            if (c.caloriesPct > 0.4f || c.caloriesPct < -0.4f) return true
            if (c.flags.isNotEmpty()) return true   // low protein / fat-heavy already flagged
        }
        if (brief.todayOutlier) return true
        if (brief.consecutiveUnloggedBeforeToday >= 2) return true
        return false
    }

    /** The date the brief is anchored at: the day itself (daily) or the week's end, capped at today. */
    private fun briefRefDate(period: String, periodKey: String): String {
        val today = LocalDate.now()
        return if (period.equals("weekly", ignoreCase = true)) {
            val start = runCatching { LocalDate.parse(periodKey) }.getOrDefault(today.minusDays(6))
            minOf(start.plusDays(6), today).toString()
        } else {
            periodKey.ifBlank { today.toString() }
        }
    }
}
