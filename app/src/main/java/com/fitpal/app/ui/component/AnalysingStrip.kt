package com.fitpal.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass

/**
 * The compact "the AI is on it" strip shown above the "While the AI works" card: a spinner, the live
 * progress line, and which model is answering.
 */
@Composable
fun AnalysingStrip(message: String, source: AiSource?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().glass().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = GoldLight)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                message.ifBlank { "Analysing…" },
                style = MaterialTheme.typography.bodyMedium, color = Cream
            )
            if (source != null) {
                Spacer(Modifier.height(6.dp))
                AiSourceBadge(source)
            }
        }
    }
}
