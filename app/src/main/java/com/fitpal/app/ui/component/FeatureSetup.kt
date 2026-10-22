package com.fitpal.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.Caffeine
import com.fitpal.app.domain.CaffeineDose
import com.fitpal.app.domain.CaffeineSettings
import com.fitpal.app.domain.StatusKind
import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.domain.model.Feature
import com.fitpal.app.domain.model.FastingIntensity
import com.fitpal.app.domain.model.FastingStyle
import com.fitpal.app.domain.model.FeatureSetup
import com.fitpal.app.ui.theme.AccentGarden
import com.fitpal.app.ui.theme.CaffeineColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.DessertColor
import com.fitpal.app.ui.theme.FastingColor
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glassSoft
import kotlin.math.roundToInt

/*
 * Each optional feature's basic setup (docs/redesign/06): one screen — a visual, one sentence, at most
 * three controls with the defaults already picked. The same pieces serve the Features page (in a
 * sheet), Home's suggestion card and the first-run setup, so a feature is set up the same everywhere.
 */

/** The feature's identity colour. */
val Feature.color: Color
    get() = when (this) {
        Feature.FASTING -> FastingColor
        Feature.CAFFEINE -> CaffeineColor
        Feature.LIMITS -> DessertColor
        Feature.TRAIL -> AccentGarden
    }

val Feature.icon: ImageVector
    get() = when (this) {
        Feature.FASTING -> Icons.Default.HourglassEmpty
        Feature.CAFFEINE -> Icons.Default.LocalCafe
        Feature.LIMITS -> Icons.Default.Icecream
        Feature.TRAIL -> Icons.Default.Landscape
    }

/** The one-sentence explanation at the top of a feature's setup. */
val Feature.setupSentence: String
    get() = when (this) {
        Feature.FASTING -> "Pick when you eat. Home shows the time left, and logging outside the window asks first."
        Feature.CAFFEINE -> "Every coffee, tea and energy drink you log is counted, so you can see what's still in you at bedtime."
        Feature.LIMITS -> "Pick the treats to keep an eye on and a daily cap for each. You'll get a heads-up as you get close."
        Feature.TRAIL -> "Each day you log grows your garden, and challenges unlock along the way. Nothing to set up."
    }

/** The feature's icon in its colour, on a soft circle. */
@Composable
fun FeatureIconCircle(feature: Feature, size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(feature.color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(feature.icon, contentDescription = null, tint = feature.color, modifier = Modifier.size(size * 0.55f))
    }
}

/** A small picture of what the feature adds, from sample data — drawn on the device, offline. */
@Composable
fun FeatureVisual(feature: Feature, setup: FeatureSetup = FeatureSetup(), modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        when (feature) {
            Feature.FASTING -> FastingStyleThumbnail(FastingStyle.RING, Modifier.size(width = 104.dp, height = 88.dp))
            Feature.CAFFEINE -> CaffeineSketch(setup.caffeineHalfLifeHours)
            Feature.LIMITS -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DietaryRuleKind.DESSERT to 0.45f, DietaryRuleKind.FRIED to 0.8f, DietaryRuleKind.SUGARY_DRINK to 1f).forEach { (kind, f) ->
                    TrackerChip(
                        icon = ruleIcon(kind), color = ruleColor(kind),
                        value = "${(kind.defaultLimitKcal * f).roundToInt()}", label = ruleShortName(kind),
                        fraction = f, status = if (f >= 1f) StatusKind.OVER else if (f >= 0.8f) StatusKind.CLOSE else null,
                        modifier = Modifier.width(76.dp)
                    )
                }
            }
            Feature.TRAIL -> GardenSketch()
        }
    }
}

