package com.fitpal.app.ui.component

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitpal.app.domain.model.EatenAt
import com.fitpal.app.domain.model.MealContext
import com.fitpal.app.domain.model.MealTypes
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ui.component.kit.aiGlow
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.NumberFamily
import com.fitpal.app.ui.theme.OnGold
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/*
 * The meal page's shared pieces (docs/redesign/16, built on 08): Analysis, Describe and a logged meal
 * are one page — the meal's name as the title, the photo with the model on it, the time as a line of
 * text, the nutrition card, a card per dish, the insights, and (until it's logged) the meal pills and
 * the Log bar at the bottom. Built from these, so the screens can't drift apart.
 */

/** Where an analysis is, for the one place each screen branches. */
enum class AnalysisPhase { READY, WORKING, FAILED, RESULTS, SAVING }

/**
 * The meal's photo: 200 dp, cropped to fill. The ↖↘ icon in its top-left corner opens the whole photo
 * ([PhotoViewer]); the photo itself does nothing on a tap (the 4 Nov review, #5). While the AI works
 * ([working]) a gold light sweeps down it. Once there's a [source], the model sits on its corner as
 * frosted glass; tap it and it opens in place onto the exact model.
 */
@Composable
fun MealPhoto(model: Any?, source: AiSource?, reason: String? = null, working: Boolean = false, modifier: Modifier = Modifier) {
    var viewing by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).onSizeChanged { size = it }) {
        AsyncImage(
            model = model, contentDescription = "Food photo",
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentScale = ContentScale.Crop
        )
        if (working) ScanSweep(Modifier.fillMaxWidth().height(200.dp))
        PhotoOpenButton(onClick = { viewing = true }, modifier = Modifier.align(Alignment.TopStart))
        if (source != null) {
            FrostedModelBadge(model, size, source, reason, Modifier.align(Alignment.BottomEnd).padding(10.dp))
        }
    }
    if (viewing) PhotoViewer(model) { viewing = false }
}

/** A slow gold light sweeping down the photo while the AI looks at it; a still gold line with Reduce motion. */
@Composable
internal fun ScanSweep(modifier: Modifier) {
    if (LookState.appearance.reduceMotion) {
        Box(modifier) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(GoldLight.copy(alpha = 0.8f)))
        }
        return
    }
    val t by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing)), label = "sweepY"
    )
    val light = GoldLight
    Canvas(modifier) {
        drawRect(Color.Black.copy(alpha = 0.14f))
        val band = size.height * 0.45f
        val top = -band + (size.height + band) * t
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.78f to light.copy(alpha = 0.20f),
                0.98f to Color(0xFFFFECBE).copy(alpha = 0.85f), 1f to Color.Transparent,
                startY = top, endY = top + band
            ),
            topLeft = Offset(0f, top), size = Size(size.width, band)
        )
    }
}

/**
 * The model that answered, on the photo's corner: the photo behind it blurred (frosted glass, no black
 * pill — real blur on Android 12+, a light smoky film before that). Tap → the chip itself opens, towards
 * the left (the 4 Nov review, #4): "Fallback 2 · gemini-3.5-flash", and on a second line why it ran on
 * the phone when online couldn't. No popup — that one was placed against the whole photo and landed
 * on the title bar.
 */
