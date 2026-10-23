@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.fitpal.app.ui.screen.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.local.dao.DailyMicros
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.domain.DayScore
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.BarTrendChart
import com.fitpal.app.ui.component.fastingClockLabel
import com.fitpal.app.ui.component.fastingCountdownLabel
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.swipeNavigation
import com.fitpal.app.ui.component.MacroBar
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.component.TrendChart
import com.fitpal.app.ui.theme.AccentActivity
import com.fitpal.app.ui.theme.AccentGarden
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.FastingColor
import com.fitpal.app.ui.theme.CarbColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.FatColor
import com.fitpal.app.ui.theme.FiberColor
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.LoggedBest
import com.fitpal.app.ui.theme.LoggedGood
import com.fitpal.app.ui.theme.LoggedLow
import com.fitpal.app.ui.theme.LoggedMid
import com.fitpal.app.ui.theme.LoggedWorst
import com.fitpal.app.ui.theme.ProteinColor
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.ScorePoor
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.scoreColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * The building blocks of the Analytics screen: card frames, charts, tiles, heatmaps and the
 * explainer dialogs. AnalyticsScreen decides what goes where.
 */

// ======================== PIECES ========================

/** A fading accent border used to flag a widget after the user jumps to it. */
@Composable
internal fun highlightBorder(active: Boolean): Modifier {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            alpha.snapTo(1f)
            alpha.animateTo(0f, animationSpec = tween(1200))
        }
    }
    return if (alpha.value > 0f) Modifier.border(2.dp, AccentTrends.copy(alpha = alpha.value), RoundedCornerShape(26.dp)) else Modifier
}

/**
 * The card's base surface. When [spotlit] (the user long-pressed it) it switches to the
 * warm gold palette from the home screen — a gold gradient fill and bright gold ring —
 * so the widgets you care about clearly stand out against the blue analytics backdrop.
 */
@Composable
internal fun Modifier.cardSurface(spotlit: Boolean, shape: RoundedCornerShape = RoundedCornerShape(26.dp)): Modifier =
    if (spotlit) this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Gold.copy(alpha = 0.30f), Gold.copy(alpha = 0.10f))))
        .border(1.5.dp, GoldLight.copy(alpha = 0.9f), shape)
    else this.glass(shape)

@Composable
internal fun ChartCard(
    title: String,
    subtitle: String,
    highlight: Boolean = false,
    spotlight: Set<String> = emptySet(),
    onToggleSpotlight: (String) -> Unit = {},
    /** Tap action (e.g. open an explainer popup); defaults to nothing. Long-press still spotlights. */
    onClick: () -> Unit = {},
    /** Optional control parked at the end of the header row (e.g. the Lifetime pill). */
    trailing: (@Composable () -> Unit)? = null,
    /** What the card shows and how it's worked out, behind an (i) next to the title. */
    info: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .cardSurface(title in spotlight)
            .then(highlightBorder(highlight))
            .combinedClickable(onClick = onClick, onLongClick = { onToggleSpotlight(title) })
            .padding(16.dp)
    ) {
        CardHeader(title, subtitle, info, trailing)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/**
 * A card's title row — title, (i), then any trailing control — and under it the card's takeaway:
 * one computed sentence about the data (P8), never a description of the chart.
 */
@Composable
internal fun CardHeader(
    title: String,
    subtitle: String,
    info: String?,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title, style = MaterialTheme.typography.titleMedium, color = Cream,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
        )
        if (info != null) com.fitpal.app.ui.component.InfoButton(title = title, text = info)
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
    if (subtitle.isNotEmpty()) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CreamMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The "Lifetime" switch on the weight / macro / micronutrient cards: off = the period you're
 * viewing, on = everything since your very first entry. It swallows the tap so it doesn't also
 * flip the card's view or trigger the card's own click.
 */
@Composable
internal fun LifetimePill(active: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) AccentTrends.copy(alpha = 0.22f) else com.fitpal.app.ui.theme.filmColor(0.07f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Default.AllInclusive,
            contentDescription = if (active) "Show the viewed period" else "Show all time",
            tint = if (active) AccentTrends else CreamMuted,
            modifier = Modifier.size(13.dp)
        )
        Text(
            "Lifetime",
            style = MaterialTheme.typography.labelSmall,
            color = if (active) AccentTrends else CreamMuted
        )
    }
}

/** A chart card whose content switches between perspectives when tapped. */
@Composable
internal fun SwitchableChartCard(
    title: String,
    views: List<String>,
    viewState: Map<String, Int> = emptyMap(),
    onSetView: (String, Int) -> Unit = { _, _ -> },
    highlight: Boolean = false,
    spotlight: Set<String> = emptySet(),
    onToggleSpotlight: (String) -> Unit = {},
    /** Optional control parked before the view switcher (e.g. the Lifetime pill). */
    trailing: (@Composable () -> Unit)? = null,
    /** The card's takeaway line and its explanation (see [CardHeader]). */
    subtitle: String = "",
    info: String? = null,
    content: @Composable (Int) -> Unit
) {
    val view = (viewState[title] ?: 0).coerceIn(0, views.size - 1)
    Column(
        modifier = Modifier.fillMaxWidth()
            .cardSurface(title in spotlight)
            .then(highlightBorder(highlight))
            .combinedClickable(
                onClick = { onSetView(title, (view + 1) % views.size) },
                onLongClick = { onToggleSpotlight(title) }
            )
            .padding(16.dp)
    ) {
        CardHeader(title, subtitle, info, trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                trailing?.invoke()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(views[view], style = MaterialTheme.typography.labelMedium, color = AccentTrends)
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Switch view", tint = AccentTrends, modifier = Modifier.size(16.dp))
                }
            }
        })
        Spacer(Modifier.height(10.dp))
        content(view)
    }
}

