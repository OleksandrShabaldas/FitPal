package com.fitpal.app.ui.screen.coach

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitpal.app.domain.model.RecipeStep
import com.fitpal.app.ml.CookTimers
import com.fitpal.app.ui.component.GlassButton
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.NumberFamily
import com.fitpal.app.ui.theme.OnGold
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glassSoft
import kotlinx.coroutines.launch

/*
 * A planned recipe's steps (docs/redesign/14): grouped by phase, each with a title, the full
 * instruction, the ingredients it uses and — for a waiting step — a timer; and cook mode, one step
 * per page with a big countdown. Recipes planned before 2.15 have plain steps and show as before.
 */

/** "PREP", "COOK", "SERVE" — the small gold label over each phase's first step. */
internal fun phaseLabel(phase: String): String = when (phase) {
    RecipeStep.PREP -> "Prep"
    RecipeStep.SERVE -> "Serve"
    RecipeStep.COOK -> "Cook"
    else -> ""
}

/** "12:00", "4:32". */
internal fun clockOf(ms: Long): String {
    val s = (ms + 999L) / 1000L
    return "%d:%02d".format(s / 60L, s % 60L)
}

/** One step on the recipe page. The timer chip starts, pauses and resets that step's timer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecipeStepRow(
    number: Int,
    step: RecipeStep,
    timer: CookTimers.Timer?,
    now: Long,
    onTimerTap: () -> Unit
) {
    Row(Modifier.fillMaxWidth().glassSoft().padding(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(GoldLight), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = OnGold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (step.title.isNotBlank()) Text(step.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Cream)
            Text(step.text, style = MaterialTheme.typography.bodyMedium, color = Cream)
            if (step.uses.isNotEmpty() || step.minutes != null) {
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    step.uses.forEach { SmallChip(it) }
                    step.minutes?.let { TimerChip(it, timer, now, onTimerTap) }
                }
            }
        }
    }
}

@Composable
private fun SmallChip(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = CreamMuted, maxLines = 1,
        modifier = Modifier.glassSoft(RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** "⏱ 12 min" → tap to start; "⏱ 4:32" running; "⏸ 4:32" paused; "✓ Done" when it's rung. */
@Composable
private fun TimerChip(minutes: Int, timer: CookTimers.Timer?, now: Long, onTap: () -> Unit) {
    val left = timer?.remaining(now)
    val text = when {
        timer == null -> "⏱ $minutes min"
        left == 0L -> "✓ Done"
        timer.running -> "⏱ ${clockOf(left!!)}"
        else -> "⏸ ${clockOf(left!!)}"
    }
    Text(
        text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = GoldLight, maxLines = 1,
        modifier = Modifier.accentGlass(GoldLight, RoundedCornerShape(50)).clickable(onClick = onTap).padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

/**
 * Cook mode: one step per page — where you are, the step big, what it uses, and its timer with a
 * big countdown. Back / Next at the bottom; Next on the last step leaves cook mode.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun CookModePager(
    steps: List<RecipeStep>,
    timers: Map<String, CookTimers.Timer>,
    keyFor: (Int) -> String,
    now: Long,
    onStart: (Int) -> Unit,
    onPause: (Int) -> Unit,
    onReset: (Int) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pager = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
            val step = steps[i]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val phase = phaseLabel(step.phase)
                Text(
                    "Step ${i + 1} of ${steps.size}" + (if (phase.isNotEmpty()) " · $phase" else ""),
                    style = MaterialTheme.typography.labelLarge, color = CreamMuted
                )
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(filmColor(0.10f))) {
                    Box(Modifier.fillMaxWidth((i + 1f) / steps.size).height(4.dp).clip(RoundedCornerShape(50)).background(GoldLight))
                }
                if (step.title.isNotBlank()) Text(step.title, style = MaterialTheme.typography.headlineSmall, color = Cream)
                Text(step.text, style = MaterialTheme.typography.titleLarge, color = Cream)
                if (step.uses.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        step.uses.forEach { SmallChip(it) }
                    }
                }
                step.minutes?.let { minutes ->
                    val timer = timers[keyFor(i)]
                    val left = timer?.remaining(now) ?: minutes * 60_000L
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (timer != null && left == 0L) "Done" else clockOf(left),
                            style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, fontFeatureSettings = "tnum"),
                            color = if (timer?.running == true) GoldLight else Cream
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            when {
                                timer == null -> GoldButton("Start timer", onClick = { onStart(i) })
                                left == 0L -> GlassButton("Reset", onClick = { onReset(i) })
                                timer.running -> {
                                    GlassButton("Pause", onClick = { onPause(i) })
                                    GlassButton("Reset", onClick = { onReset(i) })
                                }
                                else -> {
                                    GoldButton("Resume", onClick = { onStart(i) })
                                    GlassButton("Reset", onClick = { onReset(i) })
                                }
                            }
                        }
                        Text("You'll get a notification when it's done.", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val page = pager.currentPage
            if (page > 0) GlassButton("‹ Back", onClick = { scope.launch { pager.animateScrollToPage(page - 1) } }, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            GoldButton(
                if (page < steps.size - 1) "Next ›" else "Done",
                onClick = { if (page < steps.size - 1) scope.launch { pager.animateScrollToPage(page + 1) } else onDone() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
