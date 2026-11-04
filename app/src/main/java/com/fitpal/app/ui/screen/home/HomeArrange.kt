package com.fitpal.app.ui.screen.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.CardSize
import com.fitpal.app.domain.model.ResolvedCard
import com.fitpal.app.domain.model.TrackerPlacement
import com.fitpal.app.ui.component.ChoiceChip
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.component.TrackerSheet
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.LookShapes
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glassSoft

/*
 * Arrange mode (docs/redesign/13): Home looks like Home — the real cards in their real places, small
 * cards side by side — each with a dashed outline and a ⋯. Hold and drag a card to move it; tap it for
 * one sheet with its choices drawn as little pictures. The strip has its own sheet (what's in it, in
 * what order), and hidden cards wait behind "+ Add a card". No control bars, no wiggle.
 */

/** The outline and ⋯ colour in this look. */
@Composable
private fun arrangeColor(): Color = when (LookState.style) {
    LookStyle.MATERIAL -> MaterialTheme.colorScheme.primary
    LookStyle.LIQUID -> if (LookState.colors.isLight) GoldLight else Color.White.copy(alpha = 0.8f)
    else -> GoldLight.copy(alpha = 0.75f)
}

/**
 * The one-time tip (2.17, the 4 Nov review #2): the Arrange icon left the date line, so say once that
 * holding a card is the way in. Drawn like the strip's old tip — a soft row, one sentence and an ×.
 */
@Composable
internal fun HoldToArrangeTip(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().glassSoft().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Hold any card to arrange Home.",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.Close, contentDescription = "Dismiss", tint = CreamMuted,
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(8.dp)
        )
    }
}

/**
 * The way into Arrange when Home has no card left to hold (every card hidden but the strip and the
 * meals, which keep their own long-press): a small gold link at the end of Home.
 */
@Composable
internal fun ArrangeHomeLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Arrange Home", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = GoldLight)
        }
    }
}

/** Arrange mode's banner: what you can do here, and Done. */
@Composable
internal fun ArrangeBanner(onDone: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().accentGlass(GoldLight).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Arranging Home", style = MaterialTheme.typography.titleSmall, color = Cream)
            Text("Hold and drag to move · tap a card for its options", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
        Spacer(Modifier.width(8.dp))
        GoldButton("Done", onClick = onDone)
    }
}

/**
 * One card in arrange mode: the real card, a dashed outline and a ⋯ badge. A layer over the card
 * takes the taps (→ [onOptions]) and the hold that starts a drag ([dragHandle], from the reorder
 * library), so the card's own buttons don't react while you arrange.
 */
@Composable
internal fun ArrangeCard(
    label: String,
    dragging: Boolean,
    dragHandle: Modifier,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val color = arrangeColor()
    val shape = LookShapes.card
    Box(
        modifier
            .graphicsLayer {
                if (dragging) { scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 18.dp.toPx() }
            }
            .dashedOutline(color, shape, if (dragging) 2.dp else 1.5.dp)
    ) {
        content()
        Box(
            Modifier.matchParentSize().then(dragHandle).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Options for $label",
                onClick = onOptions
            )
        )
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp).size(24.dp).clip(CircleShape)
                .background(LookState.colors.inkBlack).border(1.dp, color, CircleShape).clickable(onClick = onOptions),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "Options for $label", tint = color, modifier = Modifier.size(15.dp))
        }
    }
}

/** A dashed line 3 dp outside the card, following its shape — drawn first, so the ⋯ badge sits on top of it. */
private fun Modifier.dashedOutline(color: Color, shape: Shape, width: androidx.compose.ui.unit.Dp): Modifier = drawWithContent {
    val o = 3.dp.toPx()
    val outline = shape.createOutline(Size(size.width + 2 * o, size.height + 2 * o), layoutDirection, this)
    translate(-o, -o) {
        drawOutline(
            outline, color,
            style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())))
        )
    }
    drawContent()
}

/**
 * Hold a Home card to start arranging. Watches the press without taking it from the card; only once
 * the finger has stayed still for the long-press time does it call [onHold] and take the rest of that
 * press, so the card's own tap doesn't fire on release. Moving (a scroll or a swipe) cancels it.
 */
