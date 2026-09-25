package com.fitpal.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.EatenAt
import com.fitpal.app.domain.model.TimeSource
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glassSoft
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * "When did you eat this?" — the small, deliberately quiet time control on every logging screen
 * (and on a logged meal). Shows "Eaten just now", a picked time, or the photo's own capture time
 * ("7:40 PM · from photo"); tapping opens a time picker. [prominent] is the larger pill used in the
 * "While the AI works" card, where there's time to set it. [onUsePhotoSuggestion] offers an older
 * photo's time in one tap when it wasn't applied on its own.
 */
@Composable
fun EatenAtChip(
    eatenAt: EatenAt,
    onPick: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    onUsePhotoSuggestion: (() -> Unit)? = null,
    prominent: Boolean = false,
    /** What "no time set" means here — "just now" while logging, the log time on a logged meal. */
    unsetLabel: String = "just now"
) {
    val context = LocalContext.current
    val openPicker = {
        val t = eatenAt.time ?: LocalTime.now()
        android.app.TimePickerDialog(
            context, { _, h, m -> onPick(LocalTime.of(h, m)) }, t.hour, t.minute, false
        ).show()
    }
    val timeText = eatenAt.time?.let { clockLabel(it) } ?: unsetLabel
    val fromPhoto = eatenAt.source == TimeSource.PHOTO && eatenAt.time != null

    Column(modifier = modifier) {
        if (prominent) {
            Row(
                modifier = Modifier
                    .glassSoft(RoundedCornerShape(50))
                    .clickable(onClick = openPicker)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
                Text("Eaten $timeText", style = MaterialTheme.typography.titleSmall, color = Cream)
                if (fromPhoto) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Time from the photo", tint = CreamMuted, modifier = Modifier.size(14.dp))
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = openPicker)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = CreamMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Eaten $timeText" + if (fromPhoto) " · from photo" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = CreamMuted
                )
            }
        }
        val suggestion = eatenAt.photoSuggestion
        if (suggestion != null && onUsePhotoSuggestion != null && !fromPhoto) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onUsePhotoSuggestion)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Photo taken ${photoWhenLabel(suggestion)} · ", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
                Text("Use this time", style = MaterialTheme.typography.labelSmall, color = GoldLight)
            }
        }
    }
}

/** "today 7:40 PM", "yesterday 11:05 PM", "Tue 23 Sep, 7:40 PM". */
private fun photoWhenLabel(taken: LocalDateTime): String {
    val date = taken.toLocalDate()
    val today = LocalDate.now()
    val day = when (date) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM")) + ","
    }
    return "$day ${clockLabel(taken.toLocalTime())}"
}