/** Per-day stacked macro composition (protein/fat/net-carbs/fiber by share of grams). */
@Composable
internal fun MacroCompositionChart(
    rows: List<DailyNutritionRow>,
    dates: List<LocalDate>,
    labels: List<String> = emptyList(),
    highlightIndex: Int? = null,
    onBarClick: ((Int) -> Unit)? = null
) {
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val byDate = rows.associateBy { it.date }
    val emptyC = com.fitpal.app.ui.theme.filmColor(0.06f)
    val labelArgb = CreamFaint.toArgb()
    val highlightArgb = Cream.toArgb()
    val tapModifier = if (onBarClick != null && dates.isNotEmpty()) {
        Modifier.pointerInput(dates.size) {
            detectTapGestures { offset ->
                val idx = (offset.x / (size.width.toFloat() / dates.size)).toInt().coerceIn(0, dates.size - 1)
                onBarClick(idx)
            }
        }
    } else Modifier
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).then(tapModifier)) {
        val chartH = size.height * 0.82f
        val slot = size.width / dates.size
        val barW = slot * 0.6f
        val radius = androidx.compose.ui.geometry.CornerRadius(barW / 2.5f, barW / 2.5f)
        dates.forEachIndexed { i, d ->
            val row = byDate[d.format(fmt)]
            val protein = row?.protein ?: 0f
            val fat = row?.fat ?: 0f
            val netCarbs = ((row?.carbs ?: 0f) - (row?.fiber ?: 0f)).coerceAtLeast(0f)
            val fiber = row?.fiber ?: 0f
            val total = protein + fat + netCarbs + fiber
            val x = i * slot + (slot - barW) / 2
            if (total <= 0f) {
                drawRoundRect(emptyC, Offset(x, chartH * 0.6f), Size(barW, chartH * 0.4f), cornerRadius = radius)
            } else {
                // Clip each day's column to a rounded rect so the whole stacked bar reads as one rounded bar.
                val path = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(x, 0f, x + barW, chartH, radius))
                }
                clipPath(path) {
                    var y = chartH
                    listOf(protein to ProteinColor, fat to FatColor, netCarbs to CarbColor, fiber to FiberColor).forEach { (g, c) ->
                        val segH = (g / total) * chartH
                        drawRect(c, Offset(x, y - segH), Size(barW, segH))
                        y -= segH
                    }
                }
            }
            if (i == highlightIndex) {
                drawCircle(Cream, radius = barW * 0.16f, center = Offset(x + barW / 2f, chartH + 8f))
            }
        }
        // Per-bar day labels when there's room.
        if (labels.size == dates.size && dates.size <= 16) {
            val paint = android.graphics.Paint().apply {
                color = labelArgb; textSize = 22f; isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            labels.forEachIndexed { i, lbl ->
                paint.color = if (i == highlightIndex) highlightArgb else labelArgb
                drawContext.canvas.nativeCanvas.drawText(lbl, i * slot + slot / 2f, size.height - 2f, paint)
            }
        }
    }
}

/** RDA reference + averaged daily intake for one micronutrient. */
internal data class MicroStat(val label: String, val avg: Float, val rda: Float, val unit: String)

/** Each goal vitamin / mineral's daily average over [loggedDays] vs its adult target. */
internal fun microStats(totals: DailyMicros, loggedDays: Int): List<MicroStat> {
    val d = loggedDays.coerceAtLeast(1).toFloat()
    return listOf(
        MicroStat("Vitamin A", totals.vitaminA / d, 700f, "mcg"),
        MicroStat("Vitamin C", totals.vitaminC / d, 75f, "mg"),
        MicroStat("Vitamin D", totals.vitaminD / d, 15f, "mcg"),
        MicroStat("Vitamin E", totals.vitaminE / d, 15f, "mg"),
        MicroStat("Vitamin B6", totals.vitaminB6 / d, 1.3f, "mg"),
        MicroStat("Vitamin B12", totals.vitaminB12 / d, 2.4f, "mcg"),
        MicroStat("Folate", totals.folate / d, 400f, "mcg"),
        MicroStat("Calcium", totals.calcium / d, 1000f, "mg"),
        MicroStat("Iron", totals.iron / d, 12f, "mg"),
        MicroStat("Magnesium", totals.magnesium / d, 400f, "mg"),
        MicroStat("Zinc", totals.zinc / d, 11f, "mg"),
        MicroStat("Potassium", totals.potassium / d, 3400f, "mg")
    )
}

