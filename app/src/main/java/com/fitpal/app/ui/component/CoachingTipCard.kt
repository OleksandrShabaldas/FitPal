package com.fitpal.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.CoachingTip
import com.fitpal.app.domain.model.TipType
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.ScoreGreen

private fun iconFor(type: TipType): ImageVector = when (type) {
    TipType.PORTION -> Icons.Filled.Scale
    TipType.SWAP -> Icons.Filled.SwapHoriz
    TipType.ADDITION -> Icons.Filled.Add
    TipType.TIMING -> Icons.Filled.Schedule
    TipType.GOAL -> Icons.Filled.Flag
    TipType.BALANCE -> Icons.Filled.Balance
}

/**
 * A single meal-aware coaching note — a calm green card with a type-matched icon on the left. Shown
 * only when the AI produced a genuinely useful tip for this plate (see [com.fitpal.app.ml.InsightsWorker]);
 * absent otherwise, by design.
 */
@Composable
fun CoachingTipCard(tip: CoachingTip, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ScoreGreen.copy(alpha = 0.10f), shape)
            .border(1.dp, ScoreGreen.copy(alpha = 0.30f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(ScoreGreen.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconFor(tip.type),
                contentDescription = null,
                tint = ScoreGreen,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Coach's note",
                style = MaterialTheme.typography.labelMedium,
                color = ScoreGreen,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = tip.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Cream
            )
        }
    }
}
