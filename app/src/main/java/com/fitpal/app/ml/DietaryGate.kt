package com.fitpal.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.model.DietaryRule
import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.domain.model.DietaryRuleStatus
import com.fitpal.app.domain.model.DietaryWarning
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pre-log gate for dietary rules: "you're near your dessert limit and this looks like a dessert
 * — log it anyway?". Returns a [DietaryWarning] to show, or null to just proceed. Every method is
 * **best-effort and fails open** (returns null on any error) so it can never block a log.
 *
 * It's deliberately frugal:
 *  - It only ever fires for **today** (back-filling history never nags).
 *  - It skips the AI classify entirely unless a rule could actually be triggered — either it's
 *    already near/over its cap, or (for known-calorie foods) this addition could cross it. Most logs,
 *    most of the time, cost nothing extra.
 *  - For photos and text (calories unknown until analysis) it only warns when a rule is already
 *    near/over — and runs *before* the expensive analysis, so declining skips that call entirely.
 */
@Singleton
class DietaryGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val mealRepository: MealRepository,
    private val classifier: DietaryClassifier
) {

    /** Enabled rules that warn on log. */
    private fun activeRules(): List<DietaryRule> =
        settings.dietaryRules.value.filter { it.enabled && it.warnOnLog }

    private suspend fun consumedToday(): Map<DietaryRuleKind, Int> =
        runCatching { mealRepository.dietaryConsumedTodayOnce(mealRepository.todayString()) }.getOrDefault(emptyMap())

    private fun statusOf(rule: DietaryRule, consumed: Map<DietaryRuleKind, Int>): DietaryRuleStatus =
        DietaryRuleStatus(rule.kind, consumed[rule.kind] ?: 0, rule.dailyLimitKcal)

    /**
     * Known-calorie foods (manual entry, database pick, barcode, "your usuals"). [items] is
     * (name, calories) per food. Warns if any item is in a category that's already over/near its
     * cap, or whose cap this meal would cross.
     */
    suspend fun checkKnownFoods(items: List<Pair<String, Float>>, isToday: Boolean): DietaryWarning? {
        val rules = activeRules()
        if (!isToday || rules.isEmpty() || items.isEmpty()) return null
        val consumed = consumedToday()
        val totalIncoming = items.sumOf { it.second.toDouble() }.toInt()

        // Which rules could possibly be triggered by this meal — the rest we don't even classify.
        val inPlay = rules.filter { rule ->
            val status = statusOf(rule, consumed)
            status.isRelevant ||
                (rule.dailyLimitKcal > 0 && (consumed[rule.kind] ?: 0) + totalIncoming > rule.dailyLimitKcal)
        }
        if (inPlay.isEmpty()) return null

        val tags = runCatching { classifier.tagFoods(items.map { it.first }, inPlay.map { it.kind }.toSet()) }
            .getOrElse { return null }

        // Candidates that should warn, each as (rule, alreadyConsumed, resultingTotal). Pick the one
        // that ends up furthest over its cap.
        val candidates = inPlay.mapNotNull { rule ->
            val incoming = items.indices
                .filter { tags.getOrElse(it) { emptySet() }.contains(rule.kind) }
                .sumOf { items[it].second.toDouble() }.toInt()
            if (incoming <= 0) return@mapNotNull null
            val already = consumed[rule.kind] ?: 0
            val status = statusOf(rule, consumed)
            val crosses = rule.dailyLimitKcal > 0 && already + incoming > rule.dailyLimitKcal
            if (status.isOver || status.isNear || crosses) Triple(rule, already, already + incoming) else null
        }
        val worst = candidates.maxByOrNull { it.third - it.first.dailyLimitKcal } ?: return null
        return DietaryWarning(worst.first.kind, worst.second, worst.first.dailyLimitKcal, alreadyOver = statusOf(worst.first, consumed).isOver)
    }

    /** Photo path — runs *before* the expensive analysis. Warns only when a rule is already near/over. */
    suspend fun checkPhoto(imageUri: String?, note: String, isToday: Boolean): DietaryWarning? {
        val relevant = relevantRules(isToday) ?: return null
        if (relevant.isEmpty()) return null
        val bitmap = decodeSmall(imageUri) ?: return null
        val present = runCatching { classifier.screenImage(bitmap, note, relevant.keys) }.getOrElse { return null }
        return warningFor(present, relevant)
    }

    /** Describe-to-AI path — runs before the expensive analysis. Warns only when a rule is near/over. */
    suspend fun checkText(text: String, isToday: Boolean): DietaryWarning? {
        val relevant = relevantRules(isToday) ?: return null
        if (relevant.isEmpty() || text.isBlank()) return null
        val present = runCatching { classifier.screenText(text, relevant.keys) }.getOrElse { return null }
        return warningFor(present, relevant)
    }

    /** Enabled+warn rules that are already near or over their cap today, with their status. */
    private suspend fun relevantRules(isToday: Boolean): Map<DietaryRuleKind, DietaryRuleStatus>? {
        val rules = activeRules()
        if (!isToday || rules.isEmpty()) return null
        val consumed = consumedToday()
        return rules.mapNotNull { rule ->
            val status = statusOf(rule, consumed)
            if (status.isRelevant) rule.kind to status else null
        }.toMap()
    }

    private fun warningFor(
        present: Set<DietaryRuleKind>,
        relevant: Map<DietaryRuleKind, DietaryRuleStatus>
    ): DietaryWarning? {
        val worst = relevant.filterKeys { present.contains(it) }.values.maxByOrNull { it.fraction } ?: return null
        return DietaryWarning(worst.kind, worst.consumedKcal, worst.limitKcal, alreadyOver = worst.isOver)
    }

    /** Decode a downscaled bitmap from a content/file uri, just for the fast dessert screen. */
    private fun decodeSmall(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > TARGET_PX) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }

    private companion object {
        const val TARGET_PX = 768
    }
}
