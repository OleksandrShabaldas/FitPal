package com.fitpal.app.ui.screen.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.fitpal.app.ui.component.kit.FpSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.DayScore
import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.domain.model.FastingPreset
import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.domain.model.Sex
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.CalorieRing
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MacroRingsRow
import com.fitpal.app.ui.component.fastingClockLabel
import com.fitpal.app.ui.component.color
import com.fitpal.app.ui.component.setupSentence
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.FastingColor
import com.fitpal.app.ui.theme.CaffeineColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.DessertColor
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InkBlack
import com.fitpal.app.ui.theme.NumberFamily
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft
import kotlinx.coroutines.delay

/** The intro's steps, in order. [counted] steps get a segment in the progress bar. */
private enum class Step(val skippable: Boolean, val counted: Boolean) {
    WELCOME(false, false),
    HOW(true, true),
    RING(true, true),
    ABOUT(true, true),
    YOUR_DAY(true, true),
    GOAL(true, true),
    PLAN(false, true),
    TRACK(true, true),
    SETUP(false, true),
    AI(true, true),
    REMINDERS(true, true),
    DONE(false, false)
}

/**
 * The first-run intro — and its replay from Settings ([replay]). A short, animated walk through what
 * FitPal does (logging, the Home ring), the few stats the targets need, a live preview of the plan
 * those make, and the optional features to switch on. Nothing is written until the last step, so
 * backing out of a replay changes nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    replay: Boolean = false,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    // Which picked feature's setup screen is showing (SET UP walks them one after another).
    var setupIndex by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val step = Step.entries[stepIndex]
    val toSetUp = draft.toSetUp
    val setupFeature = toSetUp.getOrNull(setupIndex)

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // The plan needs a weight to work from — without one, it's skipped (both ways). SET UP only has
    // screens for the features picked on TRACK, so with none it's skipped too.
    fun next(from: Step): Step = when (from) {
        Step.GOAL -> if (draft.targets != null) Step.PLAN else Step.TRACK
        Step.TRACK -> if (toSetUp.isNotEmpty()) Step.SETUP else Step.AI
        Step.DONE -> Step.DONE
        else -> Step.entries[from.ordinal + 1]
    }
    fun previous(from: Step): Step = when (from) {
        Step.TRACK -> if (draft.targets != null) Step.PLAN else Step.GOAL
        Step.AI -> if (toSetUp.isNotEmpty()) Step.SETUP else Step.TRACK
        Step.WELCOME -> Step.WELCOME
        else -> Step.entries[from.ordinal - 1]
    }
    fun go(to: Step) {
        forward = to.ordinal > stepIndex
        if (to == Step.SETUP) setupIndex = if (forward) 0 else (toSetUp.size - 1).coerceAtLeast(0)
        stepIndex = to.ordinal
    }
    // Inside SET UP, Continue and Back move between the picked features first.
    fun advance() {
        if (step == Step.SETUP && setupIndex < toSetUp.size - 1) { forward = true; setupIndex++ } else go(next(step))
    }
    fun back() {
        if (step == Step.SETUP && setupIndex > 0) { forward = false; setupIndex-- } else go(previous(step))
    }
    fun finish() = viewModel.finish(onFinish)

    BackHandler(enabled = stepIndex > 0 && step != Step.DONE) { back() }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        DriftingGlow()
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            TopBar(
                step = step,
                onBack = { back() },
                onSkip = {
                    when (step) {
                        // No body stats → no plan to show; go to the goal.
                        Step.ABOUT -> go(Step.GOAL)
                        else -> go(next(step))
                    }
                }
            )

            AnimatedContent(
                targetState = step to setupIndex,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(380)) { it * dir / 3 } + fadeIn(tween(380))) togetherWith
                        (slideOutHorizontally(tween(300)) { -it * dir / 3 } + fadeOut(tween(250)))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "onboardingStep"
            ) { (s, index) ->
                when (s) {
                    Step.WELCOME -> WelcomeStep(replay)
                    Step.HOW -> HowStep()
                    Step.RING -> RingStep()
                    Step.ABOUT -> AboutStep(draft, viewModel)
                    Step.YOUR_DAY -> YourDayStep(draft, viewModel)
                    Step.GOAL -> GoalStep(draft, viewModel)
                    Step.PLAN -> PlanStep(draft)
                    Step.TRACK -> TrackStep(draft, viewModel)
                    Step.SETUP -> draft.toSetUp.getOrNull(index)?.let { f -> SetupStep(f, index, draft.toSetUp.size, draft, viewModel) }
                    Step.AI -> AiStep(draft, viewModel)
                    Step.REMINDERS -> RemindersStep(draft, viewModel, onReminderOn = ::askForNotifications)
                    Step.DONE -> DoneStep(draft, replay)
                }
            }

            BottomBar(
                label = when (step) {
                    Step.WELCOME -> if (replay) "Take the tour" else "Let's begin"
                    Step.HOW, Step.RING -> "Next"
                    Step.PLAN -> "Looks good"
                    Step.AI -> if (draft.geminiKey.isBlank()) "Continue" else "Save key & continue"
                    Step.DONE -> if (replay) "Done" else "Start using FitPal"
                    else -> "Continue"
                },
                enabled = when (step) {
                    Step.ABOUT -> draft.aboutYouValid
                    Step.SETUP -> setupFeature == null || draft.setup.canTurnOn(setupFeature)
                    else -> true
                },
                onClick = {
                    when (step) {
                        // Leaving the reminders is the natural moment to ask — reminders, and the "your
                        // photo's been analysed" notification, both need it.
                        Step.REMINDERS -> { askForNotifications(); go(Step.DONE) }
                        Step.DONE -> finish()
                        else -> advance()
                    }
                },
                secondary = when (step) {
                    Step.WELCOME -> (if (replay) "Close" else "Skip setup") to {
                        if (replay) onFinish() else { viewModel.skip(); onFinish() }
                    }
                    Step.SETUP -> setupFeature?.let { f -> "Use the defaults" to { viewModel.useDefaults(f); advance() } }
                    else -> null
                }
            )
        }
    }
}

// ======================== CHROME ========================

@Composable
private fun TopBar(step: Step, onBack: () -> Unit, onSkip: () -> Unit) {
    val showChrome = step != Step.WELCOME && step != Step.DONE
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (showChrome) {
                Box(
                    modifier = Modifier.size(40.dp).glass(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Cream, modifier = Modifier.size(20.dp))
                }
            }
        }
        Box(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            if (showChrome) ProgressSegments(step)
        }
        Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
            if (showChrome && step.skippable) {
                Text(
                    "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = CreamMuted,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onSkip).padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** One thin segment per counted step; they fill in gold as you go. */
