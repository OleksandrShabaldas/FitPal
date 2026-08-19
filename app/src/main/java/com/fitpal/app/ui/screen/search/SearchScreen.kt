package com.fitpal.app.ui.screen.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.rememberFastingGuard
import com.fitpal.app.ui.theme.AccentActivity
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassOverlay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val ROW = RoundedCornerShape(20.dp)

/**
 * Global search over everything the user has ever logged — foods and exercises — with the offline
 * typo-tolerant matcher. Opened from the pinned bar on Home. Tapping a result opens a small detail
 * sheet with a "Log again to today" button (the decided behaviour). Empty query shows recents.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<SearchResult?>(null) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val fastingGuard = rememberFastingGuard()

    // Open straight into the keyboard — search is a type-first screen.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Search", onBack = onBack)
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search anything you've logged…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )

            if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isBlank())
                            "Log a few meals or workouts and they'll show up here to search."
                        else "No matches for “${query.trim()}”. Try fewer letters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CreamMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(40.dp)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (query.isBlank()) {
                        item {
                            Text(
                                "Recently logged",
                                style = MaterialTheme.typography.labelMedium,
                                color = CreamFaint,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                    items(results, key = { it.rowKey() }) { result ->
                        HistoryResultRow(result) { selected = result }
                    }
                }
            }
        }
    }

    selected?.let { result ->
        SearchDetailDialog(
            result = result,
            onLogAgain = {
                when (result) {
                    is SearchResult.Food -> fastingGuard.attempt {
                        viewModel.logFoodAgain(result.item) {
                            android.widget.Toast.makeText(context, "Added to today", android.widget.Toast.LENGTH_SHORT).show()
                            selected = null
                        }
                    }
                    is SearchResult.Exercise -> viewModel.logExerciseAgain(result.entry) {
                        android.widget.Toast.makeText(context, "Logged to today", android.widget.Toast.LENGTH_SHORT).show()
                        selected = null
                    }
                }
            },
            onDismiss = { selected = null }
        )
    }
}

/** A stable key so the list keeps its place as the query narrows. */
private fun SearchResult.rowKey(): String = when (this) {
    is SearchResult.Food -> "f${item.id}"
    is SearchResult.Exercise -> "e${entry.id}"
}

@Composable
private fun HistoryResultRow(result: SearchResult, onClick: () -> Unit) {
    val (icon, accent, name, sub) = when (result) {
        is SearchResult.Food -> RowContent(
            icon = Icons.Default.Restaurant,
            accent = GoldLight,
            name = result.item.name,
            sub = "${result.item.calories.roundToInt()} kcal · last logged ${friendlyDate(result.lastDate)}"
        )
        is SearchResult.Exercise -> RowContent(
            icon = Icons.Default.FitnessCenter,
            accent = AccentActivity,
            name = result.entry.name,
            sub = "${result.entry.minutes} min · ${result.entry.caloriesBurned.roundToInt()} kcal · last logged ${friendlyDate(result.lastDate)}"
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().glass(ROW).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = CreamMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Bundle so the `when` above can destructure a row's pieces. */
private data class RowContent(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color,
    val name: String,
    val sub: String
)

@Composable
private fun SearchDetailDialog(
    result: SearchResult,
    onLogAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassOverlay()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            val accent = if (result is SearchResult.Exercise) AccentActivity else GoldLight
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (result is SearchResult.Exercise) Icons.Default.FitnessCenter else Icons.Default.Restaurant,
                    contentDescription = null, tint = accent, modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = when (result) {
                        is SearchResult.Food -> result.item.name
                        is SearchResult.Exercise -> result.entry.name
                    },
                    style = MaterialTheme.typography.headlineSmall, color = Cream,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Last logged ${friendlyDate(result.lastDate)}",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
            Spacer(Modifier.height(14.dp))

            when (result) {
                is SearchResult.Food -> {
                    val f = result.item
                    StatLine("Calories", "${f.calories.roundToInt()} kcal", accent)
                    StatLine("Protein", "${f.protein.roundToInt()} g")
                    StatLine("Carbs", "${f.carbs.roundToInt()} g")
                    StatLine("Fat", "${f.fat.roundToInt()} g")
                    if (f.grams > 0f) StatLine("Portion", "${f.grams.roundToInt()} ${if (f.isDrink) "ml" else "g"}")
                }
                is SearchResult.Exercise -> {
                    val e = result.entry
                    StatLine("Duration", "${e.minutes} min", accent)
                    StatLine("Calories burned", "${e.caloriesBurned.roundToInt()} kcal")
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onLogAgain,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
            ) { Text("Log again to today", fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close", color = CreamMuted)
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, valueColor: Color = Cream) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy")

/** "today" / "yesterday" / "3 Aug 2026" for a logged item's date. */
private fun friendlyDate(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val today = LocalDate.now()
    return when (date) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> date.format(DAY_MONTH_YEAR)
    }
}
