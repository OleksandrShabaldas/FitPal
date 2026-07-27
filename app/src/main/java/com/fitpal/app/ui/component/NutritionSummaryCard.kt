package com.fitpal.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitpal.app.domain.model.NutritionInfo
import com.fitpal.app.ui.theme.CalorieColor

/**
 * Card showing today's calorie total prominently, with a macro breakdown bar underneath.
 * This is the main element at the top of the Home screen.
 */
@Composable
fun NutritionSummaryCard(
    nutrition: NutritionInfo,
    modifier: Modifier = Modifier,
    dailyGoal: Int = 0,
    title: String = "Today"
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Big calorie number
            Text(
                text = "${nutrition.calories.toInt()}",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = CalorieColor
            )

            // Show goal + remaining if a goal is set, otherwise just "kcal"
            if (dailyGoal > 0) {
                val remaining = (dailyGoal - nutrition.calories.toInt()).coerceAtLeast(0)
                Text(
                    text = "of $dailyGoal kcal · $remaining left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            } else {
                Text(
                    text = "kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Macro breakdown
            MacroBar(
                protein = nutrition.protein,
                fat = nutrition.fat,
                carbs = nutrition.carbs,
                fiber = nutrition.fiber
            )
        }
    }
}
