package com.fitpal.app.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.glass

/**
 * A frosted-glass segmented control (e.g. 30D / 3M / 6M / 1Y) — in Liquid glass a glass track with a
 * lens that springs between options, in Material You Material's segmented buttons.
 */
@Composable
fun SegmentedPills(
    labels: List<String>,
    selectedIndex: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    when (LookState.style) {
        LookStyle.LIQUID -> { LiquidSegmented(labels, selectedIndex, onSelect, modifier); return }
        LookStyle.MATERIAL -> { MaterialSegmented(labels, selectedIndex, onSelect, modifier); return }
        else -> Unit
    }
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

/**
 * Liquid glass: a glass track with a lens for a thumb — a brighter glass capsule that springs from
 * one option to the next.
 */
@Composable
private fun LiquidSegmented(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val pill = RoundedCornerShape(50)
    val light = LookState.colors.isLight
    BoxWithConstraints(modifier = modifier.fillMaxWidth().glass(pill).padding(4.dp)) {
        val n = labels.size.coerceAtLeast(1)
        val segment = maxWidth / n
        val x by animateDpAsState(
            segment * selectedIndex.coerceIn(0, n - 1),
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
            label = "lens"
        )
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .height(36.dp)
                .shadow(4.dp, pill, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(pill)
                .background(if (light) Color.White else Color.White.copy(alpha = 0.22f))
                .border(1.dp, Color.White.copy(alpha = if (light) 0.9f else 0.45f), pill)
        )
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { i, label ->
                val selected = i == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(pill)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) Cream else CreamMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Material You: Material's segmented buttons (a check on the chosen one when there's room). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialSegmented(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            SegmentedButton(
                selected = selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                icon = { if (labels.size <= 3) SegmentedButtonDefaults.Icon(selected) }
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
