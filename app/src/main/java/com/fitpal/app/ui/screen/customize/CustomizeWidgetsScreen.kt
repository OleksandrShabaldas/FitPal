package com.fitpal.app.ui.screen.customize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.fitpal.app.ui.component.kit.FpSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.model.ResolvedCard
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.OnGold
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * One screen's cards as a list: drag ≡ to reorder, the switch shows or hides, and the chips set
 * each card's size (Small cards pair up side by side) and style. Home can also be arranged in place
 * from the pencil in its header.
 */
@Composable
fun CustomizeWidgetsScreen(
    onBack: () -> Unit,
    viewModel: CustomizeWidgetsViewModel = hiltViewModel()
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = from.key as? String
        val t = to.key as? String
        val from = cards.firstOrNull { it.key == f }
        val into = cards.firstOrNull { it.key == t }
        // Analytics' sections are fixed: a card moves only within its own section.
        if (f != null && t != null && from != null && into != null && from.def.section == into.def.section) viewModel.move(f, t)
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Customize ${viewModel.screenTitle}", onBack = onBack)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "hint") {
                    Text(
                        "Drag ≡ to reorder. The switch shows or hides a card; the chips pick its size and style. " +
                            "Two Small cards sit side by side; trackers in the strip follow this order.",
                        style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                    )
                }
                val shown = com.fitpal.app.domain.model.ScreenWidgets.bySection(cards).filter { viewModel.inUse(it.key) }
                val titles = com.fitpal.app.domain.model.ScreenWidgets.analyticsSections.toMap()
                shown.forEachIndexed { i, card ->
                    val section = card.def.section
                    if (section != null && shown.getOrNull(i - 1)?.def?.section != section) {
                        item(key = "section_$section") {
                            Text(
                                titles[section] ?: section, style = MaterialTheme.typography.titleMedium, color = Cream,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                    item(key = card.key) {
                    ReorderableItem(reorderState, key = card.key) { dragging ->
                        CardRow(
                            card = card,
                            dragging = dragging,
                            dragHandle = Modifier.draggableHandle(),
                            onToggle = { viewModel.toggle(card.key) },
                            onSize = { viewModel.setSize(card.key, it) },
                            onVariant = { viewModel.setVariant(card.key, it) },
                            onPlacement = { viewModel.setPlacement(card.key, it) }
                        )
                    }
                    }
                }
                item(key = "reset") {
                    TextButton(onClick = viewModel::resetToDefault) { Text("Reset to default") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardRow(
    card: ResolvedCard,
    dragging: Boolean,
    dragHandle: Modifier,
    onToggle: () -> Unit,
    onSize: (com.fitpal.app.domain.model.CardSize) -> Unit,
    onVariant: (String) -> Unit,
    onPlacement: (com.fitpal.app.domain.model.TrackerPlacement) -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().glass().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DragHandle, contentDescription = "Drag to reorder",
                tint = if (dragging) GoldLight else CreamFaint,
                modifier = dragHandle.size(30.dp).padding(3.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                card.def.label, style = MaterialTheme.typography.titleSmall,
                color = if (card.enabled) Cream else CreamFaint, modifier = Modifier.weight(1f)
            )
            FpSwitch(checked = card.enabled, onCheckedChange = { onToggle() })
        }
        if (card.enabled && (card.def.isTracker || card.def.sizes.size > 1 || card.def.variants.size > 1)) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (card.def.isTracker) {
                    // Trackers pick where they show; the strip's order is their order in this list.
                    Text("Show as", style = MaterialTheme.typography.labelMedium, color = CreamFaint, modifier = Modifier.padding(start = 2.dp, top = 6.dp))
                    card.def.placements.forEach { p -> Chip(p.label, card.placement == p) { onPlacement(p) } }
                } else if (card.def.sizes.size > 1) {
                    Text("Size", style = MaterialTheme.typography.labelMedium, color = CreamFaint, modifier = Modifier.padding(start = 2.dp, top = 6.dp))
                    card.def.sizes.sortedBy { it.ordinal }.forEach { s -> Chip(s.label, card.size == s) { onSize(s) } }
                }
                if (card.def.variants.size > 1) {
                    Text("Style", style = MaterialTheme.typography.labelMedium, color = CreamFaint, modifier = Modifier.padding(start = 2.dp, top = 6.dp))
                    card.def.variants.forEach { v -> Chip(v.label, card.variant == v.id) { onVariant(v.id) } }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) OnGold else Cream,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(GoldLight) else Modifier.glassSoft(RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
