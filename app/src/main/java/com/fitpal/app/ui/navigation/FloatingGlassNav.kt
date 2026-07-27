package com.fitpal.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InkBlack
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/** Floating frosted-glass tab bar with a raised gold "+" in the centre. */
@Composable
fun FloatingGlassNav(
    isHome: Boolean,
    isAnalytics: Boolean,
    isCollection: Boolean,
    isSettings: Boolean,
    onHome: () -> Unit,
    onAnalytics: () -> Unit,
    onCollection: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                // Swallow taps that land on the bar but miss a button, so they don't fall through
                // to whatever content sits behind the floating bar.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                // Real frosted glass: blur whatever the screen drew behind the bar…
                .hazeEffect(state = hazeState) {
                    blurRadius = 28.dp
                    backgroundColor = InkBlack
                }
                // …then a light, milky "liquid glass" fill + a bright top-lit edge so the
                // pill reads as luminous frosted glass rather than blending into the dark.
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.12f))
                    ),
                    RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(isHome, onHome) { tint ->
                Icon(Icons.Default.Home, contentDescription = "Home", tint = tint)
            }
            NavItem(isAnalytics, onAnalytics) { tint ->
                Icon(painterResource(R.drawable.ic_bar_chart), contentDescription = "Analytics", tint = tint)
            }
            Box(Modifier.width(48.dp))
            NavItem(isCollection, onCollection) { tint ->
                Icon(Icons.Default.Bookmark, contentDescription = "Collection", tint = tint)
            }
            NavItem(isSettings, onSettings) { tint ->
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = tint)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-14).dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Gold, GoldLight)))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add food",
                tint = Color(0xFF3A2406),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun NavItem(selected: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val tint = if (selected) GoldLight else CreamMuted
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon(tint)
    }
}