internal fun Modifier.holdToArrange(onHold: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var ended = false
            while (!ended) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                ended = change == null || !change.pressed || change.isConsumed ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            }
            true
        }
        if (released == null) {
            onHold()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// The sheets
// ---------------------------------------------------------------------------------------------------

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = CreamMuted)
}

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** A choice in an options sheet: a small picture of the result over its name. */
@Composable
private fun RowScope.OptionTile(label: String, selected: Boolean, onClick: () -> Unit, picture: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.weight(1f)
            .then(if (selected) Modifier.accentGlass(GoldLight, shape) else Modifier.glassSoft(shape))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(width = 50.dp, height = 36.dp)) { picture() }
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = if (selected) GoldLight else Cream,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Where a tracker would sit: on the main card, a full card, a small card, the strip, or off Home. */
@Composable
private fun PlacementPicture(placement: TrackerPlacement, selected: Boolean) {
    val on = if (selected) GoldLight else Cream.copy(alpha = 0.55f)
    val faint = Cream.copy(alpha = 0.14f)
    Canvas(Modifier.fillMaxSize()) {
        val r = CornerRadius(3.dp.toPx())
        val w = size.width
        val h = size.height
        val gap = 3.dp.toPx()
        when (placement) {
            TrackerPlacement.MAIN -> {
                drawRoundRect(faint, size = Size(w, h), cornerRadius = r)
                drawCircle(on, radius = h * 0.3f, center = Offset(w / 2, h / 2), style = Stroke(2.5.dp.toPx()))
            }
            TrackerPlacement.CARD_M -> {
                drawRoundRect(faint, size = Size(w, h * 0.3f), cornerRadius = r)
                drawRoundRect(on, topLeft = Offset(0f, h * 0.36f), size = Size(w, h * 0.34f), cornerRadius = r)
                drawRoundRect(faint, topLeft = Offset(0f, h * 0.76f), size = Size(w, h * 0.24f), cornerRadius = r)
            }
            TrackerPlacement.CARD_S -> {
                drawRoundRect(faint, size = Size(w, h * 0.3f), cornerRadius = r)
                drawRoundRect(on, topLeft = Offset(0f, h * 0.36f), size = Size((w - gap) / 2, h * 0.64f), cornerRadius = r)
                drawRoundRect(faint, topLeft = Offset((w + gap) / 2, h * 0.36f), size = Size((w - gap) / 2, h * 0.64f), cornerRadius = r)
            }
            TrackerPlacement.STRIP -> {
                drawRoundRect(faint, size = Size(w, h * 0.42f), cornerRadius = r)
                val cw = (w - 3 * gap) / 4
                for (i in 0 until 4) drawRoundRect(if (i == 1) on else faint, topLeft = Offset(i * (cw + gap), h * 0.5f), size = Size(cw, h * 0.3f), cornerRadius = r)
            }
            TrackerPlacement.OFF_HOME -> {
                drawRoundRect(faint, size = Size(w, h), cornerRadius = r)
                drawLine(on, Offset(w * 0.25f, h * 0.2f), Offset(w * 0.75f, h * 0.8f), strokeWidth = 2.5.dp.toPx())
            }
        }
    }
}

/** A card's size: half width, full width, or full width and tall. */
@Composable
private fun SizePicture(cardSize: CardSize, selected: Boolean) {
    val on = if (selected) GoldLight else Cream.copy(alpha = 0.55f)
    val faint = Cream.copy(alpha = 0.14f)
    Canvas(Modifier.fillMaxSize()) {
        val r = CornerRadius(3.dp.toPx())
        val w = size.width
        val h = size.height
        val gap = 3.dp.toPx()
        when (cardSize) {
            CardSize.S -> {
                drawRoundRect(on, topLeft = Offset(0f, h * 0.25f), size = Size((w - gap) / 2, h * 0.5f), cornerRadius = r)
                drawRoundRect(faint, topLeft = Offset((w + gap) / 2, h * 0.25f), size = Size((w - gap) / 2, h * 0.5f), cornerRadius = r)
            }
            CardSize.M -> drawRoundRect(on, topLeft = Offset(0f, h * 0.25f), size = Size(w, h * 0.5f), cornerRadius = r)
            CardSize.L -> drawRoundRect(on, topLeft = Offset(0f, h * 0.05f), size = Size(w, h * 0.9f), cornerRadius = r)
        }
    }
}

/** A style: a ring, rings, bars, or numbers. */
@Composable
private fun VariantPicture(variant: String, selected: Boolean) {
    val on = if (selected) GoldLight else Cream.copy(alpha = 0.55f)
    if (variant == "numbers") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("123", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = on)
        }
        return
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(2.5.dp.toPx())
        when (variant) {
            "ring" -> drawCircle(on, radius = h * 0.38f, center = Offset(w / 2, h / 2), style = stroke)
            "bars" -> for (i in 0 until 3) {
                val y = h * (0.22f + i * 0.28f)
                drawLine(on.copy(alpha = 0.3f), Offset(0f, y), Offset(w, y), strokeWidth = 3.dp.toPx())
                drawLine(on, Offset(0f, y), Offset(w * (0.8f - i * 0.2f), y), strokeWidth = 3.dp.toPx())
            }
            else -> for (i in 0 until 3) drawCircle(on, radius = h * 0.2f, center = Offset(w * (0.18f + i * 0.32f), h / 2), style = stroke)
        }
    }
}