@Composable
private fun ProgressSegments(step: Step) {
    val counted = Step.entries.filter { it.counted }
    val current = counted.indexOf(step)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        counted.forEachIndexed { i, _ ->
            val color by animateColorAsState(
                if (i <= current) GoldLight else com.fitpal.app.ui.theme.filmColor(0.12f),
                animationSpec = tween(400), label = "segment"
            )
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

@Composable
private fun BottomBar(label: String, enabled: Boolean, onClick: () -> Unit, secondary: Pair<String, () -> Unit>?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = GoldLight, contentColor = InkBlack),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        secondary?.let { (text, action) ->
            TextButton(onClick = action) { Text(text, color = CreamMuted) }
        }
    }
}

/** Fades + lifts its content in, [index] beats after the step appears — so a screen assembles itself. */
@Composable
private fun Staggered(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(90L + 110L * index)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 4 }
    ) { content() }
}

@Composable
private fun StepColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = Cream)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
    }
}

// ======================== STEPS ========================

@Composable
private fun WelcomeStep(replay: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(24.dp))
        GlowRing(target = 0.72f, diameter = 190.dp) {
            Icon(Icons.Default.Restaurant, contentDescription = null, tint = GoldLight, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(30.dp))
        Staggered(0) {
            Text("FitPal", style = MaterialTheme.typography.displayLarge, color = Cream, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(6.dp))
        Staggered(1) {
            Text(
                buildAnnotatedString {
                    append("Eat well, ")
                    withStyle(SpanStyle(color = GoldLight, fontStyle = FontStyle.Italic)) { append("know more") }
                    append(", worry less.")
                },
                style = MaterialTheme.typography.headlineSmall, color = Cream, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(16.dp))
        Staggered(2) {
            Text(
                if (replay) "Here's the tour again. Everything starts from your current settings, and nothing changes unless you change it."
                else "A calm, clever food diary that does the counting for you. About two minutes to set up — and everything can be changed later.",
                style = MaterialTheme.typography.bodyMedium, color = CreamMuted, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HowStep() {
    StepColumn {
        Staggered(0) { StepTitle("Logging takes seconds", "Three easy ways in — use whichever suits the moment.") }
        Staggered(1) {
            FeatureCard(
                Icons.Default.PhotoCamera, "Snap your plate",
                "Take or pick a photo. The AI names each food, sizes the portions — and reads the time you ate from the photo itself."
            )
        }
        Staggered(2) {
            FeatureCard(
                Icons.Default.AutoAwesome, "Or just say it",
                "Type \"chicken wrap and a latte\" and it fills in the nutrition. While it thinks, you can add where and when you ate."
            )
        }
        Staggered(3) {
            FeatureCard(
                Icons.Default.QrCodeScanner, "Scan, search, repeat",
                "Barcodes, a big food database, and the foods you eat often — back again with one tap."
            )
        }
        Staggered(4) {
            Row(
                modifier = Modifier.fillMaxWidth().glassSoft().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulsingPlus(diameter = 30.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "It all starts from the gold + in the middle of the bottom bar.",
                    style = MaterialTheme.typography.bodyMedium, color = Cream
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, body: String) {
    Row(modifier = Modifier.fillMaxWidth().glass().padding(16.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(GoldLight.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = GoldLight, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Cream)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
    }
}

@Composable
private fun RingStep() {
    // The real Home ring and macro rings, filling up with a sample day.
    var filled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350)
        filled = true
    }
    StepColumn {
        Staggered(0) { StepTitle("Your day at a glance", "Home keeps score for you — here's how to read it.") }
        Staggered(1) {
            Column(
                modifier = Modifier.fillMaxWidth().glass().padding(vertical = 18.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CalorieRing(
                    consumed = if (filled) 1340 else 0, goal = 2000, tier = DayScore.Tier.GOOD, diameter = 150.dp,
                    balanceScore = 84, balanceHint = "nicely balanced"
                )
                Spacer(Modifier.height(14.dp))
                MacroRingsRow(
                    protein = if (filled) 96f else 0f, proteinTarget = 130f,
                    fat = if (filled) 46f else 0f, fatTarget = 70f,
                    carbs = if (filled) 150f else 0f, carbTarget = 220f,
                    fiber = if (filled) 17f else 0f, fiberTarget = 30f
                )
            }
        }
        Staggered(2) { Bullet("The ring fills as you eat. The big number is what's left today — tap it for your balance score.") }
        Staggered(3) { Bullet("It only turns red if you go over. Workouts and steps you log earn calories back.") }
        Staggered(4) { Bullet("Protein and fibre are goals to reach; fat and carbs are limits to stay under.") }
        Staggered(5) { Bullet("Swipe the card sideways for vitamins & minerals, or tap the little arrow on top for your food limits and caffeine.") }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(GoldLight))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Cream)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutStep(draft: OnboardingDraft, viewModel: OnboardingViewModel) {
    StepColumn {
        Staggered(0) { StepTitle("A little about you", "Your targets are worked out from these. They stay on your phone.") }
        Staggered(1) {
            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("I am", style = MaterialTheme.typography.labelLarge, color = CreamMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sex.entries.forEach { s ->
                        Choice(label = s.label, selected = draft.sex == s) { viewModel.update { it.copy(sex = s) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.age,
                        onValueChange = { v -> viewModel.update { it.copy(age = v.filter { c -> c.isDigit() }.take(3)) } },
                        label = { Text("Age") }, suffix = { Text("yr") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draft.height,
                        onValueChange = { v -> viewModel.update { it.copy(height = v.filter { c -> c.isDigit() }.take(3)) } },
                        label = { Text("Height") }, suffix = { Text("cm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = draft.weight,
                    onValueChange = viewModel::setWeight,
                    label = { Text("Current weight") }, suffix = { Text("kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (!draft.aboutYouValid && (draft.weight.isNotEmpty() || draft.age.isEmpty() || draft.height.isEmpty())) {
                    Text(
                        "Age 10–120, height 50–300 cm and weight 20–400 kg.",
                        style = MaterialTheme.typography.labelSmall, color = CreamFaint
                    )
                }
            }
        }
    }
}

/** Plain-language goal names — the app's short labels ("Recomp") mean little on day one. */
private fun goalTitle(g: FitnessGoal): String = when (g) {
    FitnessGoal.LOSE_FAT -> "Lose body fat"
    FitnessGoal.RECOMP -> "Lose fat, build muscle"
    FitnessGoal.BUILD_MUSCLE -> "Build muscle"
    FitnessGoal.MAINTAIN -> "Stay where I am"
}

private fun goalBody(g: FitnessGoal): String = when (g) {
    FitnessGoal.LOSE_FAT -> "A steady calorie deficit, with plenty of protein to keep your muscle."
    FitnessGoal.RECOMP -> "A slight deficit and lots of protein — best alongside strength training."
    FitnessGoal.BUILD_MUSCLE -> "A small surplus, with the protein and carbs to grow."
    FitnessGoal.MAINTAIN -> "Eat at maintenance and keep things balanced."
}

private fun goalIcon(g: FitnessGoal): ImageVector = when (g) {
    FitnessGoal.LOSE_FAT -> Icons.AutoMirrored.Filled.TrendingDown
    FitnessGoal.RECOMP -> Icons.Default.Autorenew
    FitnessGoal.BUILD_MUSCLE -> Icons.Default.FitnessCenter
    FitnessGoal.MAINTAIN -> Icons.Default.Balance
}

@Composable
private fun GoalStep(draft: OnboardingDraft, viewModel: OnboardingViewModel) {
    StepColumn {
        Staggered(0) { StepTitle("What are you aiming for?", "This shapes your calorie target and how much protein you'll aim for.") }
        FitnessGoal.entries.forEachIndexed { i, g ->
            Staggered(i + 1) {
                val selected = draft.goal == g
                val shape = RoundedCornerShape(22.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (selected) Modifier.accentGlass(GoldLight, shape) else Modifier.glass(shape))
                        .clickable { viewModel.update { it.copy(goal = g) } }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(goalIcon(g), contentDescription = null, tint = if (selected) GoldLight else CreamMuted, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(goalTitle(g), style = MaterialTheme.typography.titleMedium, color = if (selected) GoldLight else Cream)
                        Text(goalBody(g), style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    }
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = GoldLight, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanStep(draft: OnboardingDraft) {
    val t = draft.targets
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250)
        shown = true
    }
    StepColumn {
        Staggered(0) {
            StepTitle("Your daily plan", "Worked out from your body and your goal: ${goalTitle(draft.goal).lowercase()}.")
        }
        val kcal by animateIntAsState(
            if (shown && t != null) t.calories else 0, tween(1400, easing = FastOutSlowInEasing), label = "planKcal"
        )
        if (t == null) {
            Text("Add your details to see your plan.", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        } else Staggered(1) {
            Column(
                modifier = Modifier.fillMaxWidth().glass().padding(vertical = 22.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "%,d".format(kcal),
                    style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, fontFeatureSettings = "tnum"),
                    color = GoldLight
                )
                Text("kcal a day", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                Spacer(Modifier.height(18.dp))
                MacroRingsRow(
                    protein = if (shown) t.proteinG.toFloat() else 0f, proteinTarget = t.proteinG.toFloat(),
                    fat = if (shown) t.fatG.toFloat() else 0f, fatTarget = t.fatG.toFloat(),
                    carbs = if (shown) t.carbsG.toFloat() else 0f, carbTarget = t.carbsG.toFloat(),
                    fiber = if (shown) t.fiberG.toFloat() else 0f, fiberTarget = t.fiberG.toFloat()
                )
                draft.waterGoalMl?.let { ml ->
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = AccentTrends, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "And about ${"%.1f".format(ml / 1000f)} L of water, from drinks and food",
                            style = MaterialTheme.typography.bodyMedium, color = Cream
                        )
                    }
                }
            }
        }
        if (t != null) {
            Staggered(2) { Bullet("Workouts and steps you log add calories back on the day you do them.") }
            Staggered(3) { Bullet("Change any of this later in Settings → Profile & goals.") }
        }
    }
}

@Composable
private fun AiStep(draft: OnboardingDraft, viewModel: OnboardingViewModel) {
    var howOpen by remember { mutableStateOf(false) }
    StepColumn {
        Staggered(0) { StepTitle("Let the AI do the counting", "It reads your photos and descriptions, and works two ways:") }
        Staggered(1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniInfoCard(Icons.Default.Cloud, "Online", "Google's Gemini — the sharpest and fastest. Needs your own free key.", Modifier.weight(1f))
                MiniInfoCard(Icons.Default.PhoneAndroid, "On your phone", "Works with no internet, after a one-time download in Settings → AI.", Modifier.weight(1f))
            }
        }
        Staggered(2) {
            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                OutlinedTextField(
                    value = draft.geminiKey,
                    onValueChange = { v -> viewModel.update { it.copy(geminiKey = v.trim()) } },
                    label = { Text("Gemini API key (optional)") },
                    placeholder = { Text("Paste your key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AnimatedVisibility(visible = draft.geminiKey.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = ScoreFair, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("You'll get online AI whenever you have internet.", style = MaterialTheme.typography.bodySmall, color = ScoreFair)
                    }
                }
                Spacer(Modifier.height(10.dp))
                val arrow by animateFloatAsState(if (howOpen) 180f else 0f, label = "howArrow")
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { howOpen = !howOpen }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("How do I get a free key?", style = MaterialTheme.typography.labelLarge, color = GoldLight)
                    Icon(Icons.Default.ExpandMore, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp).rotate(arrow))
                }
                AnimatedVisibility(visible = howOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Text("1. Open aistudio.google.com and sign in with Google.", style = MaterialTheme.typography.bodySmall, color = Cream)
                        Text("2. Tap \"Get API key\", then \"Create API key\".", style = MaterialTheme.typography.bodySmall, color = Cream)
                        Text("3. Copy it and paste it above.", style = MaterialTheme.typography.bodySmall, color = Cream)
                    }
                }
            }
        }
        Staggered(3) {
            Text(
                "Heads-up: on Google's free tier, what you send (meal photos included) may be used to improve their AI. " +
                    "No key? Skip this — add one any time in Settings → AI.",
                style = MaterialTheme.typography.labelSmall, color = CreamFaint
            )
        }
    }
}

@Composable
private fun MiniInfoCard(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.glass().padding(14.dp)) {
        Icon(icon, contentDescription = null, tint = GoldLight, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = Cream)
        Spacer(Modifier.height(2.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
    }
}

@Composable
private fun YourDayStep(draft: OnboardingDraft, viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    fun pick(minutes: Int, onPick: (Int) -> Unit) {
        android.app.TimePickerDialog(context, { _, hh, mm -> onPick(hh * 60 + mm) }, minutes / 60, minutes % 60, false).show()
    }
    StepColumn {
        Staggered(0) {
            StepTitle("Your day", "We use these to pace your water and to tell you if caffeine will keep you up.")
        }
        Staggered(1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeTile(Icons.Default.WbSunny, "Wake up", draft.wakeMin, Modifier.weight(1f)) {
                    pick(draft.wakeMin) { m -> viewModel.update { it.copy(wakeMin = m) } }
                }
                TimeTile(Icons.Default.Bedtime, "Bedtime", draft.bedMin, Modifier.weight(1f)) {
                    pick(draft.bedMin) { m -> viewModel.update { it.copy(bedMin = m) } }
                }
            }
        }
        Staggered(2) { Bullet("Change them any time in Settings → Profile & goals → Your day.") }
    }
}

/** A big tappable time: icon, label, the time in large numbers. */
@Composable
private fun TimeTile(icon: ImageVector, label: String, minutes: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.glass().clickable(onClick = onClick).padding(vertical = 18.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = GoldLight, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = CreamMuted)
        Text(
            fastingClockLabel(minutes),
            style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, fontFeatureSettings = "tnum"),
            color = Cream
        )
    }
}

/** "What do you want to keep an eye on?" — tap the optional features you want; the core is always on. */
@Composable
private fun TrackStep(draft: OnboardingDraft, viewModel: OnboardingViewModel) {
    StepColumn {
        Staggered(0) { StepTitle("What do you want to keep an eye on?", "Pick any — you'll set each one up next. Change it later in Settings → Features.") }
        com.fitpal.app.domain.model.Feature.entries.forEachIndexed { i, feature ->
            Staggered(1 + i) {
                val picked = feature in draft.features
                val shape = RoundedCornerShape(22.dp)
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .then(if (picked) Modifier.accentGlass(feature.color, shape) else Modifier.glass(shape))
                        .clickable { viewModel.toggleFeature(feature) }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.fitpal.app.ui.component.FeatureIconCircle(feature, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(feature.title, style = MaterialTheme.typography.titleMedium, color = Cream)
                            Text(feature.line, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                        }
                        Icon(
                            if (picked) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                            contentDescription = if (picked) "Picked" else "Not picked",
                            tint = if (picked) feature.color else CreamFaint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // Picking one shows what it adds.
                    AnimatedVisibility(visible = picked, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        com.fitpal.app.ui.component.FeatureVisual(feature, draft.setup, Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
        Staggered(6) {
            Text(
                "Always on: calories, meals, water, steps and weight.",
                style = MaterialTheme.typography.bodySmall, color = CreamFaint
            )
        }
    }
}

/** One picked feature's basic setup — the same screen as Settings → Features opens. */
@Composable
private fun SetupStep(
    feature: com.fitpal.app.domain.model.Feature,
    index: Int,
    count: Int,
    draft: OnboardingDraft,
    viewModel: OnboardingViewModel
) {
    StepColumn {
        Staggered(0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (count > 1) Text("${index + 1} of $count", style = MaterialTheme.typography.labelLarge, color = feature.color)
                StepTitle(feature.title, feature.setupSentence)
            }
        }
        Staggered(1) { com.fitpal.app.ui.component.FeatureVisual(feature, draft.setup) }
        Staggered(2) {
            Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                com.fitpal.app.ui.component.FeatureSetupControls(feature, draft.setup) { s -> viewModel.update { it.copy(setup = s) } }
            }
        }
    }
}

@Composable
private fun RemindersStep(draft: OnboardingDraft, viewModel: OnboardingViewModel, onReminderOn: () -> Unit) {
    StepColumn {
        Staggered(0) { StepTitle("A gentle nudge?", "Only if it helps — change the times any time in Settings → Reminders.") }
        Staggered(1) {
            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = GoldLight, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Reminders", style = MaterialTheme.typography.titleMedium, color = Cream)
                }
                SwitchRow("Meal reminders", "Breakfast, lunch and dinner", draft.mealReminders) { on ->
                    viewModel.update { it.copy(mealReminders = on) }
                    if (on) onReminderOn()
                }
                SwitchRow("Morning weigh-in", "Opens straight to the weigh-in", draft.weighInReminder) { on ->
                    viewModel.update { it.copy(weighInReminder = on) }
                    if (on) onReminderOn()
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Cream)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = CreamFaint)
        }
        FpSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DoneStep(draft: OnboardingDraft, replay: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiBurst()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            Spacer(Modifier.height(12.dp))
            GlowRing(target = 1f, diameter = 140.dp, durationMs = 1100) {
                Icon(Icons.Default.Check, contentDescription = null, tint = GoldLight, modifier = Modifier.size(52.dp))
            }
            Staggered(0) {
                Text("You're all set", style = MaterialTheme.typography.displaySmall, color = Cream, textAlign = TextAlign.Center)
            }
            Staggered(1) {
                Text(
                    if (replay) "Your settings are saved." else "Your first day starts now.",
                    style = MaterialTheme.typography.bodyMedium, color = CreamMuted, textAlign = TextAlign.Center
                )
            }
            Staggered(2) {
                Column(
                    modifier = Modifier.fillMaxWidth().glass().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    draft.targets?.let { SummaryLine("Daily target", "%,d kcal".format(it.calories)) }
                    SummaryLine("Your day", "${fastingClockLabel(draft.wakeMin)} – ${fastingClockLabel(draft.bedMin)}")
                    SummaryLine("AI", if (draft.geminiKey.isNotBlank()) "Online, with on-phone backup" else "Add a key or the on-phone model later")
                    val on = com.fitpal.app.domain.model.Feature.entries.filter { it in draft.features && draft.setup.canTurnOn(it) }
                    SummaryLine("On", if (on.isEmpty()) "Just the basics — add more in Settings → Features" else on.joinToString(" · ") { it.title })
                    if (com.fitpal.app.domain.model.Feature.FASTING in on) {
                        SummaryLine("Fasting", "Eat ${fastingClockLabel(draft.setup.eatStartMin)} – ${fastingClockLabel(draft.setup.eatEndMin)}")
                    }
                    if (draft.mealReminders || draft.weighInReminder) {
                        SummaryLine(
                            "Reminders",
                            listOfNotNull(if (draft.mealReminders) "Meals" else null, if (draft.weighInReminder) "Weigh-in" else null).joinToString(" · ")
                        )
                    }
                }
            }
            if (!replay) {
                Staggered(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth().glassSoft().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingPlus(diameter = 30.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Tap the gold + to log your first meal.", style = MaterialTheme.typography.bodyMedium, color = Cream)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.width(100.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
    }
}

/** A selectable pill — gold when chosen. */
@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .then(if (selected) Modifier.accentGlass(GoldLight, shape) else Modifier.glassSoft(shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) GoldLight else Cream)
    }
}
