package com.fitpal.wear.presentation

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.input.RemoteInputIntentHelper
import com.fitpal.wear.data.PhoneComms
import kotlinx.coroutines.launch

@Composable
fun DescribeMealScreen() {
    val context = LocalContext.current
    DescribeScreen(
        title = "Describe meal",
        prompt = "Say what you ate",
        onSend = { text -> PhoneComms.describeMeal(context, text) }
    )
}

@Composable
fun DescribeExerciseScreen() {
    val context = LocalContext.current
    DescribeScreen(
        title = "Log exercise",
        prompt = "Say what you did",
        onSend = { text -> PhoneComms.describeExercise(context, text) }
    )
}

/**
 * Captures a description by voice/keyboard (Wear [RemoteInput]) and fires it to the phone, which
 * generates the analysis in the background and notifies when it's ready to review. The watch stays
 * thin: it never shows the result — that's on the phone.
 */
@Composable
private fun DescribeScreen(
    title: String,
    prompt: String,
    onSend: suspend (String) -> Boolean
) {
    val scope = rememberCoroutineScope()
    var recognized by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val text = data?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(KEY_DESCRIPTION)?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            recognized = text
            sent = false
            failed = false
            scope.launch {
                sending = true
                val ok = onSend(text)
                sending = false
                sent = ok
                failed = !ok
            }
        }
    }

    fun launchInput() {
        val remoteInputs = listOf(
            RemoteInput.Builder(KEY_DESCRIPTION).setLabel(prompt).build()
        )
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        launcher.launch(intent)
    }

    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ListHeader { Text(title) } }
            item {
                Chip(
                    label = { Text(if (recognized == null) "Speak or type" else "Redo") },
                    onClick = { launchInput() },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            recognized?.let { text ->
                item {
                    Text(
                        text = "“$text”",
                        style = MaterialTheme.typography.caption1,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    )
                }
            }
            item {
                when {
                    sending -> CircularProgressIndicator(modifier = Modifier.padding(6.dp))
                    sent -> Text(
                        text = "Sent ✓ Your phone is analysing — watch for the notification.",
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    )
                    failed -> Text(
                        text = "Phone not connected. Try again when it's in range.",
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    )
                }
            }
        }
    }
}

private const val KEY_DESCRIPTION = "fitpal_description"
