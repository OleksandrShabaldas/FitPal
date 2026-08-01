package com.fitpal.app.data.repository

import com.fitpal.app.data.local.dao.ChallengeDao
import com.fitpal.app.data.local.dao.TrailDao
import com.fitpal.app.data.local.entity.TrailProjectEntity
import com.fitpal.app.data.local.entity.TrailStateEntity
import com.fitpal.app.data.local.entity.TrailUnlockEntity
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.CasePack
import com.fitpal.app.domain.CaseReward
import com.fitpal.app.domain.CurioCatalog
import com.fitpal.app.domain.GoalAdherence
import com.fitpal.app.domain.ProjectView
import com.fitpal.app.domain.SceneTheme
import com.fitpal.app.domain.ShopState
import com.fitpal.app.domain.Streaks
import com.fitpal.app.domain.ThemeCatalog
import com.fitpal.app.domain.TrailCatalog
import com.fitpal.app.domain.TrailDisplay
import com.fitpal.app.domain.TrailProgression
import com.fitpal.app.domain.TrailProject
import com.fitpal.app.domain.TrailRules
import com.fitpal.app.domain.TutorialStep
import com.fitpal.app.domain.model.FitnessGoal
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The engine behind "The Trail" (see GAME_DESIGN.md).
 *
 * [evaluate] is an idempotent catch-up: it folds in every completed day since the last
 * run — a logged day earns 💧, raises vitality and runs **one tick**; a missed day only
 * costs vitality. Today ticks as soon as you've logged something, so the daily visit
 * pays off immediately. **Elapsed time alone never produces anything.**
 */
