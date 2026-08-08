package com.fitpal.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Rename anything the user has logged — a dish, or a whole meal.
 *
 * Renaming is plain text on purpose: it's the one edit that shouldn't need the AI or the food
 * database. A food picked from the database arrives under its catalogue name ("Cheese, cheddar,
 * sharp"), and the AI names dishes as it sees them; either can be replaced with whatever the user
 * actually calls it. Only the name changes — the nutrition stays exactly as logged.
 *
 * The field opens with the current name selected, so typing replaces it and a small tweak is still
 * possible. When [allowEmpty] is set, clearing the field is a valid answer (it removes the name);
 * otherwise the confirm button stays disabled until there's something to save.
 */
@Composable
fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Rename",
    label: String = "Name",
    hint: String? = null,
    placeholder: String? = null,
    /** True for the whole-meal name, which is optional — an empty field clears it. */
    allowEmpty: Boolean = false
) {
    var value by remember {
        mutableStateOf(TextFieldValue(currentName, selection = TextRange(0, currentName.length)))
    }
    val trimmed = value.text.trim()
    val canSave = allowEmpty || trimmed.isNotEmpty()
    // Focus on open so the keyboard is up and the pre-selected name is replaced by the first keystroke.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text(label) },
                    placeholder = placeholder?.let { { Text(it) } },
                    singleLine = true,
                    // Enter saves — renaming is a one-field job, so don't make them reach for a button.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(trimmed) })
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = canSave) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
