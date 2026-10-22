package com.fitpal.app.ui.screen.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.GoalKind
import com.fitpal.app.domain.Takeaway
import com.fitpal.app.domain.TakeawayStatus
import com.fitpal.app.ui.component.BarTrendChart
import com.fitpal.app.ui.component.WeekBars
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.glass
import java.time.LocalDate

/*
 * Analytics' top and its sections (docs/redesign/07): the takeaways card — a few rule-computed
 * sentences, each a way into its card — and the four section headers that fold their cards away.
 * Plus the two Habits cards that came with the sections: caffeine and food limits.
 */

/** "This week" (or "Last week" early on a Monday): up to three takeaways; tap one to jump to its card. */
@Composable
internal fun HighlightsCard(title: String, takeaways: List<Takeaway>, onOpen: (String) -> Unit) {
    if (takeaways.isEmpty()) return
    Column(Modifier.fillMaxWidth().glass().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Cream)
        Spacer(Modifier.height(4.dp))
        takeaways.forEach { t ->
            val ok = t.status == TakeawayStatus.OK
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(t.cardKey) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = if (ok) "On track" else "Worth a look",
                    tint = if (ok) ScoreFair else Gold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    t.text, style = MaterialTheme.typography.bodyMedium, color = Cream, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CreamFaint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A section's header: its name, one line on how it went, and a chevron that folds it. */
@Composable
internal fun AnalyticsSectionHeader(title: String, summary: String, collapsed: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(
        if (collapsed) -90f else 0f,
        animationSpec = tween(if (LookState.appearance.reduceMotion) 0 else 200),
        label = "sectionChevron"
    )
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Cream)
            if (summary.isNotEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = CreamMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = if (collapsed) "Show $title" else "Hide $title",
            tint = CreamMuted,
            modifier = Modifier.size(26.dp).rotate(turn)
        )
    }
}

/**
 * A day-by-day bar chart against a limit: the Mon–Sun [WeekBars] for a week, a bar per day for
 * 30 days. Days past the limit are red.
 */
@Composable
internal fun LimitBars(
    dates: List<LocalDate>,
    values: List<Float?>,
    limit: Float?,
    color: Color,
    unit: String,
    height: androidx.compose.ui.unit.Dp,
    onDay: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    if (dates.size == 7) {
        WeekBars(
            values = values, goal = limit, goalKind = GoalKind.LIMIT, color = color,
            todayIndex = dates.indexOf(today).takeIf { it >= 0 },
            goalLabel = limit?.let { "${it.toInt()} $unit" },
            modifier = Modifier.fillMaxWidth().height(height),
            onDay = { i -> dates.getOrNull(i)?.let(onDay) }
        )
    } else {
        BarTrendChart(
            values = values.map { it ?: 0f }, accent = color, target = limit, overColor = MacroOver,
            height = height, labels = dates.map { it.dayOfMonth.toString() },
            highlightIndex = dates.indexOf(today).takeIf { it >= 0 },
            onBarClick = { i -> dates.getOrNull(i)?.let(onDay) }
        )
    }
}

/** One food limit's row inside the Food limits card: its name, how often it went over, its bars. */
@Composable
internal fun LimitRow(
    icon: ImageVector,
    name: String,
    color: Color,
    overDays: Int,
    onOpen: () -> Unit,
    bars: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, color = Cream, modifier = Modifier.weight(1f))
            Text(
                if (overDays == 0) "Within your cap" else "Over on ${if (overDays == 1) "1 day" else "$overDays days"}",
                style = MaterialTheme.typography.labelMedium, color = if (overDays == 0) CreamMuted else MacroOver
            )
        }
        bars()
    }
}