/** The vitamins and minerals averaging under half their target, lowest first. */
internal fun lowMicros(totals: DailyMicros?, loggedDays: Int): List<String> =
    if (totals == null || loggedDays <= 0) emptyList()
    else microStats(totals, loggedDays).filter { it.avg < it.rda * 0.5f }.sortedBy { it.avg / it.rda }.map { it.label }

/**
 * Daily-average intake of each goal micronutrient versus its target, surfaced as the
 * three you're lowest on (to supplement) and the three you're best on. Sodium is a
 * limit, not a goal, so it's called out separately only when it runs high.
 */
@Composable
internal fun MicronutrientAnalysis(totals: DailyMicros?, loggedDays: Int) {
    if (totals == null || loggedDays <= 0) {
        NotEnough()
        return
    }
    val goals = microStats(totals, loggedDays)
    val d = loggedDays.toFloat()
    val ranked = goals.sortedBy { it.avg / it.rda }
    val deficient = ranked.take(3)
    val proficient = ranked.reversed().take(3)

    Text("Lowest — consider topping up", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
    Spacer(Modifier.height(8.dp))
    deficient.forEach { MicroRow(it) }

    Spacer(Modifier.height(14.dp))
    Text("Best covered", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
    Spacer(Modifier.height(8.dp))
    proficient.forEach { MicroRow(it) }

    val sodiumAvg = totals.sodium / d
    if (sodiumAvg > 2300f) {
        Spacer(Modifier.height(14.dp))
        Text(
            "Sodium averaging ${sodiumAvg.toInt()} mg/day — over the 2300 mg limit. Worth cutting back.",
            style = MaterialTheme.typography.bodySmall,
            color = ScorePoor
        )
    }
}

@Composable
internal fun MicroRow(stat: MicroStat) {
    val coverage = if (stat.rda > 0f) stat.avg / stat.rda else 0f
    val pct = (coverage * 100f).toInt()
    val color = when {
        coverage < 0.5f -> ScorePoor
        coverage < 0.8f -> Gold
        else -> ScoreFair
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stat.label, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.width(86.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(com.fitpal.app.ui.theme.filmColor(0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(coverage.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("$pct%", style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.width(46.dp), textAlign = TextAlign.End)
    }
}

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
    }
}

@Composable
internal fun TierRow(color: Color, label: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = Cream)
    }
}

@Composable
internal fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Cream)
    }
}

@Composable
internal fun StatTile(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    accent: Color,
    spotKey: String,
    spotlight: Set<String>,
    onToggleSpotlight: (String) -> Unit,
    change: PeriodChange? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .cardSurface(spotKey in spotlight)
            .combinedClickable(onClick = onClick, onLongClick = { onToggleSpotlight(spotKey) })
            .padding(14.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = CreamMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent, maxLines = 1)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = CreamFaint, maxLines = 1)
        change?.let {
            Spacer(Modifier.height(4.dp))
            PeriodChangeText(it)
        }
    }
}

/** How a number moved against the previous period, as one short line ("↑ 8% vs last week"). */
internal data class PeriodChange(val text: String, val color: Color)

/**
 * [current] vs [previous] as a percentage change, or null when there's nothing fair to compare.
 * [higherIsBetter] colours an improvement green; null (or a move the wrong way) stays quiet — a change
 * line informs, it doesn't scold.
 */
internal fun percentChange(current: Float?, previous: Float?, previousLabel: String, higherIsBetter: Boolean?): PeriodChange? {
    if (current == null || previous == null || previous <= 0f) return null
    val pct = ((current - previous) / previous * 100f).roundToInt()
    if (pct == 0) return PeriodChange("Same as $previousLabel", CreamFaint)
    val improved = higherIsBetter != null && (pct > 0) == higherIsBetter
    return PeriodChange(
        "${if (pct > 0) "↑" else "↓"} ${kotlin.math.abs(pct)}% vs $previousLabel",
        if (improved) ScoreFair else CreamFaint
    )
}

