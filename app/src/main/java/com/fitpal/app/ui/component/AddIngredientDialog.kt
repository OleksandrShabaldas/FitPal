package com.fitpal.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fitpal.app.data.local.entity.UsdaFoodEntity

/**
 * Searches the built-in simple (non-branded) food database and lets the user pick an ingredient,
 * with an "Add with AI" escape hatch to describe something that isn't in the list. Shared by the
 * meal-detail editor and the pre-log Analysis / Describe screens.
 */
@Composable
fun AddIngredientDialog(
    query: String,
    results: List<UsdaFoodEntity>,
    isDrink: Boolean,
    aiLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onPick: (UsdaFoodEntity) -> Unit,
    onAiAdd: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Dialog title — e.g. "Replace ingredient" when swapping one out. */
    title: String = "Add ingredient",
    /** Verb for the AI escape-hatch button, e.g. "Add" or "Use". */
    aiVerb: String = "Add"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search the database") },
                    singleLine = true
                )

                // AI escape hatch — describe an ingredient that isn't in the simple database.
                Spacer(Modifier.height(8.dp))
                when {
                    aiLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Asking the AI…", style = MaterialTheme.typography.bodyMedium)
                    }
                    query.isNotBlank() -> TextButton(onClick = { onAiAdd(query) }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("$aiVerb \"${query.take(24)}\" with AI")
                    }
                }

                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(results, key = { it.fdcId }) { food ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(food) }.padding(vertical = 8.dp)
                        ) {
                            Text(food.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${food.caloriesPer100g.toInt()} kcal / 100${if (isDrink) "ml" else "g"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