/**
 * A card's options: where a tracker shows, or a card's size; its style; hide it. [extra] adds rows of
 * the card's own (the main card's "Also on this card"). Every pick applies at once.
 */
@Composable
internal fun CardOptionsSheet(
    card: ResolvedCard,
    onPlacement: (TrackerPlacement) -> Unit,
    onSize: (CardSize) -> Unit,
    onVariant: (String) -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable ColumnScope.() -> Unit)? = null
) {
    TrackerSheet(title = card.def.label, onDismiss = onDismiss) {
        if (card.def.isTracker) {
            SheetLabel("Show as")
            TileRow {
                (card.def.placements + TrackerPlacement.OFF_HOME).forEach { p ->
                    val selected = if (p == TrackerPlacement.OFF_HOME) !card.enabled else card.enabled && card.placement == p
                    OptionTile(p.label, selected, onClick = {
                        onPlacement(p)
                        if (p == TrackerPlacement.OFF_HOME || p == TrackerPlacement.STRIP || p == TrackerPlacement.MAIN) onDismiss()
                    }) { PlacementPicture(p, selected) }
                }
            }
        } else if (card.def.sizes.size > 1) {
            SheetLabel("Size")
            TileRow {
                card.def.sizes.sortedBy { it.ordinal }.forEach { s ->
                    OptionTile(s.label, card.size == s, onClick = { onSize(s) }) { SizePicture(s, card.size == s) }
                }
            }
        }
        if (card.def.variants.size > 1) {
            SheetLabel("Style")
            TileRow {
                card.def.variants.forEach { v ->
                    OptionTile(v.label, card.variant == v.id, onClick = { onVariant(v.id) }) { VariantPicture(v.id, card.variant == v.id) }
                }
            }
        }
        extra?.invoke(this)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!card.def.isTracker) {
                Text(
                    "Hide from Home", style = MaterialTheme.typography.labelLarge, color = CreamMuted,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onHide(); onDismiss() }.padding(horizontal = 4.dp, vertical = 10.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            GoldButton("Done", onClick = onDismiss)
        }
    }
}

/** The main card's "Also on this card": a tracker drawn on it (fasting), and where else it could go. */
@Composable
internal fun AlsoOnThisCard(tracker: ResolvedCard, onPlacement: (TrackerPlacement) -> Unit) {
    SheetLabel("Also on this card: ${tracker.def.label}")
    TileRow {
        (tracker.def.placements + TrackerPlacement.OFF_HOME).forEach { p ->
            val selected = tracker.enabled && tracker.placement == p
            OptionTile(p.label, selected, onClick = { onPlacement(p) }) { PlacementPicture(p, selected) }
        }
    }
}