@Composable
internal fun PeriodChangeText(change: PeriodChange) {
    Text(change.text, style = MaterialTheme.typography.labelSmall, color = change.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Water that came from food: the water colour, lighter — still water, not a different feature. */
internal val WaterFromFood: Color get() = AccentTrends.copy(alpha = 0.55f)

/**
 * A compact tile merging the average balance score with a mini logged-days heatmap — sits in the
 * summary grid where "Avg score" used to. Each tiny square is a day, tappable to open its details.
 */
@Composable
internal fun LoggedScoreTile(
    modifier: Modifier,
    dates: List<LocalDate>,
    colorFor: (LocalDate) -> Color?,
    avgScore: Int?,
    spotlight: Set<String>,
    onToggleSpotlight: (String) -> Unit,
    selected: LocalDate? = null,
    onDayClick: (LocalDate) -> Unit
) {
    Column(
        modifier = modifier
            .cardSurface("tile_score" in spotlight)
            .combinedClickable(onClick = {}, onLongClick = { onToggleSpotlight("tile_score") })
            .padding(14.dp)
    ) {
        // Header row: title on the left, the average score parked in the free space on the right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Logged days", style = MaterialTheme.typography.labelMedium, color = CreamMuted, modifier = Modifier.weight(1f))
            Text(avgScore?.toString() ?: "—", style = MaterialTheme.typography.titleMedium, color = AccentTrends)
            Spacer(Modifier.width(3.dp))
            Text("avg", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
        }
        Spacer(Modifier.height(8.dp))
        // Fixed-height mini heatmap that auto-packs however many days into the same box, so the tile
        // stays the same height as the other summary tiles whether it's a 7-day week or a 30-day month.
        MiniHeatmap(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            dates = dates,
            colorFor = colorFor,
            selected = selected,
            onDayClick = onDayClick
        )
    }
}

/** The packed grid: columns, square cell size (px) and the centring offset within the [w]×[h] box. */
internal data class HeatGrid(val cols: Int, val cell: Float, val offsetX: Float, val offsetY: Float)

/** Finds the largest square cell (with [gap]) that packs [n] cells into the box, then centres it. */
internal fun heatGrid(w: Float, h: Float, n: Int, gap: Float): HeatGrid {
    if (n <= 0 || w <= 0f || h <= 0f) return HeatGrid(1, 0f, 0f, 0f)
    var bestCols = 1
    var bestCell = 0f
    for (cols in 1..n) {
        val rows = (n + cols - 1) / cols
        val cell = minOf((w - (cols - 1) * gap) / cols, (h - (rows - 1) * gap) / rows)
        if (cell > bestCell) { bestCell = cell; bestCols = cols }
    }
    val rows = (n + bestCols - 1) / bestCols
    val gridW = bestCols * bestCell + (bestCols - 1) * gap
    val gridH = rows * bestCell + (rows - 1) * gap
    return HeatGrid(bestCols, bestCell, ((w - gridW) / 2f).coerceAtLeast(0f), ((h - gridH) / 2f).coerceAtLeast(0f))
}

/** A Canvas mini-heatmap that fills a fixed box, auto-sizing + centring the day squares; tappable. */
@Composable
internal fun MiniHeatmap(
    modifier: Modifier,
    dates: List<LocalDate>,
    colorFor: (LocalDate) -> Color?,
    selected: LocalDate? = null,
    onDayClick: (LocalDate) -> Unit
) {
    val empty = com.fitpal.app.ui.theme.filmColor(0.06f)
    val gap = 4f
    // The tapped square gets a ring that springs open, matching the big heatmap's pop.
    val selectedIndex = dates.indexOfFirst { it == selected }.takeIf { it >= 0 }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null) {
            ring.snapTo(0f)
            ring.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        } else {
            ring.animateTo(0f, tween(200))
        }
    }
    Canvas(
        modifier = modifier.pointerInput(dates.size) {
            detectTapGestures { offset ->
                val g = heatGrid(size.width.toFloat(), size.height.toFloat(), dates.size, gap)
                if (g.cell <= 0f) return@detectTapGestures
                val col = ((offset.x - g.offsetX) / (g.cell + gap)).toInt()
                val row = ((offset.y - g.offsetY) / (g.cell + gap)).toInt()
                val idx = row * g.cols + col
                if (col in 0 until g.cols && idx in dates.indices) onDayClick(dates[idx])
            }
        }
    ) {
        val g = heatGrid(size.width, size.height, dates.size, gap)
        if (g.cell <= 0f) return@Canvas
        val r = androidx.compose.ui.geometry.CornerRadius(g.cell / 4f, g.cell / 4f)
        dates.forEachIndexed { i, d ->
            val x = g.offsetX + (i % g.cols) * (g.cell + gap)
            val y = g.offsetY + (i / g.cols) * (g.cell + gap)
            drawRoundRect(colorFor(d) ?: empty, topLeft = Offset(x, y), size = Size(g.cell, g.cell), cornerRadius = r)
        }
        val lift = ring.value
        if (selectedIndex != null && lift > 0.01f) {
            val x = g.offsetX + (selectedIndex % g.cols) * (g.cell + gap)
            val y = g.offsetY + (selectedIndex / g.cols) * (g.cell + gap)
            val grow = g.cell * 0.35f * lift
            val side = g.cell + grow
            drawRoundRect(
                color = Cream.copy(alpha = lift.coerceIn(0f, 1f)),
                topLeft = Offset(x - grow / 2f, y - grow / 2f),
                size = Size(side, side),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(side / 4f, side / 4f),
                style = Stroke(width = 2.5f)
            )
        }
    }
}

