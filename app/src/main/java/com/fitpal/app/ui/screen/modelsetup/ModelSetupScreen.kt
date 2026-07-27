package com.fitpal.app.ui.screen.modelsetup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.NutritionDbStatus
import com.fitpal.app.ml.ModelManager
import com.fitpal.app.ml.ModelStatus
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass

@Composable
fun ModelSetupScreen(
    onBack: () -> Unit,
    viewModel: ModelSetupViewModel = hiltViewModel()
) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val nutritionStatus by viewModel.nutritionStatus.collectAsStateWithLifecycle()
    val brandedStatus by viewModel.brandedStatus.collectAsStateWithLifecycle()
    val extraStatus by viewModel.extraStatus.collectAsStateWithLifecycle()
    val pickFoodCsv = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::importExtraFoods)
    }
    val token by viewModel.hfToken.collectAsStateWithLifecycle()
    val hasToken = !token.isNullOrBlank()
    val allReady = viewModel.specs.all { statuses[it.id] is ModelStatus.Ready }
    val anyDownloading = statuses.values.any { it is ModelStatus.Downloading }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "AI model setup", onBack = onBack)

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("FitPal's on-device AI — your offline fallback.", style = MaterialTheme.typography.titleMedium, color = Cream)
                Text(
                    "Download the vision model once (~4.4 GB). The app prefers the online AI when you've added a Gemini key in Settings, and falls back to this on-device model whenever you're offline or out of free quota — so recognising food always works. Wi-Fi recommended.",
                    style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                )

                if (!allReady) {
                    SetupSteps()
                    OutlinedTextField(
                        value = token.orEmpty(),
                        onValueChange = viewModel::setHfToken,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Hugging Face token") },
                        placeholder = { Text("hf_…") },
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(2.dp))

                viewModel.specs.forEach { spec ->
                    ModelRow(spec = spec, status = statuses[spec.id] ?: ModelStatus.NotDownloaded, onRetry = { viewModel.retry(spec.id) })
                }

                NutritionDbCard(
                    title = "Full food database",
                    subtitle = "Official USDA — ~15,000 foods (incl. prepared dishes)",
                    sizeLabel = "~13 MB",
                    downloadLabel = "Download full database",
                    status = nutritionStatus,
                    onDownload = { viewModel.importNutritionDb() }
                )
                NutritionDbCard(
                    title = "Branded products",
                    subtitle = "~1.9M packaged products with brands. Big: 428 MB, ~5-min import.",
                    sizeLabel = "428 MB",
                    downloadLabel = "Add branded products",
                    status = brandedStatus,
                    onDownload = { viewModel.importBranded() }
                )
                NutritionDbCard(
                    title = "European foods (Open Food Facts)",
                    subtitle = "Popular Central-European products (Horalka, Kofola, Sedita…) — downloaded for offline use.",
                    sizeLabel = "Download",
                    downloadLabel = "Download European foods",
                    status = extraStatus,
                    onDownload = { viewModel.downloadEuropeanFoods() }
                )
                // Open Food Facts rate-limits search, so each tap grabs a batch of the most
                // popular products and remembers where it stopped — tap again for the next batch.
                if (extraStatus is NutritionDbStatus.Ready) {
                    TextButton(onClick = { viewModel.downloadEuropeanFoods() }) {
                        Text("Download more popular products", style = MaterialTheme.typography.labelLarge, color = GoldLight)
                    }
                }
                TextButton(onClick = { pickFoodCsv.launch("*/*") }) {
                    Text("…or import a food file for the full set (advanced)", style = MaterialTheme.typography.labelLarge, color = CreamMuted)
                }

                if (allReady) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("All set — the AI is ready to use.", style = MaterialTheme.typography.bodyLarge, color = Cream)
                    }
                }
            }

            if (!allReady) {
                Button(
                    onClick = { viewModel.downloadAll() },
                    enabled = hasToken && !anyDownloading,
                    modifier = Modifier.fillMaxWidth().padding(20.dp)
                ) {
                    Text(
                        when {
                            anyDownloading -> "Downloading…"
                            !hasToken -> "Paste your token above to download"
                            else -> "Download AI model"
                        }
                    )
                }
            }
        }
    }
}

