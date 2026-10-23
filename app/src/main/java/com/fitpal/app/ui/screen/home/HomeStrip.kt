package com.fitpal.app.ui.screen.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitpal.app.domain.StatusKind
import com.fitpal.app.domain.TrackerStatus
import com.fitpal.app.domain.model.StripLayout
import com.fitpal.app.domain.model.StripWidth
import com.fitpal.app.domain.model.TrackerPlacement
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.NumberFamily
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

/*
 * Home's strip (docs/redesign/05): every tracker placed "in the strip", laid out by StripLayout so
 * each row fills the width. One item design in three widths — wide, half, quarter — and two
 * styles for the whole strip: Rings (ring or bar + status) or Numbers (just icon, number, label).
 * It replaced the hero's drawer: nothing is folded away any more.
 */

/** One tracker as Home draws it: in the strip, or as a card of its own. */
data class HomeTracker(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color,
    /** The full number ("1,450 ml", "120 of 300 kcal"). */
    val value: String,
    /** The short number for a quarter item ("1.45 L", "120/300"). */
    val shortValue: String,
    /** Toward the goal or limit, 0..1; null draws an empty ring. */
    val fraction: Float?,
    val status: TrackerStatus?,
    val onClick: () -> Unit,
    /** Its quick action (water: add a glass); null = the long press opens the page too. */
    val onLongClick: (() -> Unit)? = null,
    /** A one-tap button on the wide and half items ("+250"), with its action. */
    val quickAdd: Pair<String, () -> Unit>? = null
) {
    val isOver: Boolean get() = status?.kind == StatusKind.OVER
    val ringColor: Color get() = if (isOver) MacroOver else color
}

private val CellShape = RoundedCornerShape(16.dp)
private val Gap = 8.dp

/** The strip: rows of up to four, each filling the width. Draws nothing without items. */
@Composable
fun TodayStrip(
    items: List<HomeTracker>,
    bigger: String?,
    numbers: Boolean,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val byKey = items.associateBy { it.key }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gap)) {
        StripLayout.rows(items.map { it.key }, bigger).forEach { row ->
            // Equal heights in a row (P16): a row of one wide item is the slim one.
            val height = when {
                numbers -> 56.dp
                row.size == 1 -> 56.dp
                else -> 84.dp
            }
            Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                row.forEach { cell ->
                    val item = byKey.getValue(cell.key)
                    val weight = when (cell.width) {
                        StripWidth.WIDE -> 4f
                        StripWidth.HALF -> 2f
                        StripWidth.QUARTER -> 1f
                    }
                    StripItem(item, cell.width, numbers, Modifier.weight(weight).fillMaxHeight())
                }
            }
        }
    }
}

/**
 * A tracker as a full-width card (Caffeine's "Card" placement): the strip's wide item, on the
 * card surface and a little taller.
 */
@Composable
fun TrackerWideCard(item: HomeTracker, modifier: Modifier = Modifier) {
    StripItem(item, StripWidth.WIDE, numbers = false, modifier = modifier.fillMaxWidth().height(72.dp), card = true)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StripItem(item: HomeTracker, width: StripWidth, numbers: Boolean, modifier: Modifier, card: Boolean = false) {
    // The border speaks for the status: gold when close, red when over (what the drawer's dot did).
    val surface = when {
        card && item.status?.kind != StatusKind.OVER && item.status?.kind != StatusKind.CLOSE -> Modifier.glass()
        item.status?.kind == StatusKind.OVER -> Modifier.accentGlass(MacroOver, CellShape)
        item.status?.kind == StatusKind.CLOSE -> Modifier.accentGlass(Gold, CellShape)
        else -> Modifier.glassSoft(CellShape)
    }
    Box(
        modifier
            .then(surface)
            .combinedClickable(onClick = item.onClick, onLongClick = item.onLongClick ?: item.onClick)
            .padding(horizontal = if (width == StripWidth.QUARTER) 6.dp else 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            numbers -> NumbersItem(item, width)
            width == StripWidth.WIDE -> WideItem(item)
            width == StripWidth.HALF -> HalfItem(item)
            else -> QuarterItem(item)
        }
    }
}

@Composable
private fun WideItem(item: HomeTracker) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RingIcon(item, 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${item.label} · ${item.value}", style = MaterialTheme.typography.labelLarge, color = Cream,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                item.status?.let {
                    Text(
                        it.text, style = MaterialTheme.typography.labelSmall, color = CreamMuted, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false)
                    )
                }
            }
            ProgressBar(item)
        }
        item.quickAdd?.let { (label, add) ->
            Spacer(Modifier.width(10.dp))
            QuickAddPill(label, add)
        }
    }
}