/**
 * The strip's options: its style, what's in it in what order (up / down), moving a tracker out of it,
 * and — with three in a row — which one is drawn bigger.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StripOptionsSheet(
    strip: ResolvedCard,
    items: List<Pair<ResolvedCard, HomeTracker?>>,
    bigger: String?,
    onVariant: (String) -> Unit,
    onBigger: (String) -> Unit,
    onMove: (fromKey: String, toKey: String) -> Unit,
    onPlacement: (key: String, TrackerPlacement) -> Unit,
    onDismiss: () -> Unit
) {
    var movingOut by remember { mutableStateOf<String?>(null) }
    TrackerSheet(title = strip.def.label, onDismiss = onDismiss) {
        if (strip.def.variants.size > 1) {
            SheetLabel("Style")
            TileRow {
                strip.def.variants.forEach { v ->
                    OptionTile(v.label, strip.variant == v.id, onClick = { onVariant(v.id) }) { VariantPicture(v.id, strip.variant == v.id) }
                }
            }
        }
        SheetLabel("In the strip")
        if (items.isEmpty()) Text("Nothing yet. Pick Strip in a tracker's options.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { i, (card, tracker) ->
                Column(Modifier.fillMaxWidth().glassSoft(RoundedCornerShape(14.dp)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        tracker?.let { Icon(it.icon, contentDescription = null, tint = it.color, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(8.dp))
                        Text(card.def.label, style = MaterialTheme.typography.bodyLarge, color = Cream, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MoveButton(Icons.Default.KeyboardArrowUp, "Move ${card.def.label} left", enabled = i > 0) { onMove(card.key, items[i - 1].first.key) }
                        MoveButton(Icons.Default.KeyboardArrowDown, "Move ${card.def.label} right", enabled = i < items.lastIndex) { onMove(card.key, items[i + 1].first.key) }
                        Text(
                            "Move out", style = MaterialTheme.typography.labelMedium, color = GoldLight,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { movingOut = if (movingOut == card.key) null else card.key }.padding(8.dp)
                        )
                    }
                    if (movingOut == card.key) {
                        Spacer(Modifier.height(6.dp))
                        TileRow {
                            (card.def.placements.filter { it != TrackerPlacement.STRIP } + TrackerPlacement.OFF_HOME).forEach { p ->
                                OptionTile(p.label, false, onClick = { onPlacement(card.key, p); movingOut = null }) { PlacementPicture(p, false) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
        if (items.size % 4 == 3) {
            SheetLabel("Bigger in a row of three")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val current = bigger?.takeIf { b -> items.any { it.first.key == b } } ?: items.first().first.key
                items.forEach { (card, _) -> ChoiceChip(card.def.label, selected = card.key == current) { onBigger(card.key) } }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            GoldButton("Done", onClick = onDismiss)
        }
    }
}

@Composable
private fun MoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Icon(
        icon, contentDescription = description, tint = if (enabled) Cream else CreamFaint.copy(alpha = 0.3f),
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).padding(9.dp)
    )
}

/**
 * Cards that aren't on Home: tap one to bring it back (a tracker asks where), and Reset Home to its
 * default layout (asks first, in place).
 */
@Composable
internal fun AddCardSheet(
    hidden: List<ResolvedCard>,
    trackerFor: (String) -> HomeTracker?,
    onShow: (String) -> Unit,
    onPlace: (String, TrackerPlacement) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var placing by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    TrackerSheet(title = "Add a card", onDismiss = onDismiss) {
        if (hidden.isEmpty()) Text("Everything is on Home.", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            hidden.forEach { card ->
                Column(Modifier.fillMaxWidth().glassSoft(RoundedCornerShape(14.dp)).clickable {
                    if (card.def.isTracker) placing = if (placing == card.key) null else card.key
                    else { onShow(card.key); onDismiss() }
                }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        trackerFor(card.key)?.let { Icon(it.icon, contentDescription = null, tint = it.color, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(8.dp))
                        Text(card.def.label, style = MaterialTheme.typography.bodyLarge, color = Cream, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Add, contentDescription = null, tint = GoldLight, modifier = Modifier.size(20.dp))
                    }
                    if (placing == card.key) {
                        Spacer(Modifier.height(8.dp))
                        TileRow {
                            card.def.placements.forEach { p ->
                                OptionTile(p.label, false, onClick = { onPlace(card.key, p); onDismiss() }) { PlacementPicture(p, false) }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (confirmReset) {
                Text("Reset Home?", style = MaterialTheme.typography.labelLarge, color = CreamMuted)
                Text(
                    "Reset", style = MaterialTheme.typography.labelLarge, color = GoldLight,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onReset(); onDismiss() }.padding(10.dp)
                )
                Text(
                    "Cancel", style = MaterialTheme.typography.labelLarge, color = CreamMuted,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { confirmReset = false }.padding(10.dp)
                )
            } else {
                Text(
                    "Reset Home to default", style = MaterialTheme.typography.labelLarge, color = CreamMuted,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { confirmReset = true }.padding(vertical = 10.dp, horizontal = 4.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            GoldButton("Done", onClick = onDismiss)
        }
    }
}

/** "+ Add a card · 2 hidden" at the end of arrange mode. */
@Composable
internal fun AddCardButton(hiddenCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = arrangeColor()
    Row(
        // The outline sits outside the card's shape, so the clip (for the ripple) comes after it.
        modifier.fillMaxWidth().dashedOutline(color, LookShapes.card, 1.5.dp).clip(LookShapes.card).clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (hiddenCount > 0) "Add a card · $hiddenCount hidden" else "Add a card",
            style = MaterialTheme.typography.labelLarge, color = color
        )
    }
}

/** The Meals block while arranging: one card that stands for every meal section. */
@Composable
internal fun MealsEditPlaceholder(sectionCount: Int) {
    Column(Modifier.fillMaxWidth().glassSoft().padding(18.dp)) {
        Text("Meals", style = MaterialTheme.typography.titleMedium, color = Cream)
        Text("$sectionCount meal sections", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
    }
}
