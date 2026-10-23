package com.fitpal.app.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fitpal.app.R
import com.fitpal.app.domain.model.NavStyle
import com.fitpal.app.domain.model.NavTab
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GlassBorder
import com.fitpal.app.ui.theme.GlassFill
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InkBlack
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.OnGold
import com.fitpal.app.ui.theme.TiltState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * The bottom bar: the user's tabs (Settings → Home & screens → Bottom bar) with a raised accent "+"
 * in the middle. It floats over the content as frosted glass (Glow, Liquid glass) or docks to the
 * bottom edge as a solid bar (Paper) — or whichever the user chose. Material You (unless the user
 * picked a floating bar) gets Material's navigation bar with a + button above it at the end. A long
 * press on "+" always opens the full Add menu, whatever the tap is set to do.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingGlassNav(
    tabs: List<NavTab>,
    current: NavTab?,
    onTab: (NavTab) -> Unit,
    onAdd: () -> Unit,
    onAddLong: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    showLabels: Boolean = false,
    style: NavStyle = NavStyle.AUTO
) {
    val look = LookState.look
    // Material You: the real Material navigation bar, with logging on a + button above it.
    if (look.style == LookStyle.MATERIAL && style != NavStyle.FLOATING) {
        MaterialNav(tabs, current, onTab, onAdd, onAddLong, modifier, showLabels)
        return
    }
    val docked = when (style) {
        NavStyle.DOCKED -> true
        NavStyle.FLOATING -> false
        NavStyle.AUTO -> look.style == LookStyle.PAPER || look.style == LookStyle.MATERIAL
    }
    val left = tabs.take((tabs.size + 1) / 2)
    val right = tabs.drop(left.size)

    val barShape = if (docked) RoundedCornerShape(0.dp) else RoundedCornerShape(32.dp)
    val glassy = look.style == LookStyle.GLOW || look.style == LookStyle.LIQUID
    val barSurface: Modifier = when {
        docked -> Modifier.background(if (glassy) InkBlack.copy(alpha = 0.94f) else GlassFill)
        look.style == LookStyle.GLOW -> Modifier
            // Real frosted glass: blur whatever the screen drew behind the bar…
            .hazeEffect(state = hazeState) { blurRadius = 28.dp; backgroundColor = InkBlack }
            // …then a milky fill + a bright top-lit edge so it reads as luminous glass.
            .background(Color.White.copy(alpha = 0.12f), barShape)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.12f))), barShape)
        look.style == LookStyle.LIQUID -> Modifier
            .hazeEffect(state = hazeState) { blurRadius = 22.dp; backgroundColor = InkBlack }
            .background(Color.White.copy(alpha = if (look.isLight) 0.45f else 0.14f), barShape)
            .border(
                1.2.dp,
                Brush.horizontalGradient(
                    listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.6f)),
                    startX = 300f * (0.5f - TiltState.tilt.x), endX = 900f
                ),
                barShape
            )
        else -> Modifier.background(GlassFill, barShape).border(1.dp, GlassBorder, barShape)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (docked) Modifier else Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(barShape)
                // Swallow taps that land on the bar but miss a button, so they don't fall through.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .then(barSurface)
                .then(if (docked) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = if (docked) 12.dp else 18.dp, vertical = if (showLabels) 8.dp else 14.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            left.forEach { NavItem(it, it == current, showLabels) { onTab(it) } }
            Box(Modifier.width(56.dp))
            right.forEach { NavItem(it, it == current, showLabels) { onTab(it) } }
        }

        Box(
            modifier = Modifier
                .align(if (docked) Alignment.TopCenter else Alignment.Center)
                .offset(y = if (docked) (-22).dp else (-14).dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Gold, GoldLight)))
                .combinedClickable(onClick = onAdd, onLongClick = onAddLong),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add food", tint = OnGold, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, selected: Boolean, showLabel: Boolean, onClick: () -> Unit) {
    val tint = if (selected) GoldLight else CreamMuted
    Column(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(if (showLabel) 30.dp else 40.dp), contentAlignment = Alignment.Center) {
            when (tab) {
                NavTab.HOME -> Icon(Icons.Default.Home, contentDescription = tab.label, tint = tint)
                NavTab.ANALYTICS -> Icon(painterResource(R.drawable.ic_bar_chart), contentDescription = tab.label, tint = tint)
                NavTab.COACH -> Icon(Icons.Default.AutoAwesome, contentDescription = tab.label, tint = tint)
                NavTab.COLLECTION -> Icon(Icons.Default.Bookmark, contentDescription = tab.label, tint = tint)
                NavTab.TRAIL -> Icon(Icons.Default.Landscape, contentDescription = tab.label, tint = tint)
                NavTab.SETTINGS -> Icon(Icons.Default.Settings, contentDescription = tab.label, tint = tint)
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(1.dp))
            Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaterialNav(
    tabs: List<NavTab>,
    current: NavTab?,
    onTab: (NavTab) -> Unit,
    onAdd: () -> Unit,
    onAddLong: () -> Unit,
    modifier: Modifier,
    showLabels: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.padding(end = 16.dp, bottom = 16.dp).size(56.dp)
        ) {
            Box(
                Modifier.fillMaxSize().combinedClickable(onClick = onAdd, onLongClick = onAddLong),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add food", modifier = Modifier.size(24.dp))
            }
        }
        NavigationBar(containerColor = scheme.surfaceContainer) {
            tabs.forEach { tab ->
                val selected = tab == current
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTab(tab) },
                    alwaysShowLabel = showLabels,
                    label = { Text(tab.label, maxLines = 1) },
                    icon = { MaterialTabIcon(tab, selected) }
                )
            }
        }
    }
}

/** Filled when selected, outlined otherwise — the way Pixel apps draw their tabs. */
@Composable
private fun MaterialTabIcon(tab: NavTab, selected: Boolean) {
    when (tab) {
        NavTab.HOME -> Icon(if (selected) Icons.Default.Home else Icons.Outlined.Home, contentDescription = null)
        NavTab.ANALYTICS -> Icon(painterResource(R.drawable.ic_bar_chart), contentDescription = null)
        NavTab.COACH -> Icon(if (selected) Icons.Default.AutoAwesome else Icons.Outlined.AutoAwesome, contentDescription = null)
        NavTab.COLLECTION -> Icon(if (selected) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = null)
        NavTab.TRAIL -> Icon(if (selected) Icons.Default.Landscape else Icons.Outlined.Landscape, contentDescription = null)
        NavTab.SETTINGS -> Icon(if (selected) Icons.Default.Settings else Icons.Outlined.Settings, contentDescription = null)
    }
}
