package com.fitpal.app.ui.screen.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.CardSize
import com.fitpal.app.domain.model.ResolvedCard
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.OnGold
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassOverlay
import com.fitpal.app.ui.theme.glassSoft

/*
 * Home's customizable layout pieces: the half-width "small" card, the edit-mode frame each card
 * wears while you rearrange Home, and the tray of hidden cards. HomeScreen decides what goes in
 * each card; these only draw the chrome.
 */

/**
 * A half-width card: an icon, a hero number, one line under it, and an optional one-tap action.
 * The small size of Water / Steps / Exercise / Weight / AI overview / Fasting / Caffeine.
 */
@Composable
internal fun SmallTile(
    icon: ImageVector,
    title: String,
    value: String,
    sub: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .heightIn(min = 132.dp)
            .accentGlass(accent)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = CreamMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        // Hero numbers are Inter, tabular (the design system's rule) — not the serif headline face.
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)), fontWeight = FontWeight.Bold, color = Cream, maxLines = 1)
        // One line, always: text never changes a card's size, so tiles in a row stay level.
        Text(sub, style = MaterialTheme.typography.labelSmall, color = CreamFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // The action sits in a slot at the bottom, so a tile with a button lines up with one without.
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.height(10.dp))
            action()
        }
    }
}

/**
 * The frame a card wears in edit mode: the real card underneath (dimmed and not tappable), a gentle
 * wiggle (unless "Reduce motion"), and a control strip — drag handle, size, style, hide.
 * [dragHandle] is the modifier from the reorder library that makes the handle draggable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditFrame(
    card: ResolvedCard,
    index: Int,
    dragging: Boolean,
    dragHandle: Modifier,
    onSize: (CardSize) -> Unit,
    onVariant: (String) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val still = LookState.appearance.reduceMotion
    val rotation = if (still || dragging) 0f else {
        val t = rememberInfiniteTransition(label = "wiggle")
        t.animateFloat(
            initialValue = -0.35f, targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(260 + (index % 3) * 40), RepeatMode.Reverse),
            label = "rot"
        ).value
    }
    Column(modifier.graphicsLayer { rotationZ = rotation; if (dragging) { scaleX = 1.02f; scaleY = 1.02f } }) {
        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .glassOverlay(RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle, contentDescription = "Drag ${card.def.label} to move it",
                tint = if (dragging) GoldLight else Cream,
                modifier = dragHandle.size(32.dp).padding(4.dp)
            )
            Text(
                card.def.label, style = MaterialTheme.typography.labelLarge, color = Cream,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.VisibilityOff, contentDescription = "Hide ${card.def.label}",
                tint = CreamMuted,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onHide).padding(6.dp)
            )
        }
        if (card.def.sizes.size > 1 || card.def.variants.size > 1) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (card.def.sizes.size > 1) {
                    ChipLabel("Size")
                    card.def.sizes.sortedBy { it.ordinal }.forEach { s ->
                        EditChip(s.label, selected = card.size == s) { onSize(s) }
                    }
                }
                if (card.def.variants.size > 1) {
                    ChipLabel("Style")
                    card.def.variants.forEach { v ->
                        EditChip(v.label, selected = card.variant == v.id) { onVariant(v.id) }
                    }
                }
            }
        }
        // The card itself — visible, but its own buttons are off while editing.
        Box {
            Box(Modifier.graphicsLayer { alpha = 0.85f }) { content() }
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            )
        }
    }
}

@Composable
private fun ChipLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = CreamFaint, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun EditChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

/** Edit mode's footer: the hidden cards (tap to bring one back), Reset, and Done. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HiddenCardsTray(
    hidden: List<ResolvedCard>,
    onShow: (String) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Hidden cards", style = MaterialTheme.typography.titleSmall, color = Cream)
        Text(
            if (hidden.isEmpty()) "Everything is on Home. Hide a card with its eye button."
            else "Tap one to put it back on Home.",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted
        )
        if (hidden.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hidden.forEach { c ->
                    Row(
                        modifier = Modifier
                            .glassSoft(RoundedCornerShape(50))
                            .clickable { onShow(c.key) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(c.def.label, style = MaterialTheme.typography.labelLarge, color = Cream)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Reset to default",
                style = MaterialTheme.typography.labelLarge, color = CreamMuted,
                modifier = Modifier
                    .weight(1f)
                    .glassSoft(RoundedCornerShape(50))
                    .clickable(onClick = onReset)
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "Done",
                style = MaterialTheme.typography.labelLarge, color = OnGold, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(GoldLight)
                    .clickable(onClick = onDone)
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** The banner at the top of Home while editing. */
@Composable
internal fun EditModeBanner(onDone: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().accentGlass(GoldLight).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Arranging Home", style = MaterialTheme.typography.titleSmall, color = Cream)
            Text(
                "Drag ≡ to move a card, pick its size or style, or hide it.",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
        }
        Text(
            "Done",
            style = MaterialTheme.typography.labelLarge, color = OnGold, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(GoldLight).clickable(onClick = onDone)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** Placeholder for the Meals block while editing (its sections collapse to one movable card). */
@Composable
internal fun MealsEditPlaceholder(sectionCount: Int) {
    Column(Modifier.fillMaxWidth().glass().padding(18.dp)) {
        Text("Meals", style = MaterialTheme.typography.titleMedium, color = Cream)
        Text("$sectionCount meal panels", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
    }
}
