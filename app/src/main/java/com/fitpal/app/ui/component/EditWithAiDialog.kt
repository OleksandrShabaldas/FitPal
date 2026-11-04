package com.fitpal.app.ui.component

import com.fitpal.app.ui.component.kit.AiGlowLabel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import com.fitpal.app.ui.component.kit.FpAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * "Edit with AI" — the user describes a correction in plain language and the AI re-evaluates
 * the whole dish (rebalancing amounts), rather than just appending an ingredient.
 *
 * Shared by the pre-log Analysis screen and the logged meal detail, so a mistake can be fixed
 * the same way whether or not the meal has already been saved.
 */
@Composable
fun EditWithAiDialog(
    foodLabel: String,
    loading: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    FpAlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Edit \"$foodLabel\" with AI") },
        text = {
            Column {
                Text(
                    "Say what's wrong or missing — the AI re-checks the whole dish and rebalances the " +
                        "amounts to fit, instead of just adding on top.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. there are also beans; no cheese; it's grilled not fried") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !loading
                )
                if (loading) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Re-checking the dish…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            // A text button: its glow sits on the words, never a halo round a button you can't see
            // (the 4 Nov review, round 2) — and none at all while it's greyed out (#7).
            val canUpdate = text.isNotBlank() && !loading
            TextButton(onClick = { onSubmit(text) }, enabled = canUpdate) {
                AiGlowLabel("Update", enabled = canUpdate)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") } }
    )
}
