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
 * Home's customizable layout pieces: the half-width "small" card, and arrange mode (docs/redesign/13):
 * Home as it is with a dashed outline and a ⋯ on each card, hold-and-drag to move, and one options
 * sheet per card — the strip and the hidden cards have their own. HomeScreen decides what goes in
 * each card; these only draw the chrome. The arrange-mode pieces live in HomeArrange.kt.
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

