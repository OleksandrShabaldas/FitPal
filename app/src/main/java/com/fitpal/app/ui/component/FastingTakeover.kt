package com.fitpal.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitpal.app.domain.model.FastingSchedule
import com.fitpal.app.ui.screen.onboarding.GlowRing
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.InterFamily
import com.fitpal.app.ui.theme.glassSoft

/** A lighter step of the fasting blue, for the ring's sweep. */
private val FastingBlueLight = Color(0xFF9CC2FF)

/**
 * INTENSE fasting's stop on Add food: a full-screen "you're fasting" page laid over the logging
 * options — how long is left, a word of encouragement, one-tap ways to log what doesn't break a fast
 * (exercise, a weigh-in), and a slide-to-continue for logging food anyway. Back simply leaves: the easy
 * path is the one that keeps the fast.
 *
 * Swiping only opens the page. The usual log-time check (the "ate earlier" passes, a photo's own time
 * as proof) still runs when something is actually logged — this adds friction, it doesn't decide.
 */
@Composable
fun FastingTakeover(
    schedule: FastingSchedule,
    onLeave: () -> Unit,
    onContinue: () -> Unit,
    onLogExercise: () -> Unit,
    onLogWeight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberFastingState(schedule)
    val elapsed = fastingElapsedMin(schedule, state)
    GradientBackdrop(
        // Swallow stray touches so nothing underneath can be tapped through the page.
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {}
        ),
        theme = BackdropTheme.TRENDS
    ) {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(title = "Add food", onBack = onLeave)
            Column(
                modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.5f))
                Text(
                    "You're fasting",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Cream,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    encouragement(elapsed, state.minutesLeftInPhase, schedule.fastingLengthMin()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CreamMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(26.dp))
                GlowRing(
                    target = state.progressFraction,
                    diameter = 220.dp,
                    colors = listOf(AccentTrends, FastingBlueLight, AccentTrends),
                    glow = AccentTrends
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            fastingCountdownLabel(state.minutesLeftInPhase),
                            style = TextStyle(
                                fontFamily = InterFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 40.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = Cream
                        )
                        Text("until you can eat", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Your eating window opens at ${fastingClockLabel(state.nextChangeMin)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Water, black coffee and plain tea won't break it — nor will logging a workout or your weight.",
                    style = MaterialTheme.typography.labelMedium,
                    color = CreamFaint,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TakeoverPill(Icons.Default.FitnessCenter, "Log exercise", onLogExercise, Modifier.weight(1f))
                    TakeoverPill(Icons.Default.MonitorWeight, "Log weight", onLogWeight, Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                SlideToConfirm(text = "Slide to log food anyway", onConfirm = onContinue)
            }
        }
    }
}

@Composable
private fun TakeoverPill(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .glassSoft(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Cream, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Cream, maxLines = 1)
    }
}

/** A line that meets you where you are in the fast — early, midway, nearly done. */
private fun encouragement(elapsedMin: Int, leftMin: Int, totalMin: Int): String {
    val done = if (totalMin > 0) elapsedMin.toFloat() / totalMin else 0f
    return when {
        leftMin <= 30 -> "Nearly there — just ${fastingCountdownLabel(leftMin)} to go."
        done >= 0.75f -> "${fastingCountdownLabel(elapsedMin)} done. The hard part is behind you."
        done >= 0.4f -> "${fastingCountdownLabel(elapsedMin)} in, and doing well. Keep going."
        elapsedMin >= 60 -> "${fastingCountdownLabel(elapsedMin)} in. Stay with it."
        else -> "Your fast has just begun. You've got this."
    }
}
