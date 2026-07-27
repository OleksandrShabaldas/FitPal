package com.fitpal.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.glass

/** A frosted-glass segmented control (e.g. 30D / 3M / 6M / 1Y). */
@Composable
fun SegmentedPills(
    labels: List<String>,
    selectedIndex: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pill = RoundedCornerShape(50)
    Row(
        modifier = modifier.fillMaxWidth().glass(pill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(pill)
                    .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) accent else CreamMuted
                )
            }
        }
    }
}
