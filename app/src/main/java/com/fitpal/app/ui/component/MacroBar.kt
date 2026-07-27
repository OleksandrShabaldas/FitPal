package com.fitpal.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.CarbColor
import com.fitpal.app.ui.theme.FatColor
import com.fitpal.app.ui.theme.FiberColor
import com.fitpal.app.ui.theme.ProteinColor

/**
 * Horizontal bar showing protein / fat / carbs / fiber breakdown with colored segments.
 */
@Composable
fun MacroBar(
    protein: Float,
    fat: Float,
    carbs: Float,
    fiber: Float = 0f,
    modifier: Modifier = Modifier
) {
    val total = protein + fat + carbs + fiber
    if (total <= 0f) return

    Column(modifier = modifier.fillMaxWidth()) {
        // Colored bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            // Use weights so the four segments always fill the whole bar exactly
            // (sequential fillMaxWidth fractions left a gap at the end).
            MacroSegment(weight = protein, color = ProteinColor)
            MacroSegment(weight = fat, color = FatColor)
            MacroSegment(weight = carbs, color = CarbColor)
            MacroSegment(weight = fiber, color = FiberColor)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MacroLabel("Protein", protein, ProteinColor)
            MacroLabel("Fat", fat, FatColor)
            MacroLabel("Carbs", carbs, CarbColor)
            MacroLabel("Fiber", fiber, FiberColor)
        }
    }
}

@Composable
private fun RowScope.MacroSegment(weight: Float, color: Color) {
    if (weight > 0f) {
        Box(
            modifier = Modifier
                .weight(weight)
                .fillMaxHeight()
                .background(color)
        )
    }
}

@Composable
private fun MacroLabel(name: String, grams: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${grams.toInt()}g",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
