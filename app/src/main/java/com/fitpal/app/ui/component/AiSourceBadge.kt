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
import androidx.compose.ui.unit.dp
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted

/**
 * A small, subtle pill telling the user which engine produced the analysis on screen:
 * the warm accent dot + "Online AI" when Gemini answered, a muted dot + "On-device AI"
 * when it fell back to Gemma. Hidden entirely when the source is unknown.
 *
 * Matches the dark/glass design language (Cream text, no Material Card) — see DESIGN_SYSTEM.md.
 */
@Composable
fun AiSourceBadge(source: AiSource?, modifier: Modifier = Modifier, reason: String? = null) {
    if (source == null) return
    val label = if (source == AiSource.ONLINE) "Online AI" else "On-device AI"
    val dotColor = if (source == AiSource.ONLINE) MaterialTheme.colorScheme.primary else CreamMuted
    // A reason is only worth showing when on-device was used because online couldn't be.
    val hasReason = source == AiSource.OFFLINE && !reason.isNullOrBlank()
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .then(if (hasReason) Modifier.clickable { expanded = !expanded } else Modifier)
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
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CreamMuted
            )
            // A tap affordance — the reason stays collapsed until the user wants it.
            if (hasReason) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (expanded) "hide" else "why?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (hasReason && expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Online AI unavailable — $reason",
                style = MaterialTheme.typography.labelSmall,
                color = CreamMuted
            )
        }
    }
}
