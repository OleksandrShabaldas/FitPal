package com.fitpal.app.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.domain.WeightTrend
import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InkBlack
import com.fitpal.app.ui.theme.InterFamily
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.glassOverlay
import com.fitpal.app.ui.theme.glassSoft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The one weigh-in surface — opened by the morning weigh-in notification, the Home weight card and
 * the Analytics weight card (it replaced a separate dialog on each). The number starts at your last
 * weigh-in, −/+ nudge it by 0.1 kg (hold to keep going), or tap it to type.
 */
@Composable
fun WeighInCard(
    latest: WeightEntryEntity?,
    /** The weigh-in before [latest] — what a re-weigh today is compared with. */
    previous: WeightEntryEntity?,
    goal: FitnessGoal,
    onSave: (Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now().toString()
    // Re-weighing today replaces today's entry, so compare against the one before it.
    val compareWith = if (latest?.date == today) previous else latest
    // Keyed on the latest entry so the number fills in once the weigh-in history has loaded.
    var text by remember(latest?.date, latest?.weightKg) { mutableStateOf(latest?.weightKg?.let(::format1) ?: "") }
    val value = parseKg(text)

    Column(
        modifier = modifier.fillMaxWidth().glassOverlay().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MonitorWeight, contentDescription = null, tint = GoldLight, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text("Weigh-in", style = MaterialTheme.typography.headlineMedium, color = Cream)
        Text(
            text = compareWith?.let { "Last time: ${format1(it.weightKg)} kg · ${dayLabel(it.date)}" }
                ?: "Your first weigh-in — it sets your targets",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            // Reads the live text each step (not a value captured when the button was first drawn),
            // so holding the button keeps counting.
            StepButton(Icons.Default.Remove, "Less") { text = format1(((parseKg(text) ?: compareWith?.weightKg ?: 70f) - 0.1f).coerceAtLeast(20f)) }
            Spacer(Modifier.width(14.dp))
            BasicTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 52.sp,
                    fontFeatureSettings = "tnum",
                    color = Cream,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(GoldLight),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { value?.let(onSave) }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (text.isEmpty()) {
                            Text("—", style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, color = CreamFaint))
                        }
                        inner()
                    }
                },
                modifier = Modifier.width(150.dp)
            )
            Spacer(Modifier.width(14.dp))
            StepButton(Icons.Default.Add, "More") { text = format1(((parseKg(text) ?: compareWith?.weightKg ?: 70f) + 0.1f).coerceAtMost(400f)) }
        }
        Text("kg", style = MaterialTheme.typography.titleMedium, color = CreamMuted)

        // How this reading compares with the last one, coloured by whether it suits the goal.
        val delta = if (value != null && compareWith != null) value - compareWith.weightKg else null
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                delta == null -> " "
                abs(delta) < 0.05f -> "Same as last time"
                else -> (if (delta > 0) "+" else "−") + "${format1(abs(delta))} kg since ${dayLabel(compareWith!!.date)}"
            },
            style = MaterialTheme.typography.labelLarge,
            color = when (delta?.let { WeightTrend.changeIsOnTrack(it, goal) }) {
                true -> ScoreFair
                false -> Gold
                null -> CreamMuted
            }
        )

        Spacer(Modifier.height(22.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Not now", color = CreamMuted) }
            Button(
                onClick = { value?.let(onSave) },
                enabled = value != null,
                colors = ButtonDefaults.buttonColors(containerColor = GoldLight, contentColor = InkBlack),
                modifier = Modifier.weight(1f)
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** [WeighInCard] in its own floating dialog, for screens that open it in place. */
@Composable
fun WeighInDialog(
    latest: WeightEntryEntity?,
    previous: WeightEntryEntity?,
    goal: FitnessGoal,
    onSave: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        WeighInCard(
            latest = latest, previous = previous, goal = goal,
            onSave = onSave, onCancel = onDismiss,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

/** A round −/+ button that steps once on tap and keeps stepping while held. */
@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onStep: () -> Unit) {
    val scope = rememberCoroutineScope()
    val step by androidx.compose.runtime.rememberUpdatedState(onStep)
    Box(
        modifier = Modifier
            .size(46.dp)
            .glassSoft(CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    step()
                    val repeat: Job = scope.launch {
                        delay(420)
                        while (true) {
                            step()
                            delay(70)
                        }
                    }
                    waitForUpOrCancellation()
                    repeat.cancel()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Cream, modifier = Modifier.size(22.dp))
    }
}

/** One decimal, always with a dot (a comma locale would otherwise break re-parsing). */
private fun format1(kg: Float): String = String.format(Locale.US, "%.1f", (kg * 10f).roundToInt() / 10f)

private fun parseKg(text: String): Float? =
    text.replace(',', '.').toFloatOrNull()?.takeIf { it in 20f..400f }

private fun dayLabel(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val today = LocalDate.now()
    return when (date) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
}
