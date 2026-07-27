package com.fitpal.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitpal.app.update.UpdatePhase
import com.fitpal.app.update.UpdateUiState

/**
 * The "a new FitPal is out" prompt. Shown over whatever screen the user is on when a check finds a
 * newer GitHub release.
 *
 * It's explicit that Android — not FitPal — performs the install, because the user still has to
 * approve it in the system dialog; promising a one-tap silent update would be a lie.
 */
@Composable
fun UpdatePromptDialog(
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onUpdateWatch: () -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    val update = state.available ?: return
    val downloading = state.phase == UpdatePhase.DOWNLOADING_PHONE ||
        state.phase == UpdatePhase.DOWNLOADING_WATCH ||
        state.phase == UpdatePhase.SENDING_WATCH

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("FitPal ${update.version} is available") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "You're on ${state.currentVersion}." +
                        if (state.watchNeedsUpdate) " Your watch is on ${state.watchVersion}." else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (update.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        update.releaseNotes.lineSequence().take(10).joinToString("\n").trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                when (state.phase) {
                    UpdatePhase.DOWNLOADING_PHONE, UpdatePhase.DOWNLOADING_WATCH, UpdatePhase.SENDING_WATCH -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                when (state.phase) {
                                    UpdatePhase.DOWNLOADING_PHONE -> "Downloading…"
                                    UpdatePhase.DOWNLOADING_WATCH -> "Downloading watch update…"
                                    else -> "Sending to your watch…"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.totalBytes > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${state.downloadedBytes / 1_000_000} / ${state.totalBytes / 1_000_000} MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    UpdatePhase.READY_PHONE -> Text(
                        "Downloaded — confirm the install in the window Android opened.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    UpdatePhase.WATCH_SENT -> Text(
                        "Sent to your watch — tap the notification there to install it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    UpdatePhase.FAILED -> Text(
                        state.message ?: "Something went wrong.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    else -> Text(
                        "Android will ask you to confirm the install.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (state.watchNeedsUpdate && state.phase == UpdatePhase.READY_PHONE) {
                TextButton(onClick = onUpdateWatch, enabled = state.watchConnected) { Text("Update watch") }
            } else {
                TextButton(onClick = onUpdate, enabled = !downloading) {
                    Text(if (state.phase == UpdatePhase.READY_PHONE) "Install" else "Update")
                }
            }
        },
        dismissButton = {
            if (!downloading) {
                Row {
                    TextButton(onClick = onSkipVersion) { Text("Skip") }
                    TextButton(onClick = onDismiss) { Text("Later") }
                }
            }
        }
    )
}
