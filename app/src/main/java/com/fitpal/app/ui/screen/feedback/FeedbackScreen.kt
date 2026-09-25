package com.fitpal.app.ui.screen.feedback

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitpal.app.BuildConfig
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InkBlack
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

/** Where feedback goes — the one place to change it. */
const val FEEDBACK_EMAIL = "gamedev@ittt-production.uk"

/** What a message is about — sets the subject line and the prompt in the text box. */
private enum class FeedbackKind(val label: String, val icon: ImageVector, val prompt: String) {
    IDEA("An idea", Icons.Default.Lightbulb, "What would make FitPal better for you?"),
    BUG("Something's broken", Icons.Default.BugReport, "What happened, and what did you expect instead?"),
    PRAISE("Something I love", Icons.Default.FavoriteBorder, "What's working well for you?"),
    OTHER("Something else", Icons.Default.ChatBubbleOutline, "What's on your mind?")
}

/**
 * Settings → Send feedback. Write a message, pick what it's about, and it opens the sender's own
 * email app with everything filled in (addressed to [FEEDBACK_EMAIL]) — no server, no account,
 * nothing leaves the phone until they press send there. App and phone details are attached by
 * default (they make a bug report actually useful) and can be switched off.
 */
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf("") }
    var includeDetails by rememberSaveable { mutableStateOf(true) }
    var opened by rememberSaveable { mutableStateOf(false) }
    val kind = FeedbackKind.entries[kindIndex]
    val details = deviceDetails()

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Send feedback", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().glass().padding(18.dp)) {
                    Text("Tell me what you think", style = MaterialTheme.typography.headlineSmall, color = Cream)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Every message is read by the person who builds FitPal. Ideas, bugs, the bits " +
                            "that feel clunky — it all shapes what comes next.",
                        style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                    )
                }

                Text("What's it about?", style = MaterialTheme.typography.labelLarge, color = CreamMuted)
                // A 2×2 of big, friendly tiles rather than a cramped chip row.
                FeedbackKind.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { k ->
                            KindTile(
                                kind = k,
                                selected = k == kind,
                                onClick = { kindIndex = k.ordinal },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 4000) message = it },
                    placeholder = { Text(kind.prompt) },
                    minLines = 6,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth().glassSoft().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Include app & phone details", style = MaterialTheme.typography.bodyMedium, color = Cream)
                        Text(details, style = MaterialTheme.typography.labelSmall, color = CreamFaint)
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(checked = includeDetails, onCheckedChange = { includeDetails = it })
                }

                Button(
                    onClick = {
                        opened = sendFeedback(context, kind, message.trim(), if (includeDetails) details else null)
                    },
                    enabled = message.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldLight, contentColor = InkBlack),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Write the email", fontWeight = FontWeight.SemiBold)
                }

                AnimatedVisibility(visible = opened, enter = fadeIn()) {
                    Text(
                        "Thank you — it means a lot. If you didn't press send in your email app, it's still here to try again.",
                        style = MaterialTheme.typography.bodySmall, color = ScoreFair
                    )
                }

                Text(
                    "Opens your email app with this filled in, addressed to $FEEDBACK_EMAIL. Nothing is " +
                        "sent until you press send there, and your reply address is only used to answer you.",
                    style = MaterialTheme.typography.labelSmall, color = CreamFaint
                )
            }
        }
    }
}

@Composable
private fun KindTile(kind: FeedbackKind, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .then(if (selected) Modifier.accentGlass(GoldLight, shape) else Modifier.glassSoft(shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(kind.icon, contentDescription = null, tint = if (selected) GoldLight else CreamMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            kind.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) GoldLight else Cream,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** "FitPal 2.1.0 (25) · Android 15 · samsung SM-S938B" — enough to reproduce a bug, nothing personal. */
private fun deviceDetails(): String =
    "FitPal ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} · " +
        "${Build.MANUFACTURER} ${Build.MODEL}"

/**
 * Open the email app pre-filled. Returns true when an email app took it; with none installed the
 * message is copied instead, so nothing typed is lost.
 */
private fun sendFeedback(context: Context, kind: FeedbackKind, message: String, details: String?): Boolean {
    val subject = "FitPal feedback · ${kind.label} (v${BuildConfig.VERSION_NAME})"
    val body = buildString {
        append(message)
        if (details != null) append("\n\n—\n").append(details)
    }
    // Both the mailto query and the extras — different email apps read different ones.
    val uri = Uri.parse("mailto:$FEEDBACK_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
    val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("FitPal feedback", "$subject\n\n$body"))
        Toast.makeText(
            context,
            "No email app found — your message is copied. Paste it into an email to $FEEDBACK_EMAIL.",
            Toast.LENGTH_LONG
        ).show()
        false
    }
}
