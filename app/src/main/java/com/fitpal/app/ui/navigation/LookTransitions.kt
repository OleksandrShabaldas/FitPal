package com.fitpal.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavBackStackEntry
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle

/*
 * How screens change, per look:
 *  - Glow and Paper: Navigation's own default, a 700 ms cross-fade (unchanged).
 *  - Liquid glass: an iPhone push — the new page slides in from the edge on a slightly bouncy
 *    spring while the old one drifts back; a quick fade between tabs.
 *  - Material You: a new page grows out of what you tapped (Material's container-transform feel),
 *    on Material's emphasized easing; tabs fade through.
 */
internal object LookTransitions {
    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val push = spring<IntOffset>(dampingRatio = 0.86f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold)

    /** Where the last tap landed, as a fraction of the screen — Material's pages grow from there. */
    private var tapOrigin = TransformOrigin.Center

    /** Watches taps (without taking them) so Material You knows where a page should grow from. */
    fun Modifier.watchTaps(): Modifier = this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.changedToDown() }?.let { tapOrigin = origin(it.position, size) }
            }
        }
    }

    private fun origin(p: Offset, size: IntSize) =
        if (size.width == 0 || size.height == 0) TransformOrigin.Center
        else TransformOrigin((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(tabRoutes: Set<String>) =
        initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>, tabRoutes: Set<String>, pop: Boolean): EnterTransition =
        when (LookState.style) {
            LookStyle.LIQUID -> when {
                scope.betweenTabs(tabRoutes) -> fadeIn(tween(180))
                pop -> slideInHorizontally(push) { -it / 4 }
                else -> slideInHorizontally(push) { it }
            }
            LookStyle.MATERIAL -> when {
                scope.betweenTabs(tabRoutes) ->
                    fadeIn(tween(210, delayMillis = 90, easing = EmphasizedDecelerate)) +
                        scaleIn(tween(210, delayMillis = 90, easing = EmphasizedDecelerate), initialScale = 0.92f)
                pop -> fadeIn(tween(300, easing = EmphasizedDecelerate))
                else -> fadeIn(tween(250, easing = EmphasizedDecelerate)) +
                    scaleIn(tween(400, easing = EmphasizedDecelerate), initialScale = 0.82f, transformOrigin = tapOrigin)
            }
            else -> fadeIn(animationSpec = tween(700))
        }

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>, tabRoutes: Set<String>, pop: Boolean): ExitTransition =
        when (LookState.style) {
            LookStyle.LIQUID -> when {
                scope.betweenTabs(tabRoutes) -> fadeOut(tween(180))
                pop -> slideOutHorizontally(push) { it }
                else -> slideOutHorizontally(push) { -it / 4 }
            }
            LookStyle.MATERIAL -> when {
                scope.betweenTabs(tabRoutes) -> fadeOut(tween(90, easing = EmphasizedAccelerate))
                pop -> fadeOut(tween(200, easing = EmphasizedAccelerate)) +
                    scaleOut(tween(300, easing = EmphasizedAccelerate), targetScale = 0.88f)
                else -> fadeOut(tween(250, easing = EmphasizedAccelerate))
            }
            else -> fadeOut(animationSpec = tween(700))
        }
}
