package com.fitpal.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.glassOverlay

/**
 * The small (i) that holds a card's explanation, so the card itself can stay to one short line.
 * Tapping it opens [InfoSheet]. Use it wherever a screen used to explain itself in its subtitle.
 */
@Composable
fun InfoButton(title: String, text: String, modifier: Modifier = Modifier, tint: Color = CreamMuted) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.size(32.dp).clip(CircleShape).clickable { open = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Info, contentDescription = "About $title", tint = tint, modifier = Modifier.size(18.dp))
    }
    if (open) InfoSheet(title = title, text = text, onDismiss = { open = false })
}

/** The explanation behind an [InfoButton]: a title and a few plain sentences on an overlay panel. */
@Composable
fun InfoSheet(title: String, text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().glassOverlay().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Cream)
            Spacer(Modifier.height(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Got it") }
        }
    }
}
