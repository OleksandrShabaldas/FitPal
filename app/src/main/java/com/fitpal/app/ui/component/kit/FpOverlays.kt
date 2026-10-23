package com.fitpal.app.ui.component.kit

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.OverlaySurface

/**
 * Liquid glass's overlays are frosted: the dialog or sheet window blurs whatever is behind it (the
 * system's cross-window blur, Android 12+), so a translucent panel reads as glass over the page.
 * Call it inside the overlay's content. Returns whether the blur is on — it isn't on Android 11 and
 * older, or while the phone has turned blurs off (battery saver), and then the panel stays opaque.
 */
@Composable
fun liquidWindowBlur(radiusPx: Int = 60): Boolean {
    if (LookState.style != LookStyle.LIQUID || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val view = LocalView.current
    val window = remember(view) { findDialogWindow(view) } ?: return false
    val wm = view.context.getSystemService(WindowManager::class.java)
    val enabled = wm?.isCrossWindowBlurEnabled == true
    DisposableEffect(window, enabled) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = radiusPx }
        }
        onDispose { }
    }
    return enabled
}

private fun findDialogWindow(view: View): android.view.Window? {
    var v: Any? = view
    while (v is View) {
        if (v is DialogWindowProvider) return v.window
        v = v.parent
    }
    return null
}

/**
 * A press that gives a little, the way iOS controls do: shrinks a touch while held and springs back
 * with a small overshoot. Liquid glass only (and not with Reduce motion); a no-op elsewhere.
 */
@Composable
fun Modifier.liquidPress(interactionSource: MutableInteractionSource): Modifier {
    val look = LookState.look
    if (look.style != LookStyle.LIQUID || look.appearance.reduceMotion) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "press"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Material's AlertDialog, per look: unchanged in Glow, Paper and Material You (whose theme already
 * makes it a Pixel dialog); in Liquid glass a frosted panel over the blurred page.
 */
@Composable
fun FpAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    if (LookState.style != LookStyle.LIQUID) {
        AlertDialog(
            onDismissRequest, confirmButton, modifier, dismissButton, icon, title, text, shape, containerColor,
            iconContentColor, titleContentColor, textContentColor, tonalElevation, properties
        )
        return
    }
    // Liquid glass: the confirm button's slot also switches the window's blur on (it's always
    // there); the panel turns translucent only when the phone really blurs.
    val context = LocalContext.current
    val blurOn = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    }
    val confirmWithBlur: @Composable () -> Unit = {
        liquidWindowBlur()
        confirmButton()
    }
    AlertDialog(
        onDismissRequest, confirmWithBlur, modifier, dismissButton, icon, title, text, shape,
        if (blurOn) OverlaySurface.copy(alpha = 0.80f) else containerColor,
        iconContentColor, titleContentColor, textContentColor, tonalElevation, properties
    )
}
