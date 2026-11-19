package com.fitpal.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.TagSettings
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight

/**
 * The user's meal tags and how to add one, for every meal panel (the 17 Nov review, #4). Filled once by
 * `FitPalActivity` from `SettingsRepository.tagSettings`, like the AI model slots, so the screens don't
 * each have to carry them.
 */
data class MealTagLists(
    val settings: TagSettings = TagSettings(),
    /** Add a new tag to a row ([feeling] = the "How you felt" row) at its end. */
    val add: (name: String, feeling: Boolean) -> Unit = { _, _ -> }
)

val LocalMealTags = compositionLocalOf { MealTagLists() }

/**
 * Optional one-tap tags for a meal, in two rows: **where and who with** and **how you felt**. Multi-select;
 * tapping a selected chip clears it. The rows are the user's own (Settings → Logging & meal times →
 * Situation tags); a tag the meal already has that isn't on the list any more (renamed, hidden or deleted)
 * still shows, selected, so it can be taken off. Each row ends with "+ New", which adds a tag to that row
 * and puts it on this meal. Feeds the AI coach, which reads both.
 *
 * Custom compact pills (not Material's chunky FilterChip) so a row fits tightly and matches the glass look.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealContextSelector(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** The "How you felt" row and its label; the caller labels the first row. */
    feelingsLabel: (@Composable () -> Unit)? = null
) {
    val lists = LocalMealTags.current
    val tags = lists.settings
    var adding by remember { mutableStateOf<Boolean?>(null) } // null = closed, true = a feeling, false = situation
    val known = (tags.situation + tags.feelings).map { it.name.lowercase() }.toSet()
    // Tags on this meal that aren't in either list any more go with the situation row.
    val strays = selected.filter { it.lowercase() !in known }

    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        TagRow(
            names = tags.shownSituation + tags.situation.filter { it.hidden && it.name in selected }.map { it.name } + strays,
            selected = selected, onToggle = onToggle,
            canAdd = tags.situation.size < TagSettings.MAX_PER_ROW, onAdd = { adding = false }
        )
        if (feelingsLabel != null) {
            Spacer(Modifier.height(10.dp))
            feelingsLabel()
            Spacer(Modifier.height(8.dp))
            TagRow(
                names = tags.shownFeelings + tags.feelings.filter { it.hidden && it.name in selected }.map { it.name },
                selected = selected, onToggle = onToggle,
                canAdd = tags.feelings.size < TagSettings.MAX_PER_ROW, onAdd = { adding = true }
            )
        }
    }

    adding?.let { feeling ->
        NewTagSheet(
            feeling = feeling,
            canUse = tags::canAdd,
            onAdd = { name ->
                lists.add(name, feeling)
                if (name !in selected) onToggle(name)
                adding = null
            },
            onDismiss = { adding = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagRow(names: List<String>, selected: Set<String>, onToggle: (String) -> Unit, canAdd: Boolean, onAdd: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        names.distinct().forEach { tag -> ContextChip(tag = tag, selected = tag in selected, onClick = { onToggle(tag) }) }
        if (canAdd) NewChip(onAdd)
    }
}

/** "+ New": a dashed gold pill at the end of a row. */
@Composable
private fun NewChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = "+ New",
        style = MaterialTheme.typography.labelMedium,
        color = GoldLight,
        modifier = Modifier
            .clip(shape)
            .border(BorderStroke(1.dp, GoldLight.copy(alpha = 0.45f)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** A small sheet to name a new tag; it joins the end of its row (and the list in Settings). */
@Composable
private fun NewTagSheet(feeling: Boolean, canUse: (String) -> Boolean, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val clean = name.trim()
    val ok = canUse(clean)
    TrackerSheet(title = if (feeling) "New feeling" else "New tag", onDismiss = onDismiss) {
        androidx.compose.material3.OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= TagSettings.MAX_NAME) name = it },
            label = { Text("Name") },
            placeholder = { Text(if (feeling) "e.g. Anxious" else "e.g. Gym day") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        if (clean.isNotEmpty() && !ok) {
            Text("You already have that one.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
        Spacer(Modifier.height(4.dp))
        GoldButton("Add", onClick = { if (ok) onAdd(clean) }, enabled = ok, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ContextChip(tag: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = if (selected) {
        Modifier.background(GoldLight.copy(alpha = 0.16f), shape)
            .border(BorderStroke(1.dp, GoldLight.copy(alpha = 0.55f)), shape)
    } else {
        Modifier.border(BorderStroke(1.dp, CreamMuted.copy(alpha = 0.35f)), shape)
    }
    Text(
        text = tag,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) GoldLight else CreamMuted,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(shape)
            .then(base)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