@Composable
private fun FrostedModelBadge(photo: Any?, photoSize: IntSize, source: AiSource, reason: String?, modifier: Modifier) {
    var pos by remember { mutableStateOf(IntOffset.Zero) }
    var open by remember { mutableStateOf(false) }
    val expandable = aiSourceExpandable(source, reason)
    val shape = AiChipShape
    val blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && photoSize != IntSize.Zero
    Box(
        modifier.onPlaced { pos = it.positionInParent().round() }.clip(shape)
            .then(if (expandable) Modifier.clickable { open = !open } else Modifier)
    ) {
        if (blur) {
            // The photo again, laid out at full size and lined up with the real one, then blurred.
            AsyncImage(
                model = photo, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
                    .layout { measurable, constraints ->
                        val p = measurable.measure(Constraints.fixed(photoSize.width, photoSize.height))
                        layout(constraints.maxWidth, constraints.maxHeight) { p.place(-pos.x, -pos.y) }
                    }
                    .blur(14.dp)
            )
        }
        Box(Modifier.matchParentSize().background(Color(0xFF140E0A).copy(alpha = if (blur) 0.30f else 0.45f)))
        Row(
            Modifier.border(1.dp, Color.White.copy(alpha = 0.24f), shape)
                .padding(start = 9.dp, end = if (expandable) 7.dp else 9.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (source.isOnline) GoldLight else Color.White.copy(alpha = 0.7f)))
            Spacer(Modifier.width(6.dp))
            AiChipText(
                source = source, reason = reason, expanded = open,
                color = Color.White, reasonColor = Color.White.copy(alpha = 0.78f), modifier = Modifier.weight(1f, fill = false)
            )
            if (expandable) AiChipChevron(open, Color.White.copy(alpha = 0.8f))
        }
    }
}

/**
 * When it was eaten, as a line of plain text under the photo: "Today 9:33 PM · from photo · Home",
 * pencil at the end. Tap to change the day, time or where & who with. An older photo's time is
 * offered under it ("Photo taken … · Use this time") when it wasn't applied on its own.
 */
@Composable
fun MealTimeLine(
    logDate: LocalDate?,
    eatenAt: EatenAt,
    tags: Set<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onUsePhotoSuggestion: (() -> Unit)? = null
) {
    val parts = buildList {
        // No time chosen = it's logged as now: "Today, now"; a chosen one reads "Today 18:31".
        val day = logDate?.let { logDateLabel(it) }
        val time = eatenAt.time?.let { clockLabel(it) }
        add(if (time != null) listOfNotNull(day, time).joinToString(" ") else listOfNotNull(day, "now").joinToString(", "))
        if (eatenAt.isPhotoTime) add("from photo")
        MealContext.format(tags)?.takeIf { it.isNotBlank() }?.let(::add)
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 12 sp in every look (the 4 Nov review, #9) — a quiet line under the photo.
            Icon(Icons.Default.Schedule, contentDescription = null, tint = CreamMuted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                parts.joinToString(" · "), style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = CreamMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Edit, contentDescription = "Change when and where", tint = CreamFaint, modifier = Modifier.size(12.dp))
        }
        val suggestion = eatenAt.photoSuggestion
        if (suggestion != null && onUsePhotoSuggestion != null && !eatenAt.isPhotoTime) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onUsePhotoSuggestion).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val day = when (suggestion.toLocalDate()) {
                    LocalDate.now() -> "today"
                    LocalDate.now().minusDays(1) -> "yesterday"
                    else -> suggestion.format(DateTimeFormatter.ofPattern("EEE d MMM")) + ","
                }
                Text("Photo taken $day ${clockLabel(suggestion.toLocalTime())} · ", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
                Text("Use this time", style = MaterialTheme.typography.labelSmall, color = GoldLight)
            }
        }
    }
}

/**
 * When and where, laid out flat (no card): one "When" row — the day and the time side by side — and
 * the where & who with tags. Open on the page while the AI works, and in [MealDetailsSheet] behind the
 * time line otherwise. The meal type isn't here: it has its own pills ([MealTypePills]).
 * [onPickDay] null = no day choice (a logged meal moves by copying).
 */
