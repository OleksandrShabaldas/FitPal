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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted

/**
 * A small, subtle pill naming the AI that produced the generation on screen: the warm accent dot +
 * the model ("Gemini 3 Flash Preview") when the online engine answered, a muted dot + the on-device
 * model ("Gemma 3n E4B") when it fell back. Hidden entirely when the source is unknown.
 *
 * The model matters because "online" isn't one thing — the app cascades through the models set in
 * Settings when one runs out of free quota, so the same photo can be read by a different model
 * tomorrow. Tapping expands to the engine, the exact model id, and (when on-device was forced) why.
 * Older entries saved before the model was recorded fall back to plain "Online AI" / "On-device AI".
 *
 * Matches the dark/glass design language (Cream text, no Material Card) — see DESIGN_SYSTEM.md.
 */
@Composable
fun AiSourceBadge(source: AiSource?, modifier: Modifier = Modifier, reason: String? = null) {
    if (source == null) return
    val dotColor = if (source.isOnline) MaterialTheme.colorScheme.primary else CreamMuted
    // A reason is only worth showing when on-device was used because online couldn't be.
    val hasReason = source.isOffline && !reason.isNullOrBlank()
    // Nothing to expand when we know neither the model nor a reason (an old entry).
    val expandable = hasReason || source.model != null
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier)
                .background(Cream.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = source.modelLabel,
                style = MaterialTheme.typography.labelSmall,
                color = CreamMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // A model id we've never seen could be long — cap it rather than push the row wide.
                modifier = Modifier.widthIn(max = 220.dp)
            )
            // A tap affordance — the details stay collapsed until the user wants them.
            if (expandable) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (expanded) "hide" else "details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (expandable && expanded) {
            Spacer(Modifier.height(4.dp))
            // The engine in plain words, plus the raw model id — the pill shows a tidied-up name,
            // and the exact id is what the user typed in Settings (or would type to change it).
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