/** Calendar heatmap of the range, each day coloured by its balance-score tier (faint if not logged). */
@Composable
internal fun LoggedHeatmap(
    dates: List<LocalDate>,
    colorFor: (LocalDate) -> Color?,
    selected: LocalDate? = null,
    onDayClick: (LocalDate) -> Unit
) {
    if (dates.isEmpty()) return
    if (dates.size <= 7) {
        // Short range: one straight row, oldest → newest, newest on the right.
        val cell = 26.dp
        val scroll = rememberScrollState()
        LaunchedEffect(dates.size) { scroll.scrollTo(scroll.maxValue) }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            dates.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HeatCell(cell, colorFor(d), selected = d == selected) { onDayClick(d) }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (d == selected) Cream else CreamFaint
                    )
                }
            }
        }
    } else {
        // Longer range: a compact month-style grid, weekday-aligned (Mon first), small squares.
        val cell = 20.dp
        val startOffset = dates.first().dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(startOffset) { null } + dates
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = CreamFaint, textAlign = TextAlign.Center, modifier = Modifier.width(cell))
                }
            }
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { d ->
                        if (d == null) Box(Modifier.size(cell))
                        else HeatCell(cell, colorFor(d), selected = d == selected) { onDayClick(d) }
                    }
                }
            }
        }
    }
}

/**
 * One day square. When it's the day you just tapped it springs out and takes a bright ring —
 * so the details that pop up are visibly tied to the square you actually touched.
 */
@Composable
internal fun HeatCell(
    size: androidx.compose.ui.unit.Dp,
    color: Color?,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        } else {
            pop.animateTo(0f, tween(200))
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val lift = pop.value
    Box(
        Modifier
            .size(size)
            .scale(1f + 0.18f * lift)
            .clip(shape)
            .background(color ?: com.fitpal.app.ui.theme.filmColor(0.05f))
            .then(if (lift > 0.01f) Modifier.border(2.dp, Cream.copy(alpha = lift.coerceIn(0f, 1f)), shape) else Modifier)
            .clickable(onClick = onClick)
    )
}

/**
 * What a fasting-heatmap square means: the day's eating window and fast length, whether you kept or
 * broke the fast, and — if you broke it — when, and how much of the fast was still left. Keeps the
 * same "jump to this day" action as the score popup.
 */
@Composable
internal fun FastingDayDialog(
    day: LocalDate,
    schedule: com.fitpal.app.domain.model.FastingSchedule,
    mealMinutes: List<Int>?,
    graceMinute: Int?,
    /** A meal was logged with "log anyway" during the fast that day (no pass, no photo proof). */
    loggedDuringFast: Boolean,
    onJump: () -> Unit,
    onDismiss: () -> Unit
) {
    val breaks = mealMinutes?.filter { !schedule.isEatingAt(it) }.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.format(DateTimeFormatter.ofPattern("EEE, d MMM"))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Eating window ${fastingClockLabel(schedule.eatStartMin)} – ${fastingClockLabel(schedule.eatEndMin)}",
                    style = MaterialTheme.typography.bodyMedium, color = Cream
                )
                Text(
                    "${fastingCountdownLabel(schedule.fastingLengthMin())} fast · ${fastingCountdownLabel(schedule.eatingLengthMin())} eating window",
                    style = MaterialTheme.typography.bodySmall, color = CreamMuted
                )
                when {
                    graceMinute != null -> Text(
                        "You logged this late and marked it eaten at ${fastingClockLabel(graceMinute)}, inside your window — so it still counts as kept.",
                        style = MaterialTheme.typography.bodyMedium, color = AccentGarden
                    )
                    mealMinutes.isNullOrEmpty() -> Text(
                        "No food logged this day, so there's nothing to check.",
                        style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                    )
                    // Logged during the fast with "log anyway" — broken for good, even if that meal's
                    // time was moved into the window afterwards (only a pass or a photo can save it).
                    loggedDuringFast && breaks.isEmpty() -> Text(
                        "A meal was logged during your fast without using a pass, so this day counts as broken — " +
                            "even though its time was later moved into your window.",
                        style = MaterialTheme.typography.bodyMedium, color = ScorePoor
                    )
                    breaks.isEmpty() -> Text(
                        "You kept your fast — every meal was inside your eating window.",
                        style = MaterialTheme.typography.bodyMedium, color = AccentGarden
                    )
                    else -> {
                        val first = breaks.min()
                        val s = schedule.stateAt(first)
                        // Frame the slip against whichever window edge is nearer: just after the
                        // window closed reads as a small overrun ("1h after it closed"), while a
                        // pre-dawn meal reads as time before it reopens ("4h before it opened").
                        val day = com.fitpal.app.domain.model.FastingSchedule.DAY
                        val sinceClose = ((first - schedule.eatEndMin) % day + day) % day
                        val untilOpen = s.minutesLeftInPhase
                        Text(
                            "You broke your fast at ${fastingClockLabel(first)}.",
                            style = MaterialTheme.typography.bodyMedium, color = ScorePoor
                        )
                        Text(
                            text = if (sinceClose <= untilOpen)
                                "That was ${fastingCountdownLabel(sinceClose)} after your window closed at ${fastingClockLabel(schedule.eatEndMin)}."
                            else
                                "That was ${fastingCountdownLabel(untilOpen)} before your window opened at ${fastingClockLabel(s.nextChangeMin)}.",
                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onJump) { Text("Jump to this day") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** What shaped a single day's score: its macros/calories vs targets, opened by tapping a day. */
@Composable
internal fun DayScoreDialog(
    day: LocalDate,
    row: DailyNutritionRow?,
    score: Int?,
    calGoal: Int,
    proteinTarget: Float,
    fatTarget: Float,
    carbTarget: Float,
    fiberTarget: Float,
    onJump: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateLabel = day.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onJump) { Text("Jump to this day") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(dateLabel) },
        text = {
            if (row == null || (row.calories <= 0f && row.protein <= 0f)) {
                Text("Nothing logged this day.", color = CreamMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (score != null) {
                        val tier = DayScore.tierOf(score)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$score", style = MaterialTheme.typography.headlineMedium, color = scoreColor(tier))
                            Spacer(Modifier.width(8.dp))
                            Text("/ 100 · ${tier.label}", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                        }
                    }
                    Text("What shaped the score:", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                    DayFactorRow("Calories", row.calories, calGoal.toFloat(), "kcal", overIsBad = true)
                    DayFactorRow("Protein", row.protein, proteinTarget, "g", overIsBad = false)
                    DayFactorRow("Fat", row.fat, fatTarget, "g", overIsBad = true)
                    DayFactorRow("Carbs", row.carbs, carbTarget, "g", overIsBad = true)
                    DayFactorRow("Fiber", row.fiber, fiberTarget, "g", overIsBad = false)
                }
            }
        }
    )
}

@Composable
internal fun DayFactorRow(label: String, actual: Float, target: Float, unit: String, overIsBad: Boolean) {
    val over = target > 0f && actual > target
    val color = when {
        target <= 0f -> Cream
        overIsBad && over -> ScorePoor
        !overIsBad && actual >= target -> ScoreFair
        else -> Cream
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        Text(
            text = "${actual.toInt()}${if (target > 0f) " / ${target.toInt()}" else ""} $unit",
            style = MaterialTheme.typography.bodyMedium, color = color
        )
    }
}

@Composable
internal fun WaterChip(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
            Text(value, style = MaterialTheme.typography.bodySmall, color = Cream)
        }
    }
}

