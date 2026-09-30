package com.fitpal.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.ui.component.minuteOfDayNow
import com.fitpal.app.wear.WatchLink
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** What widget code outside the Hilt graph (providers, receivers) needs from it. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetUpdater(): WidgetUpdater
    fun mealRepository(): MealRepository
    fun watchLink(): WatchLink
}

/**
 * Keeps every placed FitPal widget current. Three things trigger a redraw:
 *  1. **Data changes** while the app is running — [startWatching] follows the same tables Home reads,
 *     so a meal logged anywhere (phone, watch, a widget's water button) shows up within a second.
 *  2. **The clock** — [WidgetClock] wakes it at midnight, when a fast starts or ends, and every half
 *     hour for the draining rings (fasting progress, caffeine). Those alarms never wake the phone:
 *     they wait until the screen's on anyway, which is the only time a widget is seen.
 *  3. **The launcher** — placing, resizing or restyling a widget (the providers call [update]).
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataSource: WidgetDataSource,
    private val mealRepository: MealRepository,
    private val exerciseRepository: ExerciseRepository,
    private val stepRepository: StepRepository,
    private val weightRepository: WeightRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    @Volatile private var watching = false

    private val manager: AppWidgetManager? get() = runCatching { AppWidgetManager.getInstance(context) }.getOrNull()

    fun placedIds(kind: WidgetKind): IntArray =
        runCatching { manager?.getAppWidgetIds(ComponentName(context, kind.provider)) }.getOrNull() ?: IntArray(0)

    fun hasAnyWidget(): Boolean = WidgetKind.entries.any { placedIds(it).isNotEmpty() }

    /** Redraw every placed widget (loading the data once for all of them). */
    suspend fun updateAll() = mutex.withLock {
        val placed = WidgetKind.entries.associateWith { placedIds(it) }.filterValues { it.isNotEmpty() }
        if (placed.isEmpty()) {
            WidgetClock.cancel(context)
            return@withLock
        }
        val data = dataSource.load(placed.keys)
        placed.forEach { (kind, ids) -> ids.forEach { draw(kind, it, data) } }
        WidgetClock.scheduleNext(context, placed.keys, data)
    }

    /** Redraw just [ids] of [kind] — a widget was placed, resized or restyled. */
    suspend fun update(kind: WidgetKind, ids: IntArray) = mutex.withLock {
        if (ids.isEmpty()) return@withLock
        val data = dataSource.load(setOf(kind))
        ids.forEach { draw(kind, it, data) }
        WidgetClock.scheduleNext(context, WidgetKind.entries.filter { placedIds(it).isNotEmpty() }.toSet(), data)
    }

    /** Fire-and-forget [updateAll], for callers that aren't coroutines. */
    fun requestUpdate() {
        scope.launch { runCatching { updateAll() } }
    }

    private fun draw(kind: WidgetKind, id: Int, data: WidgetData) {
        val m = manager ?: return
        runCatching {
            val size = WidgetRender.sizeOf(context, m, id, kind)
            val views = WidgetRender.remoteViews(context, kind, WidgetPrefs.style(context, id), data, size, id)
            m.updateAppWidget(id, views)
        }
    }

    /**
     * While the app's process is alive, redraw whenever today's numbers change — however they changed.
     * Idempotent; started from the Application when widgets exist, and by a provider when one's added.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun startWatching() {
        if (watching) return
        watching = true
        scope.launch {
            today().flatMapLatest { day -> changesFor(day) }
                .debounce(700L)
                .collect { if (hasAnyWidget()) runCatching { updateAll() } }
        }
    }

    /** Today's date, re-emitted just after each midnight. */
    private fun today(): Flow<String> = flow {
        while (true) {
            emit(LocalDate.now().toString())
            val now = LocalDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay().plusSeconds(2)
            delay(Duration.between(now, next).toMillis().coerceAtLeast(1_000L))
        }
    }

    /** Anything that changes what a widget shows for [day]. */
    private fun changesFor(day: String): Flow<Unit> {
        val yesterday = LocalDate.parse(day).minusDays(1).toString()
        return merge(
            mealRepository.getDailyNutrition(day).map { },
            mealRepository.getTotalWaterForDate(day).map { },
            mealRepository.caffeineRowsInRange(yesterday, day).map { },
            exerciseRepository.getTotalBurnedForDate(day).map { },
            stepRepository.getTotalSteps(day).map { },
            weightRepository.getLatest().map { },
            settingsRepository.fastingSchedule.map { },
            settingsRepository.caffeineSettings.map { },
            settingsRepository.dailyCalorieGoal.map { },
            settingsRepository.userProfile.map { },
            settingsRepository.macroSelection.map { },
            settingsRepository.waterGoalOverrideMl.map { },
            settingsRepository.waterPresets.map { }
        )
    }
}

/**
 * The widgets' clock: one pending alarm for the next moment something on a widget changes by itself
 * (midnight; a fast starting or ending; the half-hourly nudge for draining rings). Non-waking — it's
 * delivered when the phone is next awake, which is when a widget can be seen.
 */
internal object WidgetClock {
    const val ACTION_TICK = "com.fitpal.app.widget.TICK"
    private const val LIVE_REFRESH_MS = 30 * 60_000L

    fun scheduleNext(context: Context, kinds: Set<WidgetKind>, data: WidgetData) {
        val now = System.currentTimeMillis()
        val candidates = mutableListOf(nextMidnight() + 5_000L)
        if (data.fasting.enabled && (WidgetKind.FASTING in kinds || WidgetKind.QUICK_LOG in kinds)) {
            val st = data.fasting.stateAt(minuteOfDayNow())
            candidates += now + st.minutesLeftInPhase * 60_000L - (now % 60_000L) + 1_000L
        }
        if (WidgetKind.FASTING in kinds || WidgetKind.CAFFEINE in kinds) candidates += now + LIVE_REFRESH_MS
        val at = candidates.filter { it > now }.minOrNull() ?: return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = tick(context)
        runCatching {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExact(AlarmManager.RTC, at, pi)
            else am.setWindow(AlarmManager.RTC, at, 60_000L, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(tick(context))
    }

    private fun tick(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 7301,
        Intent(context, WidgetActionReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun nextMidnight(): Long =
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
