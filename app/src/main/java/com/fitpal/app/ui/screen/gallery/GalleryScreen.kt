package com.fitpal.app.ui.screen.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.FoodCard
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight

@Composable
fun GalleryScreen(
    onLogged: () -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit = {},
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val foods by viewModel.foods.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val logged by viewModel.logged.collectAsStateWithLifecycle()
    val logDate by viewModel.logDate.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(logged) { if (logged) onLogged() }

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = logDate,
            title = "Log to which day?",
            confirmLabel = "Log here",
            onConfirm = { date -> showDatePicker = false; viewModel.setLogDate(date) },
            onDismiss = { showDatePicker = false }
        )
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Saved foods", onBack = onBack)

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                placeholder = { Text("Search saved foods...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Logging to: ${logDateLabel(logDate)}")
            }

            if (foods.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Your food gallery is empty" else "No foods matching \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge, color = CreamMuted
                    )
                    if (searchQuery.isBlank()) {
                        Text(
                            "Save foods after scanning to build it up",
                            style = MaterialTheme.typography.bodyMedium, color = CreamFaint,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                Text(
                    "Tap a food to open it · use + to log it instantly",
                    style = MaterialTheme.typography.labelMedium, color = CreamMuted,
                    modifier = Modifier.padding(start = 20.dp, bottom = 2.dp)
                )
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(foods, key = { it.id }) { food ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FoodCard(
                                name = food.name,
                                grams = food.defaultPortionGrams,
                                calories = food.totalCalories,
                                unit = if (food.isDrink) "ml" else "g",
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenDetail(food.id) }
                            )
                            IconButton(onClick = { viewModel.quickLog(food.id) }) {
                                Icon(Icons.Default.Add, contentDescription = "Log ${food.name}", tint = GoldLight)
                            }
                            IconButton(onClick = { viewModel.deleteFood(food) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${food.name}", tint = CreamMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