/** Numbered, tappable step-by-step for the one-time Hugging Face setup. */
@Composable
private fun SetupSteps() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth().glass().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("How to set it up (one time)", style = MaterialTheme.typography.titleSmall, color = Cream)
        SetupStep(1, "Create a free Hugging Face account.", "Open huggingface.co/join", "https://huggingface.co/join", uriHandler)
        SetupStep(2, "Open the model page and accept the Gemma licence (a short form).", "Open the model page", "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview", uriHandler)
        SetupStep(3, "Create an access token with the “Read” role, then copy it.", "Open token settings", "https://huggingface.co/settings/tokens", uriHandler)
        SetupStep(4, "Paste the token below (it starts with hf_) and tap Download AI model.", null, null, uriHandler)
    }
}

@Composable
private fun SetupStep(number: Int, text: String, linkLabel: String?, url: String?, uriHandler: UriHandler) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$number.", style = MaterialTheme.typography.bodyMedium, color = GoldLight)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Cream)
            if (linkLabel != null && url != null) {
                Text(
                    linkLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = GoldLight,
                    modifier = Modifier.clickable { uriHandler.openUri(url) }
                )
            }
        }
    }
}

@Composable
private fun ModelRow(spec: ModelManager.Spec, status: ModelStatus, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(spec.displayName, style = MaterialTheme.typography.titleMedium, color = Cream)
                Text(spec.description, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
            StatusBadge(status = status, approxBytes = spec.approxBytes)
        }
        when (status) {
            is ModelStatus.Downloading -> {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text("${formatBytes(status.downloadedBytes)} / ${formatBytes(status.totalBytes)}", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
            }
            is ModelStatus.Failed -> {
                Spacer(Modifier.height(8.dp))
                Text(status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            else -> {}
        }
    }
}

@Composable
private fun StatusBadge(status: ModelStatus, approxBytes: Long) {
    when (status) {
        is ModelStatus.Ready -> Icon(Icons.Default.CheckCircle, contentDescription = "Ready", tint = MaterialTheme.colorScheme.primary)
        is ModelStatus.Failed -> Icon(Icons.Default.Error, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error)
        is ModelStatus.Downloading -> Text("${(status.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = Cream)
        ModelStatus.NotDownloaded -> Text("~${formatBytes(approxBytes)}", style = MaterialTheme.typography.labelLarge, color = CreamMuted)
    }
}

@Composable
private fun NutritionDbCard(
    title: String,
    subtitle: String,
    sizeLabel: String,
    downloadLabel: String,
    status: NutritionDbStatus,
    onDownload: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Cream)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
            when (status) {
                is NutritionDbStatus.Ready -> Icon(Icons.Default.CheckCircle, contentDescription = "Installed", tint = MaterialTheme.colorScheme.primary)
                is NutritionDbStatus.Failed -> Icon(Icons.Default.Error, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error)
                is NutritionDbStatus.Downloading -> Text("${(status.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = Cream)
                is NutritionDbStatus.Importing -> CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary)
                NutritionDbStatus.NotImported -> Text(sizeLabel, style = MaterialTheme.typography.labelLarge, color = CreamMuted)
            }
        }
        when (status) {
            is NutritionDbStatus.Downloading -> {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(if (status.label.isBlank()) "Downloading…" else "Downloading ${status.label}…", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
            }
            is NutritionDbStatus.Importing -> {
                Spacer(Modifier.height(8.dp))
                Text(if (status.label.isBlank()) "Importing foods…" else "Importing ${status.label}…", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
            is NutritionDbStatus.Ready -> {
                Spacer(Modifier.height(8.dp))
                Text("${status.foodCount} foods installed.", style = MaterialTheme.typography.bodyMedium, color = Cream)
            }
            is NutritionDbStatus.Failed -> {
                Spacer(Modifier.height(8.dp))
                Text(status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDownload) { Text("Retry") }
            }
            NutritionDbStatus.NotImported -> {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDownload) { Text(downloadLabel) }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000) String.format("%.1f GB", mb / 1000.0) else String.format("%.0f MB", mb)
}
