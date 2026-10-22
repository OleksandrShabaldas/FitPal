package com.fitpal.app.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitpal.app.domain.model.EatenAt
import com.fitpal.app.domain.model.MealContext
import com.fitpal.app.domain.model.MealTypes
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InkBlack
import com.fitpal.app.ui.theme.InterFamily
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.OnGold
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft
import java.time.LocalDate
import java.time.LocalTime

/*
 * The meal flow's shared pieces (docs/redesign/08): the photo/describe analysis, and the logged-meal
 * screen, are one page whose phases fill in place — the photo, the details line and the log bar never
 * move. Built from these, so the screens can't drift apart.
 */

/** Where an analysis is, for the one place each screen branches. */
enum class AnalysisPhase { READY, WORKING, FAILED, RESULTS, SAVING }

/** The meal's photo: the same 200 dp in every phase, with the model's badge on its corner once done. */
@Composable
fun MealPhoto(model: Any?, source: AiSource?, reason: String? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(200.dp)) {
        AsyncImage(
            model = model, contentDescription = "Food photo",
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop
        )
        if (source != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .clip(RoundedCornerShape(50)).background(InkBlack.copy(alpha = 0.75f)).padding(horizontal = 4.dp, vertical = 2.dp)
            ) { AiSourceBadge(source, reason = reason) }
        }
    }
}

/** "{meal type} · {day} {time} · {tags}" + ✎ — tap to change any of it. */
@Composable
fun MealDetailsRow(
    mealType: String,
    logDate: LocalDate?,
    eatenAt: EatenAt,
    tags: Set<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parts = buildList {
        add(MealTypes.label(mealType))
        // No time chosen = it's logged as now.
        val time = eatenAt.time?.let { fastingClockLabel(it.hour * 60 + it.minute) } ?: "now"
        val day = logDate?.let { logDateLabel(it) }
        listOfNotNull(day, time).joinToString(" ").takeIf { it.isNotBlank() }?.let(::add)
        if (tags.isNotEmpty()) add(MealContext.format(tags) ?: "")
    }.filter { it.isNotBlank() }
    Row(
        modifier.fillMaxWidth().glassSoft(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            parts.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = Cream,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.Edit, contentDescription = "Change meal details", tint = CreamMuted, modifier = Modifier.size(16.dp))
    }
}

/**
 * The meal's details in full — which meal, which day, when, where & who with. Inline while the AI
 * works (so the wait isn't dead time), and in [MealDetailsSheet] behind the details row otherwise.
 * [onPickDay] null = no day choice (a logged meal moves by copying).
 */
@Composable
fun MealDetailsPanel(
    mealType: String,
    onMealType: (String) -> Unit,
    logDate: LocalDate?,
    onPickDay: (() -> Unit)?,
    eatenAt: EatenAt,
    onPickTime: (LocalTime) -> Unit,
    tags: Set<String>,
    onToggleTag: (String) -> Unit,
    onUsePhotoSuggestion: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelLabel("Which meal")
        MealTypeSelector(selected = mealType, onSelected = onMealType)
        if (logDate != null && onPickDay != null) {
            Spacer(Modifier.height(4.dp))
            PanelLabel("Which day")
            SetupPill(logDateLabel(logDate), selected = false, onClick = onPickDay)
        }
        Spacer(Modifier.height(4.dp))
        PanelLabel("When you ate")
        EatenAtChip(eatenAt = eatenAt, onPick = onPickTime, prominent = true, onUsePhotoSuggestion = onUsePhotoSuggestion)
        Spacer(Modifier.height(4.dp))
        PanelLabel("Where & who with (optional)")
        MealContextSelector(selected = tags, onToggle = onToggleTag)
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = CreamMuted)
}

/** [MealDetailsPanel] in a sheet, opened from the details row. */
@Composable
fun MealDetailsSheet(
    mealType: String,
    onMealType: (String) -> Unit,
    logDate: LocalDate?,
    onPickDay: (() -> Unit)?,
    eatenAt: EatenAt,
    onPickTime: (LocalTime) -> Unit,
    tags: Set<String>,
    onToggleTag: (String) -> Unit,
    onDismiss: () -> Unit,
    onUsePhotoSuggestion: (() -> Unit)? = null
) {
    TrackerSheet(title = "Meal details", onDismiss = onDismiss) {
        MealDetailsPanel(mealType, onMealType, logDate, onPickDay, eatenAt, onPickTime, tags, onToggleTag, onUsePhotoSuggestion)
        Spacer(Modifier.height(16.dp))
        GoldButton("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * A one-line "✎ Add a note for the AI" that opens into a text field — styled like Settings' search
 * pill, gold cursor. [quote] draws the text as a quote (Describe's description).
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

/** Level 1 of a meal: "{name or meal type} · {total} kcal" — tap the name to rename the meal. */
@Composable
fun MealHeader(title: String, kcal: Int, onRename: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).then(if (onRename != null) Modifier.clickable(onClick = onRename) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (onRename != null) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.Edit, contentDescription = "Name this meal", tint = CreamMuted, modifier = Modifier.size(15.dp))
            }
        }
        Text("%,d kcal".format(kcal), style = MaterialTheme.typography.titleMedium, color = GoldLight)
    }
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
 */
@Composable
fun LogBar(
    totalKcal: Int?,
    status: String?,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    menu: List<Pair<String, () -> Unit>> = emptyList(),
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shown by animateIntAsState(totalKcal ?: 0, animationSpec = tween(400), label = "logTotal")
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .glass(RoundedCornerShape(50)).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            when {
                status != null -> Text(status, style = MaterialTheme.typography.labelLarge, color = CreamMuted)
                totalKcal != null -> Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%,d".format(shown),
                        style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, fontFeatureSettings = "tnum"),
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
        GoldPill(actionLabel, onAction, enabled = actionEnabled)
    }
}
