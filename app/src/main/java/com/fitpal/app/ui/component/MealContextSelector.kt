package com.fitpal.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The fixed set of one-tap meal situations. Curated + short so tagging stays instant. */
val MEAL_CONTEXTS = listOf(
    "Home", "Restaurant", "Family meal", "Work/school", "Social", "On the go", "Travel"
)

/**
 * Optional one-tap "where/what situation was this meal" picker. Single-select; tapping the selected
 * chip again clears it. Feeds the AI review so it can reason about *why* a day looked unusual.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealContextSelector(
    selected: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MEAL_CONTEXTS.forEach { tag ->
            FilterChip(
                selected = selected == tag,
                onClick = { onSelected(tag) },
                label = { Text(tag) }
            )
        }
    }
}
