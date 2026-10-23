package com.fitpal.app.ui.component.kit

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The collapse state of a screen's top bar (Liquid glass's large title, Material You's collapsing
 * app bar). Every screen's GradientBackdrop owns one and listens to the scrolling inside it, so the
 * bar collapses as the content scrolls up and grows back at the top — the bar and the list never
 * need to know about each other. Null in Glow and Paper (their bar doesn't collapse).
 */
@OptIn(ExperimentalMaterial3Api::class)
val LocalTopBarScroll = staticCompositionLocalOf<TopAppBarScrollBehavior?> { null }

/**
 * A fresh behaviour for one screen. The collapse range starts at zero, so a screen without a
 * collapsing bar never has its scrolling taken; the bar sets the range when it's drawn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberTopBarScroll(): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState(initialHeightOffsetLimit = 0f))
