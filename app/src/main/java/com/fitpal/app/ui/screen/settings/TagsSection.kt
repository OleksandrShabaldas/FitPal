package com.fitpal.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.model.TagItem
import com.fitpal.app.domain.model.TagSettings
import com.fitpal.app.ui.component.RenameDialog
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glass
import sh.calvin.reorderable.ReorderableColumn

/**
 * Settings → Logging & meal times → Situation tags (the 17 Nov review, #4): the two rows of tags a meal can
 * carry — where and who with, and how you felt. Drag the handle to reorder, tap a name to rename it (new meals
 * only; a meal keeps the words it was logged with), the eye hides a tag from the panel, × deletes one of your
 * own (built-in tags can only be hidden). Up to [TagSettings.MAX_PER_ROW] per row.
 */
@Composable
internal fun TagsSection(viewModel: SettingsViewModel) {
    val tags by viewModel.tagSettings.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Pair<Boolean, TagItem>?>(null) }  // (feeling?, tag)
    var adding by remember { mutableStateOf<Boolean?>(null) }                    // feeling?

    fun row(feeling: Boolean) = if (feeling) tags.feelings else tags.situation
    fun save(feeling: Boolean, list: List<TagItem>) =
        viewModel.setTagSettings(if (feeling) tags.copy(feelings = list) else tags.copy(situation = list))

    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Situation tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "The tags you can put on a meal. Your coach reads them to understand your days.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOf(false to "Where and who with", true to "How you felt").forEach { (feeling, title) ->
            Spacer(Modifier.height(14.dp))
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = CreamFaint)
            Spacer(Modifier.height(4.dp))
            TagList(
                items = row(feeling),
                onMove = { from, to -> save(feeling, row(feeling).toMutableList().apply { add(to, removeAt(from)) }) },
                onRename = { renaming = feeling to it },
                onToggleHidden = { t -> save(feeling, row(feeling).map { if (it.name == t.name) it.copy(hidden = !it.hidden) else it }) },
                onDelete = { t -> save(feeling, row(feeling).filterNot { it.name == t.name }) }
            )
            if (row(feeling).size < TagSettings.MAX_PER_ROW) {
                Text(
                    "+ Add a tag", style = MaterialTheme.typography.labelLarge, color = GoldLight,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { adding = feeling }.padding(vertical = 10.dp, horizontal = 4.dp)
                )
            }
        }
    }

    renaming?.let { (feeling, tag) ->
        RenameDialog(
            currentName = tag.name,
            title = "Rename this tag",
            label = "Tag",
            hint = "Meals already logged keep the name they have.",
            onConfirm = { name ->
                val clean = name.trim().take(TagSettings.MAX_NAME)
                val taken = (tags.situation + tags.feelings).any { it.name.equals(clean, ignoreCase = true) && !it.name.equals(tag.name, ignoreCase = true) }
                if (clean.isNotEmpty() && !taken) save(feeling, row(feeling).map { if (it.name == tag.name) it.copy(name = clean) else it })
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }
    adding?.let { feeling ->
        RenameDialog(
            currentName = "",
            title = if (feeling) "New feeling" else "New tag",
            label = "Tag",
            placeholder = if (feeling) "e.g. Anxious" else "e.g. Gym day",
            onConfirm = { name ->
                if (tags.canAdd(name)) save(feeling, (row(feeling) + TagItem(name.trim())).take(TagSettings.MAX_PER_ROW))
                adding = null
            },
            onDismiss = { adding = null }
        )
    }
}

/** One row's tags, reorderable by their handles (sh.calvin.reorderable tracks the finger and settles the drop). */
@Composable
private fun TagList(
    items: List<TagItem>,
    onMove: (from: Int, to: Int) -> Unit,
    onRename: (TagItem) -> Unit,
    onToggleHidden: (TagItem) -> Unit,
    onDelete: (TagItem) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    ReorderableColumn(
        list = items,
        onSettle = { from, to -> if (from != to) onMove(from, to) },
        onMove = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) { _, tag, dragging ->
        key(tag.name) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (dragging) Modifier.background(filmColor(0.10f)) else Modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DragHandle, contentDescription = "Move ${tag.name}", tint = CreamFaint,
                    // 44 dp of hit area around a small handle.
                    modifier = Modifier.draggableHandle(
                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                    ).size(44.dp).padding(12.dp)
                )
                Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onRename(tag) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tag.name, style = MaterialTheme.typography.bodyLarge,
                        color = if (tag.hidden) CreamFaint else Cream,
                        textDecoration = if (tag.hidden) TextDecoration.LineThrough else null,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!tag.builtIn) Text("  yours", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
                }
                Icon(
                    if (tag.hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (tag.hidden) "Show ${tag.name}" else "Hide ${tag.name}", tint = CreamMuted,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onToggleHidden(tag) }.padding(12.dp)
                )
                if (!tag.builtIn) {
                    Icon(
                        Icons.Default.Close, contentDescription = "Delete ${tag.name}", tint = CreamMuted,
                        modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onDelete(tag) }.padding(12.dp)
                    )
                }
            }
        }
    }
}