@Singleton
class TrailRepository @Inject constructor(
    private val trailDao: TrailDao,
    private val challengeDao: ChallengeDao,
    private val mealRepository: MealRepository,
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository
) {
    private val fmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Calorie target, resting-BMR floor and goal direction — for the on-goal bonus. */
    private data class GoalContext(val target: Int, val floor: Float, val goal: FitnessGoal?)

    private suspend fun goalContext(): GoalContext {
        val profile = settingsRepository.userProfile.value
        val manualGoal = settingsRepository.dailyCalorieGoal.value
        val weight = weightRepository.getLatest().first()?.weightKg
            ?: return GoalContext(0, 0f, null)
        val targets = BmrCalculator.dailyTargets(profile, weight)
        return GoalContext(
            target = if (manualGoal > 0) manualGoal else targets.calories,
            floor = BmrCalculator.bmr(profile, weight),
            goal = profile.fitnessGoal
        )
    }

    private fun goalContextFlow(): Flow<GoalContext> = combine(
        settingsRepository.userProfile,
        settingsRepository.dailyCalorieGoal,
        weightRepository.getLatest()
    ) { profile, manualGoal, weight ->
        val w = weight?.weightKg ?: return@combine GoalContext(0, 0f, null)
        val t = BmrCalculator.dailyTargets(profile, w)
        GoalContext(if (manualGoal > 0) manualGoal else t.calories, BmrCalculator.bmr(profile, w), profile.fitnessGoal)
    }

    /** 50% of a day's exercise burn raises that day's on-goal ceiling. */
    private fun onGoal(ctx: GoalContext, calories: Float, burn: Float): Boolean {
        val goal = ctx.goal ?: return false
        val effectiveTarget = ctx.target + (burn * 0.5f).toInt()
        return GoalAdherence.isOnGoal(goal, calories, effectiveTarget, ctx.floor)
    }

    /**
     * Base production from everything built so far. Only scans up to the current site —
     * you can't have built anything further along the trail than you've walked.
     */
    private fun productionOf(builtIds: Collection<String>, throughSiteIndex: Int): Long {
        if (builtIds.isEmpty()) return TrailRules.BASE_PRODUCTION
        val built = builtIds.toSet()
        var total = TrailRules.BASE_PRODUCTION
        for (index in 0..throughSiteIndex) {
            TrailCatalog.siteAt(index).projects.forEach {
                if (built.contains(it.id)) total += it.production
            }
        }
        return total
    }

    // ---------------- Catch-up evaluation ----------------

    suspend fun evaluate() {
        val today = LocalDate.now()
        val state = trailDao.getState() ?: TrailStateEntity().also { trailDao.upsertState(it) }

        // First ever run: start the clock at yesterday so days before the trail existed can't
        // penalise you — but don't stop there. If today is already logged we still want it to
        // tick, so a brand-new player has something to collect immediately rather than an
        // empty site until tomorrow.
        val lastEvaluated = state.lastEvaluatedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: today.minusDays(1)

        val production = productionOf(trailDao.builtIds(), state.siteIndex)
        val ctx = goalContext()
        val loggedDates = mealRepository.getLoggedDatesDesc().first().toSet()
        var s = state.copy(lastEvaluatedDate = lastEvaluated.format(fmt))

        // ---- completed days ----
        val lastCompleted = today.minusDays(1)
        var day = lastEvaluated.plusDays(1)
        if (!day.isAfter(lastCompleted)) {
            val cals = mealRepository
                .getDailyNutritionRange(day.format(fmt), lastCompleted.format(fmt))
                .first().associate { it.date to it.calories }
            while (!day.isAfter(lastCompleted)) {
                val iso = day.format(fmt)
                s = if (loggedDates.contains(iso)) {
                    runLoggedDay(s, iso, cals[iso] ?: 0f, ctx, production)
                } else {
                    // Neglect only ever costs rate — never anything already built.
                    s.copy(vitality = (s.vitality - TrailRules.VITALITY_LOSS).coerceAtLeast(TrailRules.VITALITY_MIN))
                }
                day = day.plusDays(1)
            }
            s = s.copy(lastEvaluatedDate = lastCompleted.format(fmt))
        }

        // ---- today (never penalised — the day isn't over yet) ----
        val todayIso = today.format(fmt)
        if (loggedDates.contains(todayIso) && s.lastTickDate != todayIso) {
            val todayCals = mealRepository.getDailyNutrition(todayIso).first().calories
            s = runLoggedDay(s, todayIso, todayCals, ctx, production)
        }

        trailDao.upsertState(s)
    }

    /** A logged day: earn 💧, gain vitality, then spend 1 💧 to run the tick. */
    private suspend fun runLoggedDay(
        state: TrailStateEntity,
        iso: String,
        calories: Float,
        ctx: GoalContext,
        production: Long
    ): TrailStateEntity {
        val burn = exerciseRepository.totalBurnedOnce(iso)
        val hitGoal = onGoal(ctx, calories, burn)

        var s = state.copy(
            water = (state.water + TrailRules.WATER_PER_LOG + if (hitGoal) TrailRules.WATER_ON_GOAL_BONUS else 0)
                .coerceAtMost(TrailRules.WATER_CAP),
            vitality = (state.vitality + TrailRules.VITALITY_GAIN).coerceAtMost(TrailRules.VITALITY_MAX)
        )

        if (s.water > 0) {
            val multiplier = s.vitality *
                (if (hitGoal) TrailRules.ON_GOAL_MULTIPLIER else 1f) *
                s.legacyMultiplier
            val gained = (production * multiplier).toLong().coerceAtLeast(1L)
            s = s.copy(
                water = s.water - 1,
                bankedGrowth = s.bankedGrowth + gained,
                lastTickDate = iso
            )
        }
        return s
    }

    // ---------------- Actions ----------------

    /** Remember that a coach-mark has been shown, so it never appears twice. */
    suspend fun markTutorialSeen(step: TutorialStep) {
        val s = trailDao.getState() ?: return
        trailDao.upsertState(s.copy(tutorialSeen = s.tutorialSeen or step.bit))
    }

    /** Collect the banked pile. Doing it by hand is what pays ⭐. Returns what was collected. */
    suspend fun collect(): Long {
        val s = trailDao.getState() ?: return 0L
        if (s.bankedGrowth <= 0L) return 0L
        val amount = s.bankedGrowth
        trailDao.upsertState(
            s.copy(
                growth = s.growth + amount,
                bankedGrowth = 0L,
                points = s.points + TrailRules.COLLECT_POINTS
            )
        )
        return amount
    }

    /** Build a project in the chosen style, if it's affordable and not already built. */
    suspend fun build(project: TrailProject, variantIndex: Int = 0): Boolean {
        val s = trailDao.getState() ?: return false
        if (trailDao.builtIds().contains(project.id)) return false
        if (s.growth < project.cost) return false
        if (project.pointCost > 0 && s.points < project.pointCost) return false
        trailDao.upsertState(
            s.copy(growth = s.growth - project.cost, points = s.points - project.pointCost)
        )
        trailDao.addProject(TrailProjectEntity(project.id, variantIndex = variantIndex.coerceIn(0, 2)))
        return true
    }

    /** Move to the next site once every non-keystone project here is built. */
    suspend fun advanceSite(): Boolean {
        val s = trailDao.getState() ?: return false
        val site = TrailCatalog.siteAt(s.siteIndex)
        val built = trailDao.builtIds().toSet()
        val required = site.projects.filter { !it.isKeystone }
        if (!required.all { built.contains(it.id) }) return false
        trailDao.upsertState(s.copy(siteIndex = s.siteIndex + 1))
        return true
    }

    // ---------------- Shop ----------------

    fun observeShop(): Flow<ShopState> = combine(
        trailDao.observeState(),
        trailDao.observeUnlocks()
    ) { state, unlocks ->
        val s = state ?: TrailStateEntity()
        ShopState(
            points = s.points,
            activeTheme = s.activeTheme,
            ownedThemes = unlocks
                .filter { it.kind == TrailUnlockEntity.KIND_THEME }
                .map { it.id.removePrefix("theme:") }
                .toSet() + ThemeCatalog.DEFAULT_ID,   // the starting look is always owned
            curios = unlocks
                .filter { it.kind == TrailUnlockEntity.KIND_CURIO }
                .associate { it.id.removePrefix("curio:") to it.count }
        )
    }

    suspend fun buyTheme(theme: SceneTheme): Boolean {
        val s = trailDao.getState() ?: return false
        if (theme.cost > 0 && s.points < theme.cost) return false
        if (trailDao.unlock(TrailUnlockEntity.themeId(theme.id)) != null) return false
        trailDao.upsertUnlock(
            TrailUnlockEntity(TrailUnlockEntity.themeId(theme.id), TrailUnlockEntity.KIND_THEME)
        )
        // Buying equips it straight away — nobody buys a look to not wear it.
        trailDao.upsertState(s.copy(points = s.points - theme.cost, activeTheme = theme.id))
        return true
    }

    suspend fun equipTheme(themeId: String): Boolean {
        val s = trailDao.getState() ?: return false
        val owned = themeId == ThemeCatalog.DEFAULT_ID ||
            trailDao.unlock(TrailUnlockEntity.themeId(themeId)) != null
        if (!owned) return false
        trailDao.upsertState(s.copy(activeTheme = themeId))
        return true
    }

    /**
     * Open a case. Always yields something: an unowned theme, a curio (a duplicate still
     * pays a little growth), or a growth bundle scaled to your current production.
     */
    suspend fun openCase(pack: CasePack): CaseReward? {
        val s = trailDao.getState() ?: return null
        if (s.points < pack.cost) return null

        val unlocks = trailDao.unlocks()
        val ownedThemes = unlocks.filter { it.kind == TrailUnlockEntity.KIND_THEME }
            .map { it.id.removePrefix("theme:") }.toSet()
        val unownedThemes = ThemeCatalog.ALL.filter {
            it.id != ThemeCatalog.DEFAULT_ID && !ownedThemes.contains(it.id)
        }

        var state = s.copy(points = s.points - pack.cost)
        val roll = Random.nextFloat()

        val reward: CaseReward = when {
            unownedThemes.isNotEmpty() && roll < pack.themeChance -> {
                val theme = unownedThemes.random()
                trailDao.upsertUnlock(
                    TrailUnlockEntity(TrailUnlockEntity.themeId(theme.id), TrailUnlockEntity.KIND_THEME)
                )
                CaseReward.GotTheme(theme)
            }
            roll < pack.themeChance + 0.62f -> {
                val curio = CurioCatalog.roll(pack.rareBoost)
                val key = TrailUnlockEntity.curioId(curio.id)
                val existing = trailDao.unlock(key)
                trailDao.upsertUnlock(
                    existing?.copy(count = existing.count + 1)
                        ?: TrailUnlockEntity(key, TrailUnlockEntity.KIND_CURIO)
                )
                val duplicate = existing != null
                // A duplicate isn't a dud — it pays a consolation in growth.
                val bonus = if (duplicate) growthBundle(state, 2) else 0L
                if (bonus > 0L) state = state.copy(growth = state.growth + bonus)
                CaseReward.GotCurio(curio, duplicate, bonus)
            }
            else -> {
                val amount = growthBundle(state, 4 + Random.nextInt(5))
                state = state.copy(growth = state.growth + amount)
                CaseReward.GotGrowth(amount)
            }
        }

        trailDao.upsertState(state)
        return reward
    }

    /** A growth payout worth roughly [ticks] days of current production. */
    private suspend fun growthBundle(state: TrailStateEntity, ticks: Int): Long {
        val production = productionOf(trailDao.builtIds(), state.siteIndex)
        return (production * ticks).coerceAtLeast(1L)
    }

    // ---------------- Observation ----------------

    /** True when there's something waiting — drives the badge on Home's streak chip. */
    fun hasPending(): Flow<Boolean> = combine(
        trailDao.observeState(),
        mealRepository.getDailyNutrition(LocalDate.now().format(fmt))
    ) { state, todayNutrition ->
        val s = state ?: return@combine false
        val todayIso = LocalDate.now().format(fmt)
        // Either growth is sitting uncollected, or today's log hasn't been ticked in yet.
        s.bankedGrowth > 0L || (todayNutrition.calories > 0f && s.lastTickDate != todayIso)
    }

    fun observe(): Flow<TrailDisplay?> {
        val today = LocalDate.now()
        val todayIso = today.format(fmt)

        // Bundle goal context + today's burn + claim count so the top-level combine stays at 5.
        val todayContext = combine(
            goalContextFlow(),
            exerciseRepository.getTotalBurnedForDate(todayIso),
            challengeDao.observeClaimedCount()
        ) { ctx, burn, claims -> Triple(ctx, burn, claims) }

        return combine(
            trailDao.observeState(),
            trailDao.observeProjects(),
            mealRepository.getDailyNutrition(todayIso),
            mealRepository.getLoggedDatesDesc(),
            todayContext
        ) { stateOrNull, projectRows, todayNutrition, loggedDates, (ctx, burn, claims) ->
            val s = stateOrNull ?: TrailStateEntity()
            val builtIds = projectRows.map { it.projectId }.toSet()
            val variantById = projectRows.associate { it.projectId to it.variantIndex }
            val site = TrailCatalog.siteAt(s.siteIndex)
            val production = productionOf(builtIds, s.siteIndex)

            val todayCals = todayNutrition.calories
            val loggedToday = todayCals > 0f
            val onGoalToday = onGoal(ctx, todayCals, burn)

            val multiplier = s.vitality *
                (if (onGoalToday) TrailRules.ON_GOAL_MULTIPLIER else 1f) *
                s.legacyMultiplier

            TrailDisplay(
                siteIndex = s.siteIndex,
                site = site,
                projects = site.projects.map { p ->
                    ProjectView(
                        project = p,
                        built = builtIds.contains(p.id),
                        affordable = s.growth >= p.cost && s.points >= p.pointCost,
                        variantIndex = variantById[p.id] ?: 0
                    )
                },
                growth = s.growth,
                bankedGrowth = s.bankedGrowth,
                water = s.water,
                points = s.points,
                vitality = s.vitality,
                production = production,
                perTick = (production * multiplier).toLong().coerceAtLeast(1L),
                streak = Streaks.current(
                    loggedDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
                    today
                ),
                loggedToday = loggedToday,
                onGoalToday = onGoalToday,
                tickedToday = s.lastTickDate == todayIso,
                themeId = s.activeTheme,
                progression = TrailProgression(
                    projectsBuilt = builtIds.size,
                    challengesClaimed = claims,
                    seenMask = s.tutorialSeen
                )
            )
        }
    }
}