@Composable
internal fun NotEnough() {
    Text(
        "Not enough data yet",
        style = MaterialTheme.typography.bodySmall,
        color = CreamFaint,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

/**
 * "How is the maintenance estimate worked out?" — shows the same numbers the estimate is built from
 * (average intake, the measured weight trend, and the 7,700-kcal-per-kg conversion) so the headline
 * figure isn't a black box. The two source rows drill into the full day-by-day lists, and the
 * reconciliation block explains why this (weight-based) number can differ from the Calorie-balance
 * screen's formula-based burn — the "why didn't the deficit show on the scale?" question.
 */
@Composable
internal fun MaintenanceDialog(
    estimate: com.fitpal.app.domain.WeightTrend.MaintenanceEstimate,
    calGoal: Int,
    theoreticalBurnPerDay: Float,
    onShowIntake: () -> Unit,
    onShowWeight: () -> Unit,
    onDismiss: () -> Unit
) {
    val rate = estimate.ratePerWeekKg
    val steady = kotlin.math.abs(rate) < 0.05f
    val losing = rate < 0f
    val adjust = kotlin.math.abs(estimate.dailyEnergyBalanceKcal)
    val formulaBurn = Math.round(theoreticalBurnPerDay)
    // How much the Calorie-balance screen's burn estimate exceeds what the scale implies, per day —
    // and the phantom "deficit" that difference invents over all the eaten days.
    val gap = formulaBurn - estimate.maintenanceKcal
    val phantomKcal = gap * estimate.loggedDays
    val phantomKg = kotlin.math.abs(phantomKcal) / 7700f
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        title = { Text("How this is worked out") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "≈ ${"%,d".format(estimate.maintenanceKcal)} kcal/day",
                    style = MaterialTheme.typography.headlineSmall, color = GoldLight
                )
                Text(
                    "What your logged food and your weight change together imply you burn on an average " +
                        "day. It's an insight only — it never changes your calorie target. Tap a row to see " +
                        "every day behind it.",
                    style = MaterialTheme.typography.bodySmall, color = CreamMuted
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaintenanceRow(
                        "Average intake",
                        "${"%,d".format(estimate.avgIntakeKcal)} kcal",
                        "over ${estimate.loggedDays} eaten days",
                        onClick = onShowIntake
                    )
                    MaintenanceRow(
                        "Weight trend",
                        if (steady) "holding steady" else "${"%.1f".format(kotlin.math.abs(rate))} kg/week ${if (losing) "down" else "up"}",
                        "tap for every weigh-in",
                        onClick = onShowWeight
                    )
                    if (!steady) {
                        MaintenanceRow(
                            if (losing) "You're eating under" else "You're eating over",
                            "≈ ${"%,d".format(adjust)} kcal/day",
                            if (losing) "so real burn is higher than intake" else "so real burn is lower than intake"
                        )
                    }
                }
                Text(
                    buildString {
                        append("Maintenance ≈ ${"%,d".format(estimate.avgIntakeKcal)}")
                        if (!steady) append(if (losing) " + ${"%,d".format(adjust)}" else " − ${"%,d".format(adjust)}")
                        append(" ≈ ${"%,d".format(estimate.maintenanceKcal)} kcal/day")
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Cream
                )
                if (calGoal > 0) {
                    Text(
                        "For comparison, your current target is ${"%,d".format(calGoal)} kcal/day.",
                        style = MaterialTheme.typography.bodySmall, color = CreamMuted
                    )
                }

                // ---- Reconciliation: why the scale can disagree with the Calorie-balance deficit ----
                if (formulaBurn > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(com.fitpal.app.ui.theme.filmColor(0.08f)))
                    Text("Why a deficit can lag the scale", style = MaterialTheme.typography.titleSmall, color = Cream)
                    Text(
                        buildString {
                            append("The Calorie-balance screen weighs your food against a formula estimate of your ")
                            append("burn (~${"%,d".format(formulaBurn)} kcal/day: resting + steps + workouts). This number ")
                            append("uses your real weight instead, putting your true burn near ${"%,d".format(estimate.maintenanceKcal)}. ")
                            when {
                                gap >= 40 -> append(
                                    "The formula runs about ${"%,d".format(gap)} kcal/day HIGH for you — so across your " +
                                    "${estimate.loggedDays} eaten days it invents roughly ${"%,d".format(phantomKcal)} kcal of " +
                                    "\"deficit\" (~${"%.1f".format(phantomKg)} kg) that was never really there. That's why the " +
                                    "scale didn't drop what the balance screen implied. "
                                )
                                gap <= -40 -> append(
                                    "The formula runs about ${"%,d".format(-gap)} kcal/day LOW for you, so the scale may be " +
                                    "moving a little faster than the balance screen suggests. "
                                )
                                else -> append("The two line up closely for you. ")
                            }
                            append("Food logging usually undercounts a little, the formula is a population average, and " +
                                "short-term water — plus some muscle when you're barely training — blur week-to-week weight. " +
                                "Judged over weeks, this weight-based number is the one to trust.")
                        },
                        style = MaterialTheme.typography.bodySmall, color = CreamMuted
                    )
                }

                Text(
                    "1 kg of body-weight change is about 7,700 kcal, spread across the week. It's a rough " +
                        "estimate — it can read high while you're losing quickly, and settles as your weight steadies.",
                    style = MaterialTheme.typography.labelSmall, color = CreamFaint
                )
            }
        }
    )
}

