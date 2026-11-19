package com.fitpal.app.ui.screen.coach

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Close
import com.fitpal.app.ui.component.kit.aiGlow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.model.MealTypes
import com.fitpal.app.domain.model.PlanRequest
import com.fitpal.app.domain.model.PlanScope
import com.fitpal.app.ml.PlanStatus
import com.fitpal.app.ui.component.AnalysingStrip
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GoldPill
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.InlineMessageCard
import com.fitpal.app.ui.component.SetupPill
import com.fitpal.app.ui.component.TrackerSheet
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.component.swipeNavigation
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glass
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The Coach tab (docs/redesign/10): today's overview and the coach's question, the reviews, and the
 * meal planner — plan a meal, a day or a week, what's coming up, saved ideas and the food profile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(
    onOpenReview: (period: String, key: String) -> Unit,
    onOpenPlan: (requestId: String) -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenFoodProfile: () -> Unit,
    onOpenMemory: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    viewModel: CoachViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val plannerOn by viewModel.plannerEnabled.collectAsStateWithLifecycle()
    val profile by viewModel.foodProfile.collectAsStateWithLifecycle()
    val job by viewModel.job.collectAsStateWithLifecycle()
    val upcoming by viewModel.upcoming.collectAsStateWithLifecycle()
    val savedCount by viewModel.savedCount.collectAsStateWithLifecycle()
    val lastPlan by viewModel.lastOpenPlan.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    var requestSheet by remember { mutableStateOf<PlanRequest?>(null) }
    var recent by remember { mutableStateOf(emptyList<String>()) }
    var left by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showEarlier by remember { mutableStateOf(false) }
    var showSkipped by remember { mutableStateOf(false) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    // × on a skipped week or month, with Undo (the 17 Nov review, #3).
    fun dismiss(item: com.fitpal.app.domain.SkippedReviews.Skipped) {
        viewModel.dismissSkipped(item)
        scope.launch {
            val r = snackbar.showSnackbar("${item.title} removed", actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Short)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.undoDismissSkipped(item)
        }
    }
    LaunchedEffect(requestSheet != null) {
        if (requestSheet != null) {
            recent = viewModel.recentIngredients()
            left = viewModel.leftToday()
        }
    }

    fun openReview(period: String, key: String) {
        viewModel.markOpened(period, key)
        onOpenReview(period, key)
    }

    GradientBackdrop(theme = BackdropTheme.TRENDS) {
        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().swipeNavigation(onSwipeLeft = onSwipeLeft, onSwipeRight = onSwipeRight),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(com.fitpal.app.ui.theme.CardGap + 2.dp)
        ) {
            item { Text("Coach", style = MaterialTheme.typography.headlineLarge, color = Cream) }

            // ---- Today ----
            item {
                Section("Today") {
                    Row(
                        Modifier.fillMaxWidth().clickable { openReview("daily", viewModel.todayKey) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Your daily overview", style = MaterialTheme.typography.bodyLarge, color = Cream, modifier = Modifier.weight(1f))
                        if (ui.todayReady) ReadyDot()
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CreamFaint)
                    }
                    ui.todayQuestion?.let { q ->
                        Spacer(Modifier.height(6.dp))
                        Text("The coach asks: “${q.question}”", style = MaterialTheme.typography.bodyMedium, color = Cream)
                        Spacer(Modifier.height(8.dp))
                        if (q.answered != null) {
                            Text("You said: ${q.answered}", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                        } else if (q.answers.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                q.answers.forEach { a -> SetupPill(a, selected = false) { viewModel.answer(a) } }
                            }
                        }
                    }
                }
            }

            // ---- Reviews ----
            item {
                Section("Reviews") {
                    ui.reviewRows.forEachIndexed { i, row ->
                        if (i > 0) Divider()
                        LinkRow(row.title, row.subtitle, ready = row.ready) { openReview(row.period, row.key) }
                    }
                    // Finished weeks and months that never got an overview: the newest three, then the rest.
                    if (ui.skipped.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "SKIPPED", style = MaterialTheme.typography.labelSmall, color = CreamFaint,
                            letterSpacing = 1.2.sp
                        )
                        ui.skipped.take(SKIPPED_SHOWN).forEachIndexed { i, item ->
                            if (i > 0) Divider()
                            SkippedRow(item, onWrite = { openReview(item.period, item.key) }, onDismiss = { dismiss(item) })
                        }
                        val more = ui.skipped.size - SKIPPED_SHOWN
                        if (more > 0) {
                            Divider()
                            LinkRow("$more more", "Older skipped weeks and months") { showSkipped = true }
                        }
                    }
                    if (ui.earlier.isNotEmpty()) {
                        Divider()
                        LinkRow("Earlier reviews", "${ui.earlier.size} kept") { showEarlier = true }
                    }
                    // What the coach has learned about you from your answers (2.18, #2).
                    Divider()
                    LinkRow(
                        "What your coach knows",
                        when (ui.memoryCount) { 0 -> "It learns from your answers"; 1 -> "1 thing"; else -> "${ui.memoryCount} things" }
                    ) { onOpenMemory() }
                }
            }

            // ---- Meal planner (off = invisible) ----
            if (plannerOn) item {
                Section("Meal planner") {
                    when (val s = job?.status) {
                        is PlanStatus.Running -> AnalysingStrip(message = s.message, source = s.source)
                        is PlanStatus.OnlineFailed -> InlineMessageCard(
                            title = "Online AI isn't available",
                            reason = s.reason,
                            primary = "Try online again" to { viewModel.retryOnline() },
                            secondary = "Use on-device" to { viewModel.useOnDevice() },
                            tertiary = "Cancel" to { viewModel.cancelPlan() }
                        )
                        is PlanStatus.Failed -> InlineMessageCard(
                            title = "Couldn't make the plan",
                            reason = s.message,
                            primary = "Try again" to { requestSheet = viewModel.requestFor(job?.scope ?: PlanScope.DINNER) },
                            tertiary = "Dismiss" to { viewModel.dismissJob() }
                        )
                        is PlanStatus.Done -> LinkRow(
                            "Your plan is ready",
                            if (s.count == 1) "1 recipe" else "${s.count} recipes",
                            ready = true
                        ) { job?.requestId?.let(onOpenPlan); viewModel.dismissJob() }
                        null -> Unit
                    }
                    if (job?.status is PlanStatus.Running || job?.status is PlanStatus.OnlineFailed) Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoldPill("Plan a meal", { requestSheet = viewModel.requestFor(PlanScope.DINNER) }, modifier = Modifier.aiGlow())
                        SetupPill("Plan my day", selected = false, modifier = Modifier.aiGlow()) { requestSheet = viewModel.requestFor(PlanScope.DAY) }
                        SetupPill("Plan my week", selected = false, modifier = Modifier.aiGlow()) { requestSheet = viewModel.requestFor(PlanScope.WEEK) }
                    }
                    Spacer(Modifier.height(6.dp))
                    upcoming.firstOrNull()?.let { m ->
                        Divider()
                        LinkRow(
                            "Coming up: " + dayWord(m.date) + " · " + MealTypes.label(m.mealType ?: MealTypes.DINNER).lowercase(),
                            m.name
                        ) { onOpenRecipe(m.id) }
                    }
                    lastPlan?.let { id ->
                        Divider()
                        LinkRow("Your last plan", "Recipes you haven't scheduled yet") { onOpenPlan(id) }
                    }
                    if (savedCount > 0) {
                        Divider()
                        LinkRow("Ideas you saved", "$savedCount") { onOpenPlan(com.fitpal.app.ui.navigation.Screen.PlanResults.SAVED) }
                    }
                    Divider()
                    LinkRow("Your food profile", profile.summary) { onOpenFoodProfile() }
                }
            }
        }
        androidx.compose.material3.SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 104.dp)
        )
        }
    }

    if (showSkipped) {
        TrackerSheet(title = "Skipped reviews", onDismiss = { showSkipped = false }) {
            // Up to three months of weeks and months: scrolls inside the sheet.
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            ui.skipped.forEachIndexed { i, item ->
                if (i > 0) Divider()
                SkippedRow(
                    item,
                    onWrite = { showSkipped = false; openReview(item.period, item.key) },
                    onDismiss = { dismiss(item); if (ui.skipped.size <= 1) showSkipped = false }
                )
            }
            }
        }
    }

    requestSheet?.let { initial ->
        PlanRequestSheet(
            initial = initial,
            favouriteCuisines = profile.cuisines,
            recentIngredients = recent,
            leftToday = left,
            onlineAvailable = viewModel.onlineAvailable(),
            onGenerate = { req ->
                requestSheet = null
                scope.launch { onOpenPlan(viewModel.startPlan(req)) }
            },
            onDismiss = { requestSheet = null }
        )
    }

    if (showEarlier) {
        TrackerSheet(title = "Earlier reviews", onDismiss = { showEarlier = false }) {
            // Up to 30 kept: scrolls inside the sheet (it used to run off the bottom).
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            ui.earlier.forEachIndexed { i, r ->
                if (i > 0) Divider()
                val title = when (r.period) {
                    "daily" -> runCatching { LocalDate.parse(r.periodKey).format(DateTimeFormatter.ofPattern("EEE d MMM")) }.getOrDefault(r.periodKey)
                    "weekly" -> "Week of " + runCatching { LocalDate.parse(r.periodKey).format(DateTimeFormatter.ofPattern("d MMM")) }.getOrDefault(r.periodKey)
                    else -> runCatching { java.time.YearMonth.parse(r.periodKey).format(DateTimeFormatter.ofPattern("MMMM yyyy")) }.getOrDefault(r.periodKey)
                }
                val firstLine = com.fitpal.app.ml.ReviewText.parse(r.text).prose.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                LinkRow(title, firstLine) { showEarlier = false; openReview(r.period, r.periodKey) }
            }
            }
        }
    }
}

private fun dayWord(iso: String?): String {
    val d = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return ""
    return when (d) {
        LocalDate.now() -> "Today"
        LocalDate.now().plusDays(1) -> "Tomorrow"
        else -> logDateLabel(d)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Cream)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = filmColor(0.08f))

@Composable
private fun LinkRow(title: String, subtitle: String?, ready: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (ready) ReadyDot()
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CreamFaint)
    }
}

/** How many skipped periods sit right in the Reviews card; the rest are one row away. */
private const val SKIPPED_SHOWN = 3

/** A finished week or month with no overview: its name and how much was logged, "✦ Write" and ×. */
@Composable
private fun SkippedRow(item: com.fitpal.app.domain.SkippedReviews.Skipped, onWrite: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = CreamMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        SetupPill("✦ Write", selected = false, modifier = Modifier.aiGlow(), onClick = onWrite)
        Icon(
            Icons.Default.Close, contentDescription = "Remove ${item.title}", tint = CreamMuted,
            modifier = Modifier.padding(start = 2.dp).size(40.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(11.dp)
        )
    }
}

/** "New since you last looked." */
@Composable
private fun ReadyDot() {
    Box(Modifier.padding(end = 6.dp).size(8.dp).clip(CircleShape).background(GoldLight))
}