@Composable
private fun HalfItem(item: HomeTracker) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, contentDescription = null, tint = item.ringColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                item.label, style = MaterialTheme.typography.labelMedium, color = CreamMuted, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            item.quickAdd?.let { (label, add) -> QuickAddPill(label, add) }
        }
        FitNumber(item.value, maxSize = 18.sp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ProgressBar(item)
            Text(
                item.status?.text ?: " ", style = MaterialTheme.typography.labelSmall, color = CreamFaint,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuarterItem(item: HomeTracker) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        RingIcon(item, 28.dp)
        FitNumber(item.shortValue, maxSize = 14.sp, center = true)
        Text(
            item.label, style = MaterialTheme.typography.labelSmall, color = CreamFaint, maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The "Numbers" strip style: no ring or bar — icon, number, label — at every width. */
@Composable
private fun NumbersItem(item: HomeTracker, width: StripWidth) {
    if (width == StripWidth.QUARTER) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(item.icon, contentDescription = null, tint = item.ringColor, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                FitNumber(item.shortValue, maxSize = 14.sp, center = true)
            }
            Text(item.label, style = MaterialTheme.typography.labelSmall, color = CreamFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Icon(item.icon, contentDescription = null, tint = item.ringColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            FitNumber(if (width == StripWidth.WIDE) item.value else item.shortValue, maxSize = 16.sp)
            Text(item.label, style = MaterialTheme.typography.labelSmall, color = CreamFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        item.quickAdd?.let { (label, add) -> QuickAddPill(label, add) }
    }
}

/** A thin ring around the tracker's icon (an empty track without a goal). */
@Composable
private fun RingIcon(item: HomeTracker, size: Dp) {
    val track = filmColor(0.10f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val f = item.fraction
            if (f != null && f > 0f) {
                drawArc(item.ringColor, -90f, 360f * f.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Icon(item.icon, contentDescription = null, tint = item.ringColor, modifier = Modifier.size(size * 0.5f))
    }
}

/** The goal (or limit) bar: the end of the bar is the goal. */
@Composable
private fun ProgressBar(item: HomeTracker) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(filmColor(0.10f))
    ) {
        val f = item.fraction
        if (f != null && f > 0f) {
            Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(item.ringColor))
        }
    }
}

@Composable
private fun QuickAddPill(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = Cream,
        maxLines = 1,
        modifier = Modifier
            .glassSoft(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/**
 * A number that shrinks to fit its width (down to 10 sp) instead of being cut off — a number is
 * never ellipsized. Inter, tabular, bold, like every hero number.
 */
@Composable
private fun FitNumber(text: String, maxSize: TextUnit, center: Boolean = false) {
    var size by remember(text, maxSize) { mutableStateOf(maxSize) }
    var ready by remember(text, maxSize) { mutableStateOf(false) }
    Text(
        text,
        style = TextStyle(
            fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = size, fontFeatureSettings = "tnum",
            textAlign = if (center) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
        ),
        color = Cream,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { layout ->
            if (layout.didOverflowWidth && size.value > 10f) size = (size.value - 1f).coerceAtLeast(10f).sp else ready = true
        },
        modifier = Modifier.drawWithContent { if (ready) drawContent() }
    )
}

/**
 * The one-time note for users who had the hero's drawer: where its rings went. Dismissed for good
 * with the ×.
 */
@Composable
fun StripMovedTip(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().glassSoft().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Your caffeine and food limits moved out of the drawer. Change how each one shows in " +
                "Settings → Home & screens.",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.Close, contentDescription = "Dismiss", tint = CreamMuted,
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(8.dp)
        )
    }
}

/** In edit mode, a tracker that isn't a card: where it is, in one line. */
@Composable
internal fun PlacedElsewhere(item: HomeTracker?, label: String, placement: TrackerPlacement?, featureOn: Boolean) {
    val where = when {
        !featureOn -> "Not in use — turn it on in Settings"
        placement == TrackerPlacement.STRIP -> "In the strip"
        placement == TrackerPlacement.MAIN -> "On the main card"
        else -> ""
    }
    Row(Modifier.fillMaxWidth().glassSoft().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        item?.let {
            Icon(it.icon, contentDescription = null, tint = it.color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = Cream, modifier = Modifier.weight(1f))
        Text(where, style = MaterialTheme.typography.labelMedium, color = if (featureOn) GoldLight else CreamFaint)
    }
}

/**
 * The gentle suggestion (docs/redesign/06): one optional feature the user's own logs point to, under
 * the strip. "Turn on" opens its setup, "Not now" waits a week, × never asks about it again.
 */
@Composable
fun FeatureSuggestionCard(
    suggestion: com.fitpal.app.domain.FeatureSuggestions.Suggestion,
    onTurnOn: () -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().glassSoft().padding(start = 14.dp, top = 10.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.fitpal.app.ui.component.FeatureIconCircle(suggestion.feature, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(suggestion.text, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.Close, contentDescription = "Don't suggest ${suggestion.feature.title} again", tint = CreamMuted,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(start = 42.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Turn on",
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = com.fitpal.app.ui.theme.OnGold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(GoldLight).clickable(onClick = onTurnOn)
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            )
            Text(
                "Not now",
                style = MaterialTheme.typography.labelLarge, color = CreamMuted,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onNotNow).padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
    }
}
