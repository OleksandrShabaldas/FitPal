package com.fitpal.app.ui.screen.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.repository.CoachMemoryRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.model.CoachMemoryParser
import com.fitpal.app.domain.model.MemoryLine
import com.fitpal.app.domain.model.MemorySection
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.SetupPill
import com.fitpal.app.ui.component.TrackerSheet
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.OverlaySurface
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class CoachMemoryViewModel @Inject constructor(
    private val memory: CoachMemoryRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    val lines: StateFlow<List<MemoryLine>> = memory.lines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** Profile → About your situation, shown as "In your own words". */
    val ownWords: StateFlow<String> = settings.personalContext

    fun add(section: MemorySection, text: String) = viewModelScope.launch { memory.add(section, text) }
    fun edit(id: Long, text: String) = viewModelScope.launch { memory.edit(id, text) }
    fun remove(line: MemoryLine) = viewModelScope.launch { memory.remove(line.id) }
    fun restore(line: MemoryLine) = viewModelScope.launch { memory.restore(line) }
    fun setOwnWords(text: String) = settings.setPersonalContext(text)
}

/**
 * "What your coach knows" (the 17 Nov review, #2): the coach memory, readable and editable. Your own words
 * (Profile → About your situation) on top, then each section with its lines — the date each came from, or
 * "yours" for a line you wrote or edited (the coach never changes those). ⋯ on a line edits or removes it
 * (with Undo); "+ Add something yourself" adds a line of your own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachMemoryScreen(onBack: () -> Unit, viewModel: CoachMemoryViewModel = hiltViewModel()) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val ownWords by viewModel.ownWords.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<MemoryLine?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editingOwn by remember { mutableStateOf(false) }

    fun remove(line: MemoryLine) {
        viewModel.remove(line)
        scope.launch {
            if (snackbar.showSnackbar("Removed", actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                viewModel.restore(line)
            }
        }
    }

    GradientBackdrop(theme = BackdropTheme.TRENDS) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                GlassTopBar(title = "What your coach knows", onBack = onBack, marquee = true)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Built from your answers to your coach. Edit or remove anything.",
                        style = MaterialTheme.typography.bodySmall, color = CreamMuted
                    )
                    Column(Modifier.fillMaxWidth().glass().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("In your own words")
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { editingOwn = true }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                ownWords.ifBlank { "Anything your coach should always keep in mind — your situation, limits, what matters to you." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (ownWords.isBlank()) CreamFaint else Cream,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Edit", style = MaterialTheme.typography.labelLarge, color = GoldLight, modifier = Modifier.padding(start = 10.dp))
                        }
                        if (lines.isEmpty()) {
                            Divider()
                            Text(
                                "Nothing else yet. Your coach adds to this after your overviews, from your answers.",
                                style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                            )
                        }
                        MemorySection.entries.forEach { section ->
                            val inSection = lines.filter { it.section == section }
                            if (inSection.isEmpty()) return@forEach
                            Divider()
                            SectionLabel(section.title)
                            inSection.forEach { line -> MemoryRow(line, onEdit = { editing = line }, onRemove = { remove(line) }) }
                        }
                    }
                    Text(
                        "+ Add something yourself", style = MaterialTheme.typography.labelLarge, color = GoldLight,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { adding = true }.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { data ->
                androidx.compose.material3.Snackbar(
                    snackbarData = data, containerColor = OverlaySurface, contentColor = Cream,
                    actionColor = GoldLight, shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }

    editing?.let { line ->
        LineSheet(
            title = "Edit", initial = line.text, section = null,
            onSave = { text, _ -> viewModel.edit(line.id, text); editing = null },
            onDismiss = { editing = null }
        )
    }
    if (adding) {
        LineSheet(
            title = "Add something yourself", initial = "", section = MemorySection.LIFE,
            onSave = { text, section -> viewModel.add(section ?: MemorySection.LIFE, text); adding = false },
            onDismiss = { adding = false }
        )
    }
    if (editingOwn) {
        var text by remember { mutableStateOf(ownWords) }
        TrackerSheet(title = "In your own words", onDismiss = { editingOwn = false }) {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(600) },
                placeholder = { Text("e.g. I work 12-hour shifts on Tuesdays and Thursdays") },
                minLines = 3, maxLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Text("The same as Profile & goals → About your situation.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            GoldButton("Save", onClick = { viewModel.setOwnWords(text); editingOwn = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** One line: what the coach knows, when it came from (or "yours"), and a ⋯ to edit or remove it. */
@Composable
private fun MemoryRow(line: MemoryLine, onEdit: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        DatePill(line)
        Spacer(Modifier.width(8.dp))
        Text(line.text, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f).padding(top = 1.dp))
        Box {
            Icon(
                Icons.Default.MoreHoriz, contentDescription = "More for this line", tint = CreamFaint,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { menu = true }.padding(6.dp)
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun DatePill(line: MemoryLine) {
    val label = if (line.mine) "yours" else runCatching {
        LocalDate.parse(line.sourceDate).format(DateTimeFormatter.ofPattern("d MMM"))
    }.getOrDefault(line.sourceDate)
    val color = if (line.mine) ScoreFair else CreamFaint
    Text(
        label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = color, maxLines = 1,
        modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(50)).background(filmColor(0.06f)).padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = GoldLight, letterSpacing = 1.1.sp)
}

@Composable
private fun Divider() = androidx.compose.material3.HorizontalDivider(color = filmColor(0.08f))

/** Edit a line, or write a new one (then [section] picks where it goes). Saving makes it yours. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineSheet(
    title: String,
    initial: String,
    section: MemorySection?,
    onSave: (String, MemorySection?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var picked by remember { mutableStateOf(section) }
    TrackerSheet(title = title, onDismiss = onDismiss) {
        if (section != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MemorySection.entries.forEach { s -> SetupPill(s.title, selected = picked == s) { picked = s } }
            }
        }
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(CoachMemoryParser.MAX_LINE) },
            placeholder = { Text("One plain sentence") },
            minLines = 2, maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Your coach reads this but won't change it.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        Spacer(Modifier.height(2.dp))
        GoldButton("Save", onClick = { onSave(text, picked) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth())
    }
}
