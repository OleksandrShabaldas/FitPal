package com.fitpal.app.ml

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.fitpal.app.FitPalActivity
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.PlannerRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.Allergens
import com.fitpal.app.domain.model.MealTypes
import com.fitpal.app.domain.model.PlanRequest
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlannerWorkerEntryPoint {
    fun pipeline(): FoodAnalysisPipeline
    fun plannerJobManager(): PlannerJobManager
    fun plannerRepository(): PlannerRepository
    fun plannerContextBuilder(): PlannerContextBuilder
    fun settingsRepository(): SettingsRepository
    fun mealRepository(): MealRepository
}

/**
 * Plans meals in the background (docs/redesign/10): one day per request — a week is seven — so the
 * answer never gets cut off and a failure loses one day, not the week. Online first; when online
 * fails, the user picks (foreground) or it goes on-device (background), as the photo analysis does.
 * Every recipe's ingredients are checked against the allergies in code before it's stored.
 */
class PlannerWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val entry = EntryPointAccessors.fromApplication(appContext, PlannerWorkerEntryPoint::class.java)
    private val pipeline = entry.pipeline()
    private val jobs = entry.plannerJobManager()
    private val repo = entry.plannerRepository()
    private val contextBuilder = entry.plannerContextBuilder()
    private val settings = entry.settingsRepository()
    private val meals = entry.mealRepository()

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(PlannerNotifications.progress(applicationContext, "Starting…"))

    override suspend fun doWork(): Result {
        PlannerNotifications.ensureChannel(applicationContext)
        val request = PlanRequest.fromJson(inputData.getString(KEY_REQUEST))
        val requestId = inputData.getString(KEY_REQUEST_ID) ?: return Result.failure()
        val regenerateId = inputData.getLong(KEY_REGENERATE_ID, -1L).takeIf { it > 0 }
        val avoid = inputData.getString(KEY_AVOID).orEmpty().split('\n').filter { it.isNotBlank() }.toMutableList()
        runCatching { setForeground(getForegroundInfo()) }

        val allergies = settings.foodProfile.value.allAllergies
        var useOnline = pipeline.canUseOnline()
        var onlineError: String? = if (useOnline) null else pipeline.offlineReason()
        var lastSource: AiSource? = null
        var count = 0
        return try {
            val regenerating = regenerateId?.let { repo.get(it) }
            val days = if (regenerating != null) listOf(regenerating.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: request.date) else request.days
            for ((i, day) in days.withIndex()) {
                val types = when {
                    regenerating != null -> listOf(regenerating.mealType ?: MealTypes.DINNER)
                    request.scope.isOneMeal -> listOf(request.scope.mealType!!)
                    else -> mealTypesFor(day)
                }
                if (types.isEmpty()) continue
                val dayLabel = if (day == LocalDate.now()) "today" else day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                progress(if (days.size > 1) "Planning $dayLabel…" else "Planning your ${if (types.size == 1) MealTypes.label(types.first()).lowercase() else "day"}…", lastSource)
                val context = contextBuilder.build(request, day)

                // Online until it fails; then the user's choice (or on-device in the background) for the rest.
                var raw: String? = null
                while (raw == null) {
                    if (useOnline) {
                        try {
                            var model: String? = null
                            raw = pipeline.planOnline(FoodPrompts.plannerOnline(context, dayLabel, types, avoid), onModel = { model = it }) { msg ->
                                jobs.setProgress(msg, AiSource.online(model))
                            }
                            lastSource = AiSource.online(model)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            onlineError = if (e is GeminiQuotaException) "Daily free quota reached" else e.message ?: "Online request failed"
                            useOnline = false
                            if (!pipeline.isOnlineAiEnabled()) continue
                            when (jobs.awaitFallbackChoice(onlineError ?: "Online AI unavailable")) {
                                FallbackChoice.RETRY_ONLINE -> useOnline = pipeline.canUseOnline()
                                FallbackChoice.CANCEL -> throw PlanCancelledException()
                                FallbackChoice.USE_ON_DEVICE -> Unit
                            }
                        }
                    } else {
                        progress("Planning $dayLabel on your phone — this takes a while…", AiSource.offline())
                        raw = pipeline.planLocal(FoodPrompts.planner(context, dayLabel, types, avoid))
                        lastSource = AiSource.offline()
                    }
                }

                val recipes = FoodJsonParser.parsePlan(raw!!, fallbackMealType = types.first(), dayOffset = i).meals
                    .map { r -> r.copy(allergens = Allergens.found(r.ingredients.map { it.ingredient.name }, allergies)) }
                if (regenerating != null) {
                    recipes.firstOrNull()?.let { repo.replace(regenerating.id, it.copy(dayOffset = 0), lastSource); count++ }
                } else if (recipes.isNotEmpty()) {
                    repo.addIdeas(requestId, request.date, recipes, lastSource)
                    count += recipes.size
                    avoid += recipes.map { it.name }
                }
            }
            if (count == 0) {
                jobs.setFailed("The AI didn't return a usable recipe. Try again, or change the request a little.")
                PlannerNotifications.postFailed(applicationContext)
                Result.success()
            } else {
                jobs.setDone(count, lastSource, if (lastSource?.isOffline == true) "Made on your phone — recipes may be simpler." else null)
                PlannerNotifications.postDone(applicationContext, requestId, count)
                Result.success()
            }
        } catch (e: PlanCancelledException) {
            jobs.clear()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            jobs.setFailed(e.message ?: "Planning failed")
            PlannerNotifications.postFailed(applicationContext)
            Result.failure()
        }
    }

    /**
     * Which meals a whole day gets: breakfast, lunch and dinner — minus breakfast when the eating
     * window opens late (11 AM or after), minus meals already logged when it's today.
     */
    private suspend fun mealTypesFor(day: LocalDate): List<String> {
        val f = settings.fastingSchedule.value
        val base = listOf(MealTypes.BREAKFAST, MealTypes.LUNCH, MealTypes.DINNER)
            .filterNot { f.enabled && it == MealTypes.BREAKFAST && f.eatStartMin >= 11 * 60 }
        if (day != LocalDate.now()) return base
        val logged = meals.getItemsWithTypeForDate(day.toString()).first().map { it.mealType }.toSet()
        return base.filterNot { it in logged }
    }

    private suspend fun progress(message: String, source: AiSource?) {
        jobs.setProgress(message, source)
        runCatching { setForeground(foregroundInfo(PlannerNotifications.progress(applicationContext, message))) }
    }

    private fun foregroundInfo(notification: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PlannerNotifications.NOTIF_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PlannerNotifications.NOTIF_PROGRESS, notification)
        }

    companion object {
        const val KEY_REQUEST = "request"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_REGENERATE_ID = "regenerate_id"
        const val KEY_AVOID = "avoid"
        const val UNIQUE_NAME = "meal_planner"
    }
}

