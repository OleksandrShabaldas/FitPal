package com.fitpal.app.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.component.kit.liquidPress
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.glass

/** A small frosted-glass pill — used for the streak chip and other inline tags (a Material chip in Material You). */
@Composable
fun GlassCapsule(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    if (LookState.style == LookStyle.MATERIAL) {
        // Material You: a Material chip — 8 dp corners and an outline.
        val chip = RoundedCornerShape(8.dp)
        var m = modifier.clip(chip).border(1.dp, MaterialTheme.colorScheme.outlineVariant, chip)
        if (onClick != null) m = m.clickable(onClick = onClick)
        Row(
            modifier = m.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
        return
    }
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    var m = modifier.liquidPress(interaction).glass(shape)
    if (onClick != null) m = m.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
    Row(
        modifier = m.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}
