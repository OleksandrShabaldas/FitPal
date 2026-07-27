package com.fitpal.app.data.repository

import com.fitpal.app.data.local.dao.GardenDao
import com.fitpal.app.data.local.entity.CollectedPlantEntity
import com.fitpal.app.data.local.entity.GardenStateEntity
import com.fitpal.app.domain.GardenDisplay
import com.fitpal.app.domain.GardenRules
import com.fitpal.app.domain.GoalAdherence
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.PlantCatalog
import com.fitpal.app.domain.Streaks
import com.fitpal.app.domain.WeekProgress
import com.fitpal.app.domain.model.FitnessGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the garden: a catch-up [evaluate] that banks growth/wilt for completed
 * days, and [observe] which layers today's live progress on top for the UI.
 */
@Singleton
class GardenRepository @Inject constructor(
    private val gardenDao: GardenDao,
    private val mealRepository: MealRepository,
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository
) {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    val collection: Flow<List<CollectedPlantEntity>> = gardenDao.observeCollection()

    /** Target calories, resting-BMR floor, and the user's goal — for on-goal checks. */
    private data class GoalContext(val target: Int, val floor: Float, val goal: FitnessGoal?)

    private suspend fun goalContext(): GoalContext {
        val profile = settingsRepository.userProfile.value
        val manualGoal = settingsRepository.dailyCalorieGoal.value
        val weight = weightRepository.getLatest().first()?.weightKg
            ?: return GoalContext(0, 0f, null)
        val targets = BmrCalculator.dailyTargets(profile, weight)
        val target = if (manualGoal > 0) manualGoal else targets.calories
        return GoalContext(target, BmrCalculator.bmr(profile, weight), profile.fitnessGoal)
    }

    /** Reactive target/floor/goal — re-emits when profile, goal or weight changes. */
    private fun goalContextFlow(): Flow<GoalContext> = combine(
        settingsRepository.userProfile,
        settingsRepository.dailyCalorieGoal,
        weightRepository.getLatest()
    ) { profile, manualGoal, weight ->
        val w = weight?.weightKg ?: return@combine GoalContext(0, 0f, null)
        val t = BmrCalculator.dailyTargets(profile, w)
        GoalContext(if (manualGoal > 0) manualGoal else t.calories, BmrCalculator.bmr(profile, w), profile.fitnessGoal)
    }

    /** Live progress of the current (partial) calendar week toward the weekly bonus. */
    fun weekProgress(): Flow<WeekProgress?> {
        val today = LocalDate.now()
        val monday = mondayOf(today)
        return combine(
            mealRepository.getDailyNutritionRange(monday.format(fmt), today.format(fmt)),
            exerciseRepository.getDailyBurnRange(monday.format(fmt), today.format(fmt)),
            goalContextFlow()
        ) { nutritionRows, burnRows, ctx ->
            if (ctx.goal == null) return@combine null
            val burnByDate = burnRows.associate { it.date to it.burned }
            val logged = nutritionRows.filter { it.calories > 0f }
            var sumCals = 0f
            var sumTarget = 0f
            logged.forEach { r ->
                sumCals += r.calories
                sumTarget += ctx.target + 0.5f * (burnByDate[r.date] ?: 0f)
            }
            WeekProgress(
                loggedDays = logged.size,
                neededDays = GardenRules.MIN_LOGGED_DAYS_FOR_WEEK,
                onTrackSoFar = logged.isNotEmpty() && GoalAdherence.isWeekOnTrack(ctx.goal, sumCals, sumTarget),
                daysElapsed = today.dayOfWeek.value
            )
        }
    }

    /**
     * Bank growth/wilt for completed days and award any finished on-track weeks.
     * Idempotent — each day and week is evaluated once. Safe to call on every open.
     */
    suspend fun evaluate() {
        val today = LocalDate.now()
        val state = gardenDao.getState() ?: GardenStateEntity().also { gardenDao.upsertState(it) }

        val start = state.lastEvaluatedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        // First run ever: start fresh from yesterday (no pre-garden penalty), and begin
        // weekly bonuses from next week (skip the partial current week).
        if (start == null) {
            gardenDao.upsertState(
                state.copy(
                    lastEvaluatedDate = today.minusDays(1).format(fmt),
                    lastWeeklyEvalWeek = mondayOf(today).format(fmt)
                )
            )
            return
        }

        val ctx = goalContext()
        var s = state

        // ---- Daily growth / wilt ----
        val lastCompleted = today.minusDays(1)
        if (start.isBefore(lastCompleted)) {
            val from = start.plusDays(1)
            val loggedDates = mealRepository.getLoggedDatesDesc().first().toSet()
            val calsByDate = mealRepository.getDailyNutritionRange(from.format(fmt), lastCompleted.format(fmt))
                .first().associate { it.date to it.calories }
            var day = from
            while (!day.isAfter(lastCompleted)) {
                val iso = day.format(fmt)
                val logged = loggedDates.contains(iso)
                val onGoal = if (logged) {
                    val cals = calsByDate[iso] ?: 0f
                    // 50% of that day's exercise burn raises the on-goal ceiling.
                    val effTarget = ctx.target + (exerciseRepository.totalBurnedOnce(iso) * 0.5f).toInt()
                    ctx.goal != null && GoalAdherence.isOnGoal(ctx.goal, cals, effTarget, ctx.floor)
                } else false
                s = processDay(s, logged, onGoal, iso)
                day = day.plusDays(1)
            }
            s = s.copy(lastEvaluatedDate = lastCompleted.format(fmt))
        }

        // ---- Weekly trend bonus ----
        s = evaluateWeeks(s, today, ctx)

        gardenDao.upsertState(s)
    }

    /** Award the weekly bonus for any fully-completed calendar weeks not yet checked. */
    private suspend fun evaluateWeeks(state: GardenStateEntity, today: LocalDate, ctx: GoalContext): GardenStateEntity {
        var s = state
        val lastWeek = s.lastWeeklyEvalWeek?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return s
        var weekMonday = lastWeek.plusWeeks(1)

        // Only weeks whose Sunday is already in the past.
        while (weekMonday.plusDays(6).isBefore(today)) {
            val sunday = weekMonday.plusDays(6)
            val loggedRows = mealRepository
                .getDailyNutritionRange(weekMonday.format(fmt), sunday.format(fmt)).first()
                .filter { it.calories > 0f }

            if (loggedRows.size >= GardenRules.MIN_LOGGED_DAYS_FOR_WEEK && ctx.goal != null) {
                var sumCals = 0f
                var sumTarget = 0f
                loggedRows.forEach { r ->
                    sumCals += r.calories
                    sumTarget += ctx.target + (exerciseRepository.totalBurnedOnce(r.date) * 0.5f)
                }
                if (GoalAdherence.isWeekOnTrack(ctx.goal, sumCals, sumTarget)) {
                    s = s.copy(
                        water = (s.water + GardenRules.WEEKLY_BONUS_WATER).coerceAtMost(GardenRules.WATER_CAP),
                        points = s.points + GardenRules.WEEKLY_BONUS_POINTS,
                        onTrackWeeks = s.onTrackWeeks + 1
                    )
                }
            }
            s = s.copy(lastWeeklyEvalWeek = weekMonday.format(fmt))
            weekMonday = weekMonday.plusWeeks(1)
        }
        return s
    }

    /**
     * Process one completed day: a logged day earns 💧; then the plant is
     * auto-watered once (if not already watered that day) — spending 1 💧 to grow,
     * or wilting toward death when the reserve is empty.
     */
    private suspend fun processDay(s: GardenStateEntity, logged: Boolean, onGoal: Boolean, dayIso: String): GardenStateEntity {
        var st = s
        if (logged) {
            st = st.copy(
                water = (st.water + GardenRules.WATER_PER_LOG + if (onGoal) GardenRules.WATER_ON_GOAL_BONUS else 0)
                    .coerceAtMost(GardenRules.WATER_CAP),
                loggedThisPlant = st.loggedThisPlant + 1,
                onGoalThisPlant = st.onGoalThisPlant + if (onGoal) 1 else 0
            )
        }
        val day = LocalDate.parse(dayIso)
        val alreadyWatered = st.lastWateredDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { !day.isAfter(it) } ?: false
        if (!alreadyWatered) {
            if (st.water > 0) {
                st = st.copy(
                    water = st.water - 1,
                    growthPoints = st.growthPoints + GardenRules.GROWTH_PER_WATER,
                    wiltLevel = (st.wiltLevel - 1).coerceAtLeast(0),
                    lastWateredDate = dayIso
                )
                st = bloomIfReady(st, dayIso)
            } else {
                val wilt = st.wiltLevel + 1
                st = if (wilt >= GardenRules.WILT_DEATH) {
                    st.copy(growthPoints = 0, wiltLevel = 0, loggedThisPlant = 0, onGoalThisPlant = 0)
                } else {
                    st.copy(wiltLevel = wilt)
                }
            }
        }
        return st
    }

    /** Manual watering for today: grows the plant + earns ⭐points. Once per day. */
    suspend fun waterPlant(): Boolean {
        val iso = LocalDate.now().format(fmt)
        val s = gardenDao.getState() ?: return false
        if (s.lastWateredDate == iso || s.water <= 0) return false
        var st = s.copy(
            water = s.water - 1,
            growthPoints = s.growthPoints + GardenRules.GROWTH_PER_WATER,
            points = s.points + GardenRules.WATER_POINTS,
            wiltLevel = (s.wiltLevel - 1).coerceAtLeast(0),
            lastWateredDate = iso
        )
        st = bloomIfReady(st, iso)
        gardenDao.upsertState(st)
        return true
    }

    /** If the current plant has reached bloom, collect it and plant a fresh seed. */
    private suspend fun bloomIfReady(s: GardenStateEntity, dayIso: String): GardenStateEntity {
        if (s.growthPoints < GardenRules.BLOOM_COST) return s
        val quality = s.onGoalThisPlant.toFloat() / s.loggedThisPlant.coerceAtLeast(1)
        val species = PlantCatalog.roll(quality)
        val newBloomCount = s.bloomCount + 1
        gardenDao.addCollected(
            CollectedPlantEntity(
                speciesId = species.id,
                rarity = species.rarity.name,
                bloomedDate = dayIso,
                orderIndex = newBloomCount
            )
        )
        return s.copy(
            growthPoints = s.growthPoints - GardenRules.BLOOM_COST,
            bloomCount = newBloomCount,
            loggedThisPlant = 0,
            onGoalThisPlant = 0,
            wiltLevel = 0  // a fresh seed starts healthy
        )
    }

    private fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    // ---- Bloom celebration ----

    /** Blooms the user hasn't celebrated yet. */
    suspend fun newlyBloomed(): List<CollectedPlantEntity> {
        val s = gardenDao.getState() ?: return emptyList()
        return gardenDao.collectedAfter(s.lastSeenBloomCount)
    }

    suspend fun markBloomsSeen() {
        val s = gardenDao.getState() ?: return
        gardenDao.upsertState(s.copy(lastSeenBloomCount = s.bloomCount))
    }

    /**
     * Live garden state for the UI: the banked state plus today's contribution
     * (shown immediately; banked on the next evaluation).
     */
    fun observe(): Flow<GardenDisplay> {
        val today = LocalDate.now()
        val todayIso = today.format(fmt)

        return combine(
            gardenDao.observeState(),
            mealRepository.getDailyNutrition(todayIso),
            mealRepository.getLoggedDatesDesc(),
            goalContextFlow(),
            exerciseRepository.getTotalBurnedForDate(todayIso)
        ) { stateOrNull, todayNutrition, loggedDates, ctx, todayBurned ->
            val s = stateOrNull ?: GardenStateEntity()
            val todayCals = todayNutrition.calories
            val loggedToday = todayCals > 0f
            val effTarget = ctx.target + (todayBurned * 0.5f).toInt()
            val onGoalToday = ctx.goal != null && GoalAdherence.isOnGoal(ctx.goal, todayCals, effTarget, ctx.floor)

            val displayGrowth = s.growthPoints.coerceAtMost(GardenRules.BLOOM_COST)
            val canWaterToday = s.water > 0 && s.lastWateredDate != todayIso

            val streak = Streaks.current(
                loggedDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
                today
            )

            GardenDisplay(
                stage = GardenRules.stageFor(displayGrowth),
                wilt = GardenRules.wiltFor(s.wiltLevel),
                growthPoints = displayGrowth,
                bloomCost = GardenRules.BLOOM_COST,
                water = s.water,
                points = s.points,
                canWaterToday = canWaterToday,
                streak = streak,
                bloomCount = s.bloomCount,
                onTrackWeeks = s.onTrackWeeks,
                loggedToday = loggedToday,
                onGoalToday = onGoalToday
            )
        }
    }
}
