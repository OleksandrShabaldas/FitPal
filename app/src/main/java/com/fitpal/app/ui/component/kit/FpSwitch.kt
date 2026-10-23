package com.fitpal.app.ui.component.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.filmColor

/*
 * FitPal's look kit: components that are drawn *structurally* differently per look, not just in other
 * colours (docs/redesign/11). Glow and Paper always take the original component unchanged.
 */

/**
 * A switch: Material's in Glow and Paper (as before), an iPhone switch in Liquid glass (a wide pill
 * that fills with the accent, a white knob that springs across), and Material You's own switch with
 * a check in its thumb.
 */
@Composable
fun FpSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    when (LookState.style) {
        LookStyle.LIQUID -> LiquidSwitch(checked, onCheckedChange, modifier, enabled)
        LookStyle.MATERIAL -> Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            thumbContent = if (checked) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else null
        )
        else -> Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
    }
}

@Composable
private fun LiquidSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean) {
    val bouncy = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.62f, stiffness = 520f)
    val knobX by animateDpAsState(if (checked) 22.dp else 2.dp, animationSpec = bouncy, label = "knob")
    val track by animateColorAsState(if (checked) GoldLight else filmColor(0.16f), label = "track")
    val toggle = if (onCheckedChange != null) Modifier.toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onValueChange = onCheckedChange
    ) else Modifier
    Box(
        modifier = modifier
            .then(toggle)
            .padding(vertical = 8.dp)
            .size(width = 51.dp, height = 31.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .background(track, RoundedCornerShape(50)),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = knobX)
                .size(27.dp)
                .shadow(3.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .background(Color.White, CircleShape)
        )
    }
}
