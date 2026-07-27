package com.fitpal.app.ui.screen.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MealTypeSelector
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass

@Composable
fun CustomFoodScreen(
    onLogged: () -> Unit,
    onBack: () -> Unit,
    viewModel: CustomFoodViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mealType by viewModel.mealType.collectAsStateWithLifecycle()
    val logDate by viewModel.logDate.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onLogged() }

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
            GlassTopBar(title = "Custom food", onBack = onBack)

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Type in a food and its values for the amount you ate. Only a name and calories are required.",
                    style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                )
                viewModel.barcode?.let { code ->
                    Text(
                        "Linked to barcode $code — once you log it, scanning this product again will find it automatically.",
                        style = MaterialTheme.typography.bodySmall, color = GoldLight
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().glass().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        placeholder = { Text("e.g. Horalka") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Amount (g/ml)", state.amount, viewModel::onAmount, Modifier.weight(1f))
                        NumberField("Calories (kcal)", state.calories, viewModel::onCalories, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Protein (g)", state.protein, viewModel::onProtein, Modifier.weight(1f))
                        NumberField("Fat (g)", state.fat, viewModel::onFat, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Carbs (g)", state.carbs, viewModel::onCarbs, Modifier.weight(1f))
                        NumberField("Fiber (g)", state.fiber, viewModel::onFiber, Modifier.weight(1f))
                    }
                }
                Text(
                    "Amount defaults to one serving if left blank. The calories and macros you enter are logged exactly.",
                    style = MaterialTheme.typography.labelSmall, color = CreamFaint
                )
                TextButton(onClick = { viewModel.saveToGallery() }, enabled = state.canSave) {
                    Icon(
                        if (state.savedToGallery) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = if (state.savedToGallery) GoldLight else LocalContentColor.current,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(if (state.savedToGallery) "Saved to collection" else "Save to collection")
                }
            }

            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MealTypeSelector(selected = mealType, onSelected = viewModel::setMealType)
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Logging to: ${logDateLabel(logDate)}")
                }
                Button(
                    onClick = { viewModel.log() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSave && !state.isSaving
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isSaving) "Saving…" else "Log food")
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.bodySmall, color = CreamMuted) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}