/** Caffeine in the body over a day with one coffee at 8 AM, the "easy on sleep" line and bedtime. */
@Composable
private fun CaffeineSketch(halfLifeHours: Float) {
    val track = filmColor(0.10f)
    val faint = CreamFaint
    Canvas(Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 8.dp)) {
        val hour = 3_600_000L
        val start = 6 * hour
        val end = 24 * hour
        val doses = listOf(CaffeineDose(8 * hour, 95f, "Coffee", estimated = false))
        val samples = 60
        val points = (0..samples).map { i ->
            val t = start + (end - start) * i / samples
            t to Caffeine.amountAt(doses, t, halfLifeHours)
        }
        val maxMg = 100f
        fun x(t: Long) = (t - start).toFloat() / (end - start) * size.width
        fun y(mg: Float) = size.height - (mg / maxMg).coerceIn(0f, 1f) * size.height
        // The "easy on sleep" line and bedtime (11 PM).
        val sleepY = y(CaffeineSettings.SLEEP_FRIENDLY_MG.toFloat())
        drawLine(track, Offset(0f, sleepY), Offset(size.width, sleepY), 1.dp.toPx())
        val bedX = x(23 * hour)
        drawLine(faint, Offset(bedX, 0f), Offset(bedX, size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        val path = Path()
        points.forEachIndexed { i, (t, mg) -> if (i == 0) path.moveTo(x(t), y(mg)) else path.lineTo(x(t), y(mg)) }
        drawPath(path, CaffeineColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(CaffeineColor, 4.dp.toPx(), Offset(x(8 * hour), y(0f)))
    }
}

/** Rolling hills, a winding path and three sprouts — the Trail's garden, in a few strokes. */
@Composable
private fun GardenSketch() {
    val hill = AccentGarden.copy(alpha = 0.35f)
    val hillFront = AccentGarden.copy(alpha = 0.6f)
    val path = GoldLight.copy(alpha = 0.8f)
    Canvas(Modifier.size(width = 200.dp, height = 88.dp)) {
        val w = size.width
        val h = size.height
        drawPath(Path().apply {
            moveTo(0f, h * 0.55f); cubicTo(w * 0.3f, h * 0.25f, w * 0.6f, h * 0.7f, w, h * 0.4f); lineTo(w, h); lineTo(0f, h); close()
        }, hill)
        drawPath(Path().apply {
            moveTo(0f, h * 0.8f); cubicTo(w * 0.35f, h * 0.55f, w * 0.7f, h * 0.95f, w, h * 0.7f); lineTo(w, h); lineTo(0f, h); close()
        }, hillFront)
        drawPath(Path().apply {
            moveTo(w * 0.15f, h); cubicTo(w * 0.35f, h * 0.75f, w * 0.55f, h * 0.85f, w * 0.7f, h * 0.55f)
        }, path, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        listOf(0.28f to 0.72f, 0.5f to 0.68f, 0.82f to 0.62f).forEach { (fx, fy) ->
            val base = Offset(w * fx, h * fy)
            drawLine(hillFront, base, Offset(base.x, base.y - 12.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(AccentGarden, 4.dp.toPx(), Offset(base.x - 4.dp.toPx(), base.y - 12.dp.toPx()))
            drawCircle(AccentGarden, 4.dp.toPx(), Offset(base.x + 4.dp.toPx(), base.y - 14.dp.toPx()))
        }
    }
}

/**
 * The feature's controls (at most three), editing [setup] through [onChange]. The Trail has none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureSetupControls(feature: Feature, setup: FeatureSetup, onChange: (FeatureSetup) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (feature) {
            Feature.FASTING -> {
                SetupLabel("Eating window")
                val preset = FeatureSetup.WINDOW_PRESETS.firstOrNull { it.eatStartMin == setup.eatStartMin && it.eatEndMin == setup.eatEndMin }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureSetup.WINDOW_PRESETS.forEach { p ->
                        SetupPill(p.label, selected = p == preset) { onChange(setup.copy(eatStartMin = p.eatStartMin, eatEndMin = p.eatEndMin)) }
                    }
                    // Custom: pick the start (and then the end, below) yourself.
                    SetupPill("Custom", selected = preset == null) {
                        android.app.TimePickerDialog(context, { _, hh, mm -> onChange(setup.copy(eatStartMin = hh * 60 + mm)) },
                            setup.eatStartMin / 60, setup.eatStartMin % 60, false).show()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Eat", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                    SetupPill(fastingClockLabel(setup.eatStartMin), selected = false) {
                        android.app.TimePickerDialog(context, { _, hh, mm -> onChange(setup.copy(eatStartMin = hh * 60 + mm)) },
                            setup.eatStartMin / 60, setup.eatStartMin % 60, false).show()
                    }
                    Text("–", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                    SetupPill(fastingClockLabel(setup.eatEndMin), selected = false) {
                        android.app.TimePickerDialog(context, { _, hh, mm -> onChange(setup.copy(eatEndMin = hh * 60 + mm)) },
                            setup.eatEndMin / 60, setup.eatEndMin % 60, false).show()
                    }
                }
                SetupLabel("How strict")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FastingIntensity.entries.forEach { level ->
                        SetupPill(level.label, selected = setup.intensity == level) { onChange(setup.copy(intensity = level)) }
                    }
                }
                Text(setup.intensity.blurb, style = MaterialTheme.typography.bodySmall, color = CreamFaint)
            }
            Feature.CAFFEINE -> {
                SetupLabel("Daily limit")
                Stepper("${setup.caffeineLimitMg} mg", onMinus = {
                    onChange(setup.copy(caffeineLimitMg = (setup.caffeineLimitMg - 50).coerceAtLeast(100)))
                }, onPlus = {
                    onChange(setup.copy(caffeineLimitMg = (setup.caffeineLimitMg + 50).coerceAtMost(1000)))
                })
                Text("400 mg is the usual guidance for healthy adults.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
                SetupLabel("How fast it wears off")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureSetup.HALF_LIVES.forEach { (label, hours) ->
                        SetupPill(label, selected = setup.caffeineHalfLifeHours == hours) { onChange(setup.copy(caffeineHalfLifeHours = hours)) }
                    }
                }
            }
            Feature.LIMITS -> {
                DietaryRuleKind.entries.forEach { kind ->
                    val cap = setup.limits[kind]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SetupPill(limitName(kind), selected = cap != null, modifier = Modifier.weight(1f)) {
                            val next = if (cap != null) setup.limits - kind else setup.limits + (kind to kind.defaultLimitKcal)
                            onChange(setup.copy(limits = next))
                        }
                        if (cap != null) {
                            Spacer(Modifier.width(8.dp))
                            Stepper("$cap kcal", onMinus = {
                                onChange(setup.copy(limits = setup.limits + (kind to (cap - 50).coerceAtLeast(50))))
                            }, onPlus = {
                                onChange(setup.copy(limits = setup.limits + (kind to (cap + 50).coerceAtMost(3000))))
                            })
                        }
                    }
                }
            }
            Feature.TRAIL -> Unit
        }
    }
}

/**
 * The basic setup as a sheet: visual, sentence, controls, "Turn on". Opened when a feature is
 * switched on from the Features page or Home's suggestion; dismissing it leaves the feature off.
 */
@Composable
fun FeatureSetupSheet(feature: Feature, initial: FeatureSetup, onTurnOn: (FeatureSetup) -> Unit, onDismiss: () -> Unit) {
    var setup by remember(feature) { mutableStateOf(initial) }
    TrackerSheet(title = feature.title, onDismiss = onDismiss) {
        FeatureVisual(feature, setup)
        Text(feature.setupSentence, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        Spacer(Modifier.height(12.dp))
        FeatureSetupControls(feature, setup) { setup = it }
        Spacer(Modifier.height(16.dp))
        GoldButton("Turn on", onClick = { onTurnOn(setup) }, modifier = Modifier.fillMaxWidth(), enabled = setup.canTurnOn(feature))
    }
}

private fun limitName(kind: DietaryRuleKind): String = when (kind) {
    DietaryRuleKind.DESSERT -> "Desserts"
    DietaryRuleKind.FRIED -> "Fried & fast food"
    DietaryRuleKind.SUGARY_DRINK -> "Sugary drinks"
}

@Composable
private fun SetupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = GoldLight)
}

/** A selectable pill — gold-rimmed when chosen. */
@Composable
fun SetupPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .then(if (selected) Modifier.accentGlass(GoldLight, shape) else Modifier.glassSoft(shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) GoldLight else Cream, maxLines = 1)
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundStep(Icons.Default.Remove, "Less", onMinus)
        Text(
            value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Cream,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        RoundStep(Icons.Default.Add, "More", onPlus)
    }
}

@Composable
private fun RoundStep(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).glassSoft(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Cream, modifier = Modifier.size(18.dp))
    }
}
