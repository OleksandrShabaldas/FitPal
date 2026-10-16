package com.fitpal.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.GoalKind
import com.fitpal.app.domain.StatusKind
import com.fitpal.app.domain.TrackerStatus
import com.fitpal.app.ui.theme.CardGap
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.OnGold
import com.fitpal.app.ui.theme.OverlaySurface
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

/*
 * The tracker template (docs/redesign/01-tracker-template.md): every tracker page — water first —
 * is the same blocks in the same order, so learning one teaches them all.
 *
 *   top bar (back · title · info · settings)
 *   hero (a visual + the number now + one status pill)
 *   quick actions
 *   one chart that switches Today ⇄ Week
 *   extra sections, then today's entries
 */

/**
 * A tracker page's frame: backdrop, top bar with the (i) and the gear, and a scrolling column for the
 * blocks. [snackbarHost] shows undo messages above the content.
 */
@Composable
fun TrackerScaffold(
    title: String,
    onBack: () -> Unit,
    infoTitle: String,
    infoText: String,
    onOpenSettings: (() -> Unit)?,
    snackbarHost: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                GlassTopBar(title = title, onBack = onBack) {
                    TopBarIcon(Icons.Outlined.Info, "How $title works") { showInfo = true }
                    if (onOpenSettings != null) {
                        Spacer(Modifier.width(8.dp))
                        TopBarIcon(Icons.Outlined.Settings, "$title settings", onOpenSettings)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(CardGap),
                    content = content
                )
            }
            if (snackbarHost != null) {
                SnackbarHost(
                    hostState = snackbarHost,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = OverlaySurface,
                        contentColor = Cream,
                        actionColor = GoldLight,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    }
    if (showInfo) InfoSheet(title = infoTitle, text = infoText, onDismiss = { showInfo = false })
}

@Composable
private fun TopBarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).glass(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = CreamMuted, modifier = Modifier.size(20.dp))
    }
}

/** The hero card: a visual (usually a ring) on the left, the numbers and the status on the right. */
@Composable
fun TrackerHero(
    visual: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().glass().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (visual != null) {
            visual()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/** One short sentence with a state colour — the tracker's verdict on right now. */
@Composable
fun StatusPill(status: TrackerStatus, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val surface = when (status.kind) {
        StatusKind.OK -> Modifier.accentGlass(ScoreFair, shape)
        StatusKind.CLOSE -> Modifier.accentGlass(Gold, shape)
        StatusKind.OVER -> Modifier.accentGlass(MacroOver, shape)
        StatusKind.NEUTRAL -> Modifier.glassSoft(shape)
    }
    Text(
        status.text,
        style = MaterialTheme.typography.labelLarge,
        color = Cream,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.then(surface).padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * The tracker's one chart, switching between [today] and [week] (the view is the caller's to persist).
 * Both views get the same height, so switching doesn't move the page. [takeaway] is the line under it.
 */
@Composable
fun TrackerChart(
    todayTitle: String,
    weekTitle: String,
    view: Int,
    onView: (Int) -> Unit,
    accent: Color,
    takeaway: String?,
    today: @Composable BoxScope.() -> Unit,
    week: @Composable BoxScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (view == 0) todayTitle else weekTitle,
                style = MaterialTheme.typography.titleMedium, color = Cream,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            SegmentedPills(
                labels = listOf("Today", "Week"),
                selectedIndex = view,
                accent = accent,
                onSelect = onView,
                modifier = Modifier.width(150.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(150.dp)) { if (view == 0) today() else week() }
        if (!takeaway.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(takeaway, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
    }
}

/**
 * Seven bars, Monday to Sunday, against a dashed [goal] line. For a [GoalKind.REACH] goal a bar short
 * of it is drawn faint; for a [GoalKind.LIMIT] a bar past it turns red. [todayIndex] is outlined while
 * the day is still going. Tapping a bar calls [onDay] with its index.
 */
@Composable
fun WeekBars(
    values: List<Float?>,
    goal: Float?,
    goalKind: GoalKind,
    color: Color,
    todayIndex: Int?,
    goalLabel: String?,
    modifier: Modifier = Modifier,
    onDay: ((Int) -> Unit)? = null
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = CreamFaint)
    val todayStyle = MaterialTheme.typography.labelSmall.copy(color = Cream, fontWeight = FontWeight.Bold)
    val goalStyle = MaterialTheme.typography.labelSmall.copy(color = CreamMuted)
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val track = com.fitpal.app.ui.theme.filmColor(0.08f)
    Canvas(
        modifier = modifier.fillMaxSize().then(
            if (onDay == null) Modifier
            else Modifier.pointerInput(values) {
                detectTapGestures { p -> onDay((p.x / (size.width / 7f)).toInt().coerceIn(0, 6)) }
            }
        )
    ) {
        val labelH = 16.dp.toPx()
        val top = 14.dp.toPx()
        val plotH = size.height - labelH - top
        val slot = size.width / 7f
        val barW = slot * 0.56f
        val max = (listOfNotNull(goal) + values.filterNotNull()).maxOrNull()?.takeIf { it > 0f }?.times(1.12f) ?: 1f
        fun yOf(v: Float) = top + plotH - (v / max).coerceIn(0f, 1f) * plotH
        val radius = CornerRadius(4.dp.toPx())
        for (i in 0 until 7) {
            val x = slot * i + (slot - barW) / 2f
            val v = values.getOrNull(i)
            // A faint track behind every bar, so empty days still read as days.
            drawRoundRect(track, Offset(x, top), Size(barW, plotH), radius)
            if (v != null && v > 0f) {
                val y = yOf(v)
                val reached = goal == null || v >= goal
                val barColor = when (goalKind) {
                    GoalKind.REACH -> if (reached) color else color.copy(alpha = 0.5f)
                    GoalKind.LIMIT -> if (goal != null && v > goal) MacroOver else color
                }
                if (i == todayIndex) {
                    drawRoundRect(barColor.copy(alpha = 0.35f), Offset(x, y), Size(barW, top + plotH - y), radius)
                    drawRoundRect(
                        barColor, Offset(x, y), Size(barW, top + plotH - y), radius,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    )
                } else {
                    drawRoundRect(barColor, Offset(x, y), Size(barW, top + plotH - y), radius)
                }
            }
            val lbl = measurer.measure(days[i], if (i == todayIndex) todayStyle else labelStyle)
            drawText(lbl, topLeft = Offset(slot * i + (slot - lbl.size.width) / 2f, size.height - lbl.size.height))
        }
        if (goal != null && goal > 0f) {
            val gy = yOf(goal)
            drawLine(
                Cream.copy(alpha = 0.5f), Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
            goalLabel?.let {
                val g = measurer.measure(it, goalStyle)
                drawText(g, topLeft = Offset(size.width - g.size.width, (gy - g.size.height - 1.dp.toPx()).coerceAtLeast(0f)))
            }
        }
    }
}

/**
 * The bottom sheet every small tracker input uses (an amount, a goal) — instead of a plain dialog.
 * Opaque like the app's other overlays, with a drag handle; content scrolls above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = OverlaySurface,
        contentColor = Cream,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CreamFaint) }
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Cream)
            content()
        }
    }
}

/** The main action of a sheet or block: a gold pill. */
@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) GoldLight else GoldLight.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = OnGold)
    }
}

/** A secondary action: a glass pill. */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .glassSoft(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Cream)
    }
}

/** A titled glass card for a tracker page block, with an optional action at the end of its title. */
@Composable
fun TrackerSection(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.weight(1f))
            action?.invoke(this)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}
