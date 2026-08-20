package com.fitpal.app.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.domain.model.DietaryWarning

/**
 * The shared "you're near/over your … limit — log it anyway?" prompt, raised by
 * [com.fitpal.app.ml.DietaryGate] from every logging path. Mirrors the fasting guard's dialog so the
 * two feel the same. Confirm logs; dismiss aborts (and, on the AI paths, skips the expensive call).
 */
@Composable
fun DietaryWarningDialog(
    warning: DietaryWarning,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val noun = dietaryNoun(warning.kind)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (warning.alreadyOver) "Over your $noun limit" else "Close to your $noun limit") },
        text = {
            val lead = if (warning.alreadyOver)
                "You're already at ${warning.consumedKcal} of your ${warning.limitKcal} kcal for $noun today"
            else
                "You're at ${warning.consumedKcal} of your ${warning.limitKcal} kcal for $noun today"
            Text("$lead, and this looks like ${dietaryArticle(warning.kind)}. Log it anyway?")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Log anyway") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

/** Short plural noun for a rule, for headings and the Home strip ("dessert", "sugary drinks"). */
fun dietaryNoun(kind: DietaryRuleKind): String = when (kind) {
    DietaryRuleKind.DESSERT -> "dessert"
    DietaryRuleKind.FRIED -> "fried & fast food"
    DietaryRuleKind.SUGARY_DRINK -> "sugary drinks"
}

/** "a dessert" / "fried or fast food" / "a sugary drink" — for the warning sentence. */
private fun dietaryArticle(kind: DietaryRuleKind): String = when (kind) {
    DietaryRuleKind.DESSERT -> "a dessert"
    DietaryRuleKind.FRIED -> "fried or fast food"
    DietaryRuleKind.SUGARY_DRINK -> "a sugary drink"
}
