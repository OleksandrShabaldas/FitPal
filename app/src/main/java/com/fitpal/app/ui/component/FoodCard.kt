package com.fitpal.app.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.glassSoft

/**
 * A frosted-glass row showing a single food item — name, weight, calories, and an
 * optional thumbnail. Tap = onClick, long-press = onLongClick.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodCard(
    name: String,
    grams: Float,
    calories: Float,
    modifier: Modifier = Modifier,
    unit: String = "g",
    photoPath: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val clickModifier = when {
        onLongClick != null -> Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }
    Row(
        modifier = modifier.fillMaxWidth().glassSoft().then(clickModifier).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!photoPath.isNullOrBlank()) {
            AsyncImage(
                model = photoPath,
                contentDescription = name,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${grams.toInt()}$unit", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${calories.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CalorieColor)
            Text("kcal", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
        }
    }
}
