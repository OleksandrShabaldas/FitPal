package com.fitpal.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted

/**
 * The user's three online model slots, in Settings order (blanks included so slot numbers line up
 * with the Settings labels). Provided once at the Compose root by `MainActivity`, so any badge can
 * name the slot that answered — "Fallback 2" — instead of spelling out a long model id.
 *
 * Empty by default: with no slots to match against, a badge just shows the model's own name, which
 * is never wrong, only longer.
 */
val LocalAiModelSlots = compositionLocalOf { emptyList<String>() }

/**
 * A small pill saying which AI produced the content above it: warm accent dot + the slot that
 * answered ("Main model" / "Fallback 2"), a muted dot + "On-device" when it ran on the phone.
 * Hidden entirely when the source is unknown.
 *
 * It names the *slot* rather than the model because online isn't one thing — [com.fitpal.app.ml.GeminiClient]
 * cascades to the next configured model when one runs out of free quota, and "Fallback 2" says that
 * in two words where the model id needs a line of its own. Tapping opens the exact model id, the
 * engine in plain words, and (when it fell back to the phone) why. Entries logged before the model
 * was recorded still show a plain "Online AI" / "On-device AI".
 *
 * Matches the dark/glass design language (Cream text, no Material Card) — see DESIGN_SYSTEM.md.
 */
@Composable
fun AiSourceBadge(source: AiSource?, modifier: Modifier = Modifier, reason: String? = null) {
    if (source == null) return
    val slots = LocalAiModelSlots.current
    val dotColor = if (source.isOnline) MaterialTheme.colorScheme.primary else CreamMuted
    // A reason is only worth showing when on-device was used because online couldn't be.
    val hasReason = source.isOffline && !reason.isNullOrBlank()
    // Nothing to expand when we know neither the model nor a reason (an older entry).
    val expandable = hasReason || source.model != null
    var expanded by remember { mutableStateOf(false) }

    val label = when {
        source.isOffline -> "On-device"
        else -> AiSource.slotOf(source.model, slots)?.let { AiSource.slotLabel(it) } ?: source.modelLabel
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
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
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CreamMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // A model id we've never seen could be long — cap it rather than push the row wide.
                modifier = Modifier.widthIn(max = 180.dp)
            )
            // A chevron, not a word: the pill should read as a label, not a button.
            if (expandable) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide AI details" else "Show which model answered",
                    tint = CreamMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (expandable && expanded) {
            Spacer(Modifier.height(4.dp))
            // The engine in plain words plus the raw model id — the id is exactly what the user
            // typed in Settings (or would type to change it).
            Text(
                text = source.engineLabel + (source.model?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = CreamMuted
            )
            if (hasReason) {
                Text(
                    text = "Online AI unavailable — $reason",
                    style = MaterialTheme.typography.labelSmall,
                    color = CreamMuted
                )
            }
        }
    }
}