@Composable
fun MealDetailsPanel(
    logDate: LocalDate?,
    onPickDay: (() -> Unit)?,
    eatenAt: EatenAt,
    onPickTime: (LocalTime) -> Unit,
    tags: Set<String>,
    onToggleTag: (String) -> Unit,
    onUsePhotoSuggestion: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelLabel("When")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            if (logDate != null && onPickDay != null) {
                Row(
                    Modifier.glassSoft(RoundedCornerShape(50)).clickable(onClick = onPickDay).padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(logDateLabel(logDate), style = MaterialTheme.typography.titleSmall, color = Cream)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Pick the day", tint = CreamMuted, modifier = Modifier.size(20.dp))
                }
            }
            EatenAtChip(eatenAt = eatenAt, onPick = onPickTime, prominent = true, onUsePhotoSuggestion = onUsePhotoSuggestion)
        }
        Spacer(Modifier.height(2.dp))
        PanelLabel("Where & who with (optional)")
        MealContextSelector(selected = tags, onToggle = onToggleTag, feelingsLabel = { PanelLabel("How you felt (optional)") })
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = CreamMuted)
}

/** [MealDetailsPanel] in a sheet, opened from the time line. */
@Composable
fun MealDetailsSheet(
    logDate: LocalDate?,
    onPickDay: (() -> Unit)?,
    eatenAt: EatenAt,
    onPickTime: (LocalTime) -> Unit,
    tags: Set<String>,
    onToggleTag: (String) -> Unit,
    onDismiss: () -> Unit,
    onUsePhotoSuggestion: (() -> Unit)? = null
) {
    TrackerSheet(title = "When and where", onDismiss = onDismiss) {
        MealDetailsPanel(logDate, onPickDay, eatenAt, onPickTime, tags, onToggleTag, onUsePhotoSuggestion)
        Spacer(Modifier.height(8.dp))
        GoldButton("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

/** Breakfast · Lunch · Dinner · Snack as four pills, at the bottom of the meal page until it's logged and after. */
@Composable
fun MealTypePills(selected: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealTypes.ALL.forEach { type ->
            val on = type == selected
            val shape = RoundedCornerShape(50)
            Box(
                Modifier.weight(1f).height(40.dp).clip(shape)
                    .then(
                        if (on) Modifier.background(GoldLight.copy(alpha = 0.16f), shape).border(BorderStroke(1.dp, GoldLight.copy(alpha = 0.6f)), shape)
                        else Modifier.glassSoft(shape)
                    )
                    .clickable { onSelected(type) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    MealTypes.label(type), style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) GoldLight else Cream, maxLines = 1
                )
            }
        }
    }
}

/**
 * A meal page's list with its bottom controls floating over it (the 17 Nov review, #10, option B). The list
 * runs to the bottom of the screen and scrolls behind the meal pills (and the Log bar); there's no strip
 * behind them. Instead the list itself fades out where it meets them (a mask, not a painted band, so the
 * look's backdrop or the user's photo shows through), and its bottom padding follows the controls' height
 * so the last card can scroll up above them.
 *
 * [content] gets the modifier that draws the fade (put it on the list) and the bottom padding to give it.
 */
@Composable
fun FloatingBottomBox(
    modifier: Modifier = Modifier,
    content: @Composable (fade: Modifier, bottomPadding: androidx.compose.ui.unit.Dp) -> Unit,
    bottom: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barPx by remember { mutableStateOf(0) }
    val fadePx = with(density) { FLOAT_FADE.toPx() }
    val fade = Modifier
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (barPx > 0) {
                // Fully there above the controls, faint behind them (still visibly scrolling past).
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black, 1f to Color.Black.copy(alpha = 0.14f),
                        startY = size.height - barPx - fadePx, endY = size.height - barPx * 0.35f
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                )
            }
        }
    Box(modifier) {
        content(fade, with(density) { barPx.toDp() } + 16.dp)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { barPx = it.height },
            content = bottom
        )
    }
}

/** How far above the floating controls the list starts to fade. */
private val FLOAT_FADE = 28.dp

