package com.fitpal.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A short, friendly label for a log date: "Today", "Yesterday", "Tomorrow", else "Mon 16 Jun". */
fun logDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "Today"
        -1L -> "Yesterday"
        1L -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
}

/**
 * A simple, offline month-grid date picker styled like the rest of the app (matches the Home
 * calorie calendar). Tapping a day selects it; the confirm button applies the choice. Used by
 * "log to another date" on the logging screens and "copy to another date" on logged entries.
 */
@Composable
fun DatePickerDialog(
    initialDate: LocalDate,
    title: String = "Select a date",
    confirmLabel: String = "Select",
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    /** When true, also show a Breakfast/Lunch/Dinner/Snack picker and report the choice. */
    mealTypeChooser: Boolean = false,
    initialMealType: String = "",
    onConfirmMeal: (LocalDate, String, Int) -> Unit = { _, _, _ -> },
    /** When true, show a "how many copies" stepper (min 1); the count is passed to the confirms. */
    copiesChooser: Boolean = false,
    /** Copies-aware confirm for pickers WITHOUT the meal chooser (e.g. exercise copy). */
    onConfirmCopies: ((LocalDate, Int) -> Unit)? = null
) {
    var selected by remember { mutableStateOf(initialDate) }
    var viewMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var selectedMeal by remember { mutableStateOf(initialMealType.ifEmpty { com.fitpal.app.domain.model.MealTypes.ALL.first() }) }
    var copies by remember { mutableStateOf(1) }
    val today = LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewMonth = viewMonth.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous month")
                    }
                    Text(
                        text = "${viewMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${viewMonth.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewMonth = viewMonth.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next month")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { name ->
                        Text(
                            text = name,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val firstOfMonth = viewMonth.atDay(1)
                val startOffset = (firstOfMonth.dayOfWeek.value - 1)
                val daysInMonth = viewMonth.lengthOfMonth()
                val cells = mutableListOf<LocalDate?>()
                repeat(startOffset) { cells.add(null) }
                for (d in 1..daysInMonth) cells.add(viewMonth.atDay(d))
                while (cells.size < 42) cells.add(null)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    userScrollEnabled = false
                ) {
                    items(cells) { date ->
                        if (date == null) {
                            Spacer(Modifier.aspectRatio(1f))
                        } else {
                            DayCell(
                                day = date.dayOfMonth,
                                isSelected = date == selected,
                                isToday = date == today,
                                onClick = { selected = date }
                            )
                        }
                    }
                }
                // One-tap jump back to today (also snaps the calendar to the current month).
                TextButton(onClick = { selected = today; viewMonth = YearMonth.from(today) }) {
                    Text("Today")
                }
                if (copiesChooser) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Copies",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { if (copies > 1) copies-- }, enabled = copies > 1) {
                            Icon(Icons.Default.Remove, contentDescription = "Fewer copies")
                        }
                        Text(
                            "$copies",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(36.dp)
                        )
                        IconButton(onClick = { if (copies < 20) copies++ }, enabled = copies < 20) {
                            Icon(Icons.Default.Add, contentDescription = "More copies")
                        }
                    }
                }
                if (mealTypeChooser) {
                    Spacer(Modifier.height(4.dp))
                    Text("Meal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    MealTypeSelector(selected = selectedMeal, onSelected = { selectedMeal = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    mealTypeChooser -> onConfirmMeal(selected, selectedMeal, copies)
                    onConfirmCopies != null -> onConfirmCopies(selected, copies)
                    else -> onConfirm(selected)
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
