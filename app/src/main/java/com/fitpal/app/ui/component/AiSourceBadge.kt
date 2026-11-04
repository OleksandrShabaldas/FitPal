package com.fitpal.app.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.LookState

/**
 * The user's three online model slots, in Settings order (blanks included so slot numbers line up
 * with the Settings labels). Provided once at the Compose root by `FitPalActivity`, so any badge can
 * name the slot that answered — "Fallback 2" — instead of spelling out a long model id.
 *
 * Empty by default: with no slots to match against, a badge just shows the model's own name, which
 * is never wrong, only longer.
 */
val LocalAiModelSlots = compositionLocalOf { emptyList<String>() }

/**
 * A model chip's corners: a pill while it's one line (half its height), a rounded box when it opens
 * onto a second line for the reason.
 */
internal val AiChipShape = RoundedCornerShape(12.dp)

/**
 * A small pill saying which AI produced the content above it: warm accent dot + the slot that
 * answered ("Main model" / "Fallback 2"), a muted dot + "On-device" when it ran on the phone.
 * Hidden entirely when the source is unknown.
 *
 * It names the *slot* rather than the model because online isn't one thing — [com.fitpal.app.ml.GeminiClient]
 * cascades to the next configured model when one runs out of free quota, and "Fallback 2" says that
 * in two words where the model id needs a line of its own. Tapping opens the chip itself, in place
 * (the 4 Nov review, #4): "Fallback 2 · gemini-3.5-flash", and on a second line why it ran on the
 * phone when online couldn't. Entries logged before the model was recorded still show a plain
 * "Online AI" / "On-device AI" and don't open.
 *
 * Matches the dark/glass design language (Cream text, no Material Card) — see DESIGN_SYSTEM.md. The
 * meal photo's frosted chip ([FrostedModelBadge] in MealFlow.kt) opens the same way.
 */
@Composable
fun AiSourceBadge(source: AiSource?, modifier: Modifier = Modifier, reason: String? = null) {
    if (source == null) return
    val dotColor = if (source.isOnline) MaterialTheme.colorScheme.primary else CreamMuted
    var expanded by remember { mutableStateOf(false) }
    val expandable = aiSourceExpandable(source, reason)
    Row(
        modifier = modifier
            .clip(AiChipShape)
            .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier)
            .background(Cream.copy(alpha = 0.08f))
            .padding(start = 8.dp, end = if (expandable) 4.dp else 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(6.dp))
        AiChipText(
            source = source, reason = reason, expanded = expanded,
            color = CreamMuted, reasonColor = CreamFaint, modifier = Modifier.weight(1f, fill = false)
        )
        // A chevron, not a word: the pill should read as a label, not a button.
        if (expandable) AiChipChevron(expanded, CreamMuted)
    }
}

/**
 * The chip's words: the slot folded, the slot and the exact model id open, and under it — only when
 * it ran on the phone because online failed — the reason. The width animates as it opens (the chip
 * grows towards its free side), instantly with Reduce motion.
 */
@Composable
internal fun AiChipText(
    source: AiSource,
    reason: String?,
    expanded: Boolean,
    color: Color,
    reasonColor: Color,
    modifier: Modifier = Modifier
) {
    val slots = LocalAiModelSlots.current
    val still = LookState.appearance.reduceMotion
    val why = aiSourceReason(source, reason)
    Column(modifier.then(if (still) Modifier else Modifier.animateContentSize())) {
        Text(
            text = if (expanded) aiSourceOpenLabel(source, slots) else aiSourceLabel(source, slots),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            // A model id we've never seen could be long — cut at the room there is, never wrap.
            overflow = TextOverflow.Ellipsis
        )
        if (expanded && why != null) {
            Text(text = why, style = MaterialTheme.typography.labelSmall, color = reasonColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** ⌄ folded, ⌃ open. */
@Composable
internal fun AiChipChevron(expanded: Boolean, tint: Color) {
    Icon(
        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = if (expanded) "Hide AI details" else "Show which model answered",
        tint = tint,
        modifier = Modifier.size(14.dp)
    )
}

/** Whether the chip has anything to open onto: the model it ran on, or why it ran on the phone. */
fun aiSourceExpandable(source: AiSource, reason: String?): Boolean =
    source.model != null || aiSourceReason(source, reason) != null

/** Who answered, in two words: the slot ("Main model", "Fallback 2"), or "On-device". */
fun aiSourceLabel(source: AiSource, slots: List<String>): String =
    if (source.isOffline) "On-device"
    else AiSource.slotOf(source.model, slots)?.let { AiSource.slotLabel(it) } ?: source.modelLabel

/**
 * The open chip's line: the slot and the exact model id ("Fallback 2 · gemini-3.5-flash", "On-device ·
 * Gemma 3n E4B"). A model that isn't in a slot any more is named by the engine and its id ("Online AI ·
 * gemini-2.5-flash"), since its folded label is already that id, prettied.
 */
fun aiSourceOpenLabel(source: AiSource, slots: List<String>): String {
    val id = source.model?.trim().orEmpty()
    if (id.isEmpty()) return source.engineLabel
    return when {
        source.isOffline -> "${aiSourceLabel(source, slots)} · $id"
        AiSource.slotOf(id, slots) != null -> "${aiSourceLabel(source, slots)} · $id"
        else -> "${source.engineLabel} · $id"
    }
}

/** Why it ran on the phone, when online couldn't: "Online AI unavailable — no connection". */
fun aiSourceReason(source: AiSource, reason: String?): String? =
    if (source.isOffline && !reason.isNullOrBlank()) "Online AI unavailable — $reason" else null