@Composable
internal fun MaintenanceRow(label: String, value: String, note: String?, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.labelSmall, color = CreamFaint)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Cream, textAlign = TextAlign.End)
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CreamMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/** A "label … value" line for the detail popups' stat blocks. */
@Composable
internal fun StatLine(label: String, value: String, valueColor: Color = Cream) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, textAlign = TextAlign.End)
    }
}

/**
 * Drill-down from the maintenance popup's "Average intake" row: the full per-day intake behind the
 * average, plus min / max / median / total — so the average is auditable, not a bare number.
 * (Water-only days are excluded — same rule as the average itself.)
 */
@Composable
internal fun IntakeDetailDialog(
    days: List<DailyNutritionRow>,
    average: Int,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM ''yy")
    fun label(isoDate: String) = runCatching { LocalDate.parse(isoDate).format(fmt) }.getOrDefault(isoDate)
    val kcals = days.map { it.calories }.sorted()
    val total = kcals.sumOf { it.toDouble() }.toInt()
    val median = when {
        kcals.isEmpty() -> 0
        kcals.size % 2 == 1 -> kcals[kcals.size / 2].toInt()
        else -> ((kcals[kcals.size / 2 - 1] + kcals[kcals.size / 2]) / 2f).toInt()
    }
    val minDay = days.minByOrNull { it.calories }
    val maxDay = days.maxByOrNull { it.calories }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onBack) { Text("Back") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Average intake") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "${"%,d".format(average)} kcal/day",
                    style = MaterialTheme.typography.headlineSmall, color = GoldLight
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatLine("Eaten days", "${days.size}")
                    StatLine("Total logged", "${"%,d".format(total)} kcal")
                    StatLine("Average", "${"%,d".format(average)} kcal")
                    StatLine("Median", "${"%,d".format(median)} kcal")
                    minDay?.let { StatLine("Lowest", "${"%,d".format(it.calories.toInt())} kcal · ${label(it.date)}", ScoreFair) }
                    maxDay?.let { StatLine("Highest", "${"%,d".format(it.calories.toInt())} kcal · ${label(it.date)}", ScorePoor) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(com.fitpal.app.ui.theme.filmColor(0.08f)))
                Text("Every eaten day", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    days.sortedByDescending { it.date }.forEach { row ->
                        val color = when (row.date) {
                            maxDay?.date -> ScorePoor
                            minDay?.date -> ScoreFair
                            else -> Cream
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label(row.date), style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                            Text("${"%,d".format(row.calories.toInt())} kcal", style = MaterialTheme.typography.bodySmall, color = color)
                        }
                    }
                }
            }
        }
    )
}

