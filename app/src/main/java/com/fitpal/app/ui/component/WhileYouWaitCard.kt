package com.fitpal.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.EatenAt
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import java.time.LocalTime

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

/**
 * The details only the user knows — which meal, when they ate, and the situation — offered while the
 * AI is still reading the photo or description, so the wait isn't dead time. Everything set here is
 * saved with the meal, including when it's auto-saved because the user left the app. [showMealType]
 * is off where the screen already shows its own meal picker.
 */
@Composable
fun WhileYouWaitCard(
    mealType: String,
    onMealType: (String) -> Unit,
    eatenAt: EatenAt,
    onPickTime: (LocalTime) -> Unit,
    tags: Set<String>,
    onToggleTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    onUsePhotoSuggestion: (() -> Unit)? = null,
    showMealType: Boolean = true
) {
    // A short beat after the progress appears, the card slides in — it reads as "meanwhile…".
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 6 },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().glass().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = GoldLight, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("While the AI works", style = MaterialTheme.typography.headlineSmall, color = Cream)
            }
            Text(
                "Add what only you know — it's saved with the meal.",
                style = MaterialTheme.typography.bodySmall,
                color = CreamMuted
            )
            Spacer(Modifier.height(6.dp))
            if (showMealType) {
                Text("Which meal", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                MealTypeSelector(selected = mealType, onSelected = onMealType)
                Spacer(Modifier.height(6.dp))
            }
            Text("When you ate", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
            EatenAtChip(
                eatenAt = eatenAt,
                onPick = onPickTime,
                prominent = true,
                onUsePhotoSuggestion = onUsePhotoSuggestion
            )
            Spacer(Modifier.height(6.dp))
            Text("Where & who with (optional)", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
            MealContextSelector(selected = tags, onToggle = onToggleTag)
        }
    }
}