/** "Planning your week…" while it runs, then "Your plan is ready" — tap opens the results. */
object PlannerNotifications {
    private const val CHANNEL = "meal_planner"
    const val NOTIF_PROGRESS = 4215
    private const val NOTIF_DONE = 4216

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Meal planner", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress and results of meal plans"
                    setShowBadge(false)
                }
            )
        }
    }

    fun progress(context: Context, message: String): Notification =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Planning your meals…")
            .setContentText(message)
            .setContentIntent(open(context, Screen.Coach.route))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    fun postDone(context: Context, requestId: String, count: Int) = notify(
        context,
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Your plan is ready")
            .setContentText(if (count == 1) "1 recipe — tap to see it" else "$count recipes — tap to see them")
            .setContentIntent(open(context, Screen.PlanResults.buildRoute(requestId)))
            .setAutoCancel(true)
            .build()
    )

    fun postFailed(context: Context) = notify(
        context,
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Couldn't make the plan")
            .setContentText("Open FitPal to try again")
            .setContentIntent(open(context, Screen.Coach.route))
            .setAutoCancel(true)
            .build()
    )

    private fun notify(context: Context, notification: Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_DONE, notification) }
    }

    private fun open(context: Context, route: String): PendingIntent {
        val intent = Intent(context, FitPalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(FitPalActivity.EXTRA_NAV_ROUTE, route)
        }
        return PendingIntent.getActivity(context, route.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