/**
 * Drill-down from the maintenance popup's "Weight trend" row: every weigh-in plus start / current /
 * net / min / max, and a plain-language note on how the weekly rate is fit (a line through ALL
 * weigh-ins, so a single water-heavy morning can't swing it) — the reasoning the user asked to see.
 */
@Composable
internal fun WeightTrendDetailDialog(
    weights: List<WeightEntryEntity>,
    ratePerWeekKg: Float,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM ''yy")
    fun label(isoDate: String) = runCatching { LocalDate.parse(isoDate).format(fmt) }.getOrDefault(isoDate)
    val sorted = weights.sortedBy { it.date }
    val start = sorted.firstOrNull()
    val current = sorted.lastOrNull()
    val minW = sorted.minByOrNull { it.weightKg }
    val maxW = sorted.maxByOrNull { it.weightKg }
    val net = (current?.weightKg ?: 0f) - (start?.weightKg ?: 0f)
    val steady = kotlin.math.abs(ratePerWeekKg) < 0.05f
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onBack) { Text("Back") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Weight trend") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (steady) "Holding steady"
                    else "${"%.2f".format(kotlin.math.abs(ratePerWeekKg))} kg/week ${if (ratePerWeekKg < 0f) "down" else "up"}",
                    style = MaterialTheme.typography.headlineSmall, color = GoldLight
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatLine("Weigh-ins", "${sorted.size}")
                    start?.let { StatLine("First", "${"%.1f".format(it.weightKg)} kg · ${label(it.date)}") }
                    current?.let { StatLine("Latest", "${"%.1f".format(it.weightKg)} kg · ${label(it.date)}") }
                    StatLine(
                        "Net change",
                        "${if (net >= 0f) "+" else "−"}${"%.1f".format(kotlin.math.abs(net))} kg",
                        if (net > 0.1f) ScorePoor else if (net < -0.1f) ScoreFair else Cream
                    )
                    minW?.let { StatLine("Lowest", "${"%.1f".format(it.weightKg)} kg · ${label(it.date)}", ScoreFair) }
                    maxW?.let { StatLine("Highest", "${"%.1f".format(it.weightKg)} kg · ${label(it.date)}", ScorePoor) }
                }
                Text(
                    "The weekly rate is the slope of a line fit through ALL your weigh-ins, not just the " +
                        "first and last — so one water-heavy morning can't swing it. Net change is the raw " +
                        "first-to-latest difference for comparison; the two differ when your weight zig-zagged. " +
                        "Day-to-day water and glycogen can move the scale ±0.5–1 kg on their own, which is why " +
                        "the estimate leans on the long trend.",
                    style = MaterialTheme.typography.bodySmall, color = CreamMuted
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(com.fitpal.app.ui.theme.filmColor(0.08f)))
                Text("Every weigh-in", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    sorted.reversed().forEach { w ->
                        val color = when (w.date) {
                            maxW?.date -> ScorePoor
                            minW?.date -> ScoreFair
                            else -> Cream
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label(w.date), style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                            Text("${"%.1f".format(w.weightKg)} kg", style = MaterialTheme.typography.bodySmall, color = color)
                        }
                    }
                }
            }
        }
    )
}

/**
 * The weight card's change chip: how much weight moved over the period you're viewing (or since your
 * first weigh-in, in Lifetime) — an arrow and the kilos, green when it's heading the way your goal
 * wants, amber when it isn't, muted when it's steady.
 */
@Composable
internal fun WeightChangeChip(deltaKg: Float, goal: com.fitpal.app.domain.model.FitnessGoal) {
    val steady = kotlin.math.abs(deltaKg) < 0.05f
    val color = when (com.fitpal.app.domain.WeightTrend.changeIsOnTrack(deltaKg, goal)) {
        true -> ScoreFair
        false -> Gold
        null -> CreamMuted
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = when {
                steady -> Icons.AutoMirrored.Filled.TrendingFlat
                deltaKg < 0f -> Icons.AutoMirrored.Filled.TrendingDown
                else -> Icons.AutoMirrored.Filled.TrendingUp
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = if (steady) "0.0 kg" else (if (deltaKg > 0f) "+" else "−") + "%.1f kg".format(kotlin.math.abs(deltaKg)),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

internal fun enough(s: List<Float?>): Boolean = s.count { it != null } >= 2

/**
 * The 5-band best→worst colour used *only* by the logged-days widgets (heatmap card + summary tile):
 * blue (best) → green → yellow → orange → red (worst). Kept local so the Home ring and the other
 * score-tier colours keep their gold-based scheme.
 */
internal fun loggedDayColor(score: Int): Color = when {
    score >= 80 -> LoggedBest
    score >= 65 -> LoggedGood
    score >= 50 -> LoggedMid
    score >= 35 -> LoggedLow
    else -> LoggedWorst
}

internal fun avgLabel(series: List<Float?>, unit: String): String {
    val present = series.filterNotNull()
    if (present.isEmpty()) return ""
    val avg = present.sum() / present.size
    return "avg ${avg.toInt()}${if (unit.isEmpty()) "" else " $unit"}/day"
}