/**
 * The note for the AI before the photo is analysed: always open (about four lines, growing as you
 * type), with [phrases] under it that add themselves to the note in one tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    phrases: List<String> = emptyList()
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Note for the AI", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
            }
            Spacer(Modifier.height(6.dp))
            Box {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = CreamFaint)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Cream),
                    cursorBrush = SolidColor(GoldLight),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                )
            }
        }
        if (phrases.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                phrases.forEach { phrase ->
                    SetupPill("+ $phrase", selected = false) { onValueChange(withPhrase(value, phrase)) }
                }
            }
        }
    }
}

/** "large bowl" + "Ate half" → "large bowl, ate half"; on an empty note the phrase as it is. */
internal fun withPhrase(note: String, phrase: String): String {
    val base = note.trimEnd().removeSuffix(",").trimEnd()
    return if (base.isEmpty()) phrase else "$base, ${phrase.replaceFirstChar { it.lowercase() }}"
}

/** The note's quick phrases (#1 of the 2026-11-03 review). */
val NOTE_PHRASES = listOf("Ate half", "Large portion", "Fried in oil", "Sauce on the side", "Homemade")

/**
 * A one-line "✎ Add a note for the AI" that opens into a text field — Describe's description box
 * ([startOpen]). [quote] draws the text as a quote.
 */
@Composable
fun NoteLine(
    value: String,
    onValueChange: (String) -> Unit,
    prompt: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    startOpen: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    var open by remember { mutableStateOf(startOpen || value.isNotBlank()) }
    if (!open) {
        Row(
            modifier.fillMaxWidth().glass(RoundedCornerShape(50)).clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.EditNote, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(prompt, style = MaterialTheme.typography.labelLarge, color = Cream)
        }
        return
    }
    Box(modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = CreamFaint)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Cream),
            cursorBrush = SolidColor(GoldLight),
            modifier = Modifier.fillMaxWidth().then(if (minHeight > 0.dp) Modifier.height(minHeight) else Modifier)
        )
    }
}

/**
 * What the AI is doing, in one line under the photo (no card): a pulsing ✦, the step in words, and who
 * is answering with the seconds so far ("Online AI · 4 s").
 */
@Composable
fun AnalysingStatus(message: String, source: AiSource?, modifier: Modifier = Modifier) {
    // The pipeline says "Waiting for the AI to reply… (4s)": the words on the left, the seconds on the right.
    val seconds = Regex("""\s*\((\d+)s\)\s*$""").find(message)
    val words = (if (seconds != null) message.substring(0, seconds.range.first) else message).ifBlank { "Analysing…" }
    val who = listOfNotNull(
        source?.let { aiSourceLabel(it, LocalAiModelSlots.current) },
        seconds?.groupValues?.get(1)?.let { "$it s" }
    ).joinToString(" · ")
    val pulse = if (LookState.appearance.reduceMotion) 1f else rememberInfiniteTransition(label = "spark").animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "sparkAlpha"
    ).value
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✦", style = MaterialTheme.typography.titleSmall, color = GoldLight, modifier = Modifier.alpha(pulse))
        Spacer(Modifier.width(8.dp))
        Text(words, style = MaterialTheme.typography.bodyMedium, color = Cream, maxLines = 2, modifier = Modifier.weight(1f))
        if (who.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(who, style = MaterialTheme.typography.labelSmall, color = CreamFaint, maxLines = 1)
        }
    }
}

/** A dish card's shape while the AI works: two shimmering lines (still with reduce motion). */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val still = LookState.appearance.reduceMotion
    val shimmer = if (still) 0.6f else rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "shimmer"
    ).value
    Column(modifier.fillMaxWidth().glassSoft().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth(0.55f).height(16.dp).alpha(shimmer).clip(RoundedCornerShape(50)).background(filmColor(0.16f)))
        Box(Modifier.fillMaxWidth(0.8f).height(10.dp).alpha(shimmer).clip(RoundedCornerShape(50)).background(filmColor(0.10f)))
    }
}

/**
 * The meal's numbers: weight ("370 g", "170 g + 200 ml") and kcal, then the macro bar with each
 * macro's name and grams. Nothing else — when, which day and which meal stay out of it.
 */
