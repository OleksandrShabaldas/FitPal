package com.fitpal.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.MealContext
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight

/**
 * Optional one-tap "what situation was this meal" tags. Multi-select — a family dinner at home is
 * both "Home" and "Family meal" — and tapping a selected chip clears it. Feeds the AI review so it
 * can reason about *why* a day looked unusual. The tags themselves live in [MealContext].
 *
 * Custom compact pills (not Material [androidx.compose.material3.FilterChip], which is chunky) so a
 * row of seven fits tightly and matches the app's glass look.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealContextSelector(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MealContext.ALL.forEach { tag ->
            ContextChip(tag = tag, selected = tag in selected, onClick = { onToggle(tag) })
        }
    }
}

@Composable
private fun ContextChip(tag: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = if (selected) {
        Modifier.background(GoldLight.copy(alpha = 0.16f), shape)
            .border(BorderStroke(1.dp, GoldLight.copy(alpha = 0.55f)), shape)
    } else {
        Modifier.border(BorderStroke(1.dp, CreamMuted.copy(alpha = 0.35f)), shape)
    }
    Text(
        text = tag,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) GoldLight else CreamMuted,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(shape)
            .then(base)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