@Composable
fun NutritionCard(weight: String, kcal: Int, protein: Float, fat: Float, carbs: Float, fiber: Float, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(weight, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.weight(1f))
            Text("%,d kcal".format(kcal), style = MaterialTheme.typography.titleLarge, color = GoldLight)
        }
        Spacer(Modifier.height(12.dp))
        MacroBar(protein = protein, fat = fat, carbs = carbs, fiber = fiber)
    }
}

/** "Meatballs with rice", "Fish and chips & side salad", "Soup, bread & tea" — a meal titled by its dishes. */
fun dishesTitle(names: List<String>): String = when (names.size) {
    0 -> "Meal"
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " & " + names.last().replaceFirstChar { it.lowercase() }
}

/** A problem shown in the page, not over it: title, the reason, and up to three actions. */
@Composable
fun InlineMessageCard(
    title: String,
    reason: String?,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>? = null,
    tertiary: Pair<String, () -> Unit>? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Cream)
        reason?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            primary?.let { (label, action) -> GoldPill(label, action) }
            secondary?.let { (label, action) -> SetupPill(label, selected = false, onClick = action) }
            tertiary?.let { (label, action) ->
                Text(label, style = MaterialTheme.typography.labelLarge, color = CreamMuted,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = action).padding(horizontal = 10.dp, vertical = 8.dp))
            }
        }
    }
}

/** The small gold action pill. */
@Composable
fun GoldPill(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = OnGold, maxLines = 1,
        modifier = modifier.alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(50)).background(GoldLight)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

/**
 * The sticky bar at the bottom of the meal flow: the total (or a status), a ⋯ menu, and the main
 * action — "Analyse" before, "Log meal" after (disabled while working), "Saving…" while saving.
 * [wideAction] makes the action the whole bar (Analyse, with nothing beside it); [onCancel] puts a
 * Cancel where the total goes (while the AI works).
 */
@Composable
fun LogBar(
    totalKcal: Int?,
    status: String?,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    menu: List<Pair<String, () -> Unit>> = emptyList(),
    modifier: Modifier = Modifier,
    /** The action starts the AI ("Analyse", "Analyse again"): it gets the AI glow while enabled. */
    aiAction: Boolean = actionLabel.startsWith("Analyse") || actionLabel.startsWith("✦"),
    wideAction: Boolean = false,
    onCancel: (() -> Unit)? = null
) {
    if (wideAction) {
        Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OnGold, maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .then(if (aiAction) Modifier.aiGlow(enabled = actionEnabled) else Modifier)
                    .alpha(if (actionEnabled) 1f else 0.4f).clip(RoundedCornerShape(50)).background(GoldLight)
                    .then(if (actionEnabled) Modifier.clickable(onClick = onAction) else Modifier)
                    .padding(vertical = 14.dp)
            )
        }
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    val shown by animateIntAsState(totalKcal ?: 0, animationSpec = tween(400), label = "logTotal")
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .glass(RoundedCornerShape(50)).padding(start = if (onCancel != null) 6.dp else 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            when {
                onCancel != null -> Text(
                    "Cancel", style = MaterialTheme.typography.labelLarge, color = Cream,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onCancel).padding(horizontal = 12.dp, vertical = 10.dp)
                )
                status != null -> Text(status, style = MaterialTheme.typography.labelLarge, color = CreamMuted)
                totalKcal != null -> Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%,d".format(shown),
                        style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, fontFeatureSettings = "tnum"),
                        color = Cream
                    )
                    Text(" kcal", style = MaterialTheme.typography.labelMedium, color = CreamMuted, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
        if (menu.isNotEmpty()) {
            Box {
                Icon(
                    Icons.Default.MoreHoriz, contentDescription = "More", tint = Cream,
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable { menuOpen = true }.padding(8.dp)
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menu.forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; action() })
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
        }
        GoldPill(actionLabel, onAction, enabled = actionEnabled, modifier = if (aiAction) Modifier.aiGlow(enabled = actionEnabled) else Modifier)
    }
}
