package com.fitpal.app.ui.screen.addfood

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass

@Composable
fun AddFoodScreen(
    onTakePhoto: () -> Unit,
    onImagePicked: (Uri) -> Unit,
    onDescribeToAI: () -> Unit,
    onScanBarcode: () -> Unit,
    onSelectSaved: () -> Unit,
    onManualEntry: () -> Unit,
    onCustomFood: () -> Unit,
    onLogExercise: () -> Unit,
    onLogged: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddFoodViewModel = hiltViewModel()
) {
    var showWeightDialog by remember { mutableStateOf(false) }
    val recentFoods by viewModel.recentFoods.collectAsStateWithLifecycle()
    val logged by viewModel.logged.collectAsStateWithLifecycle()

    LaunchedEffect(logged) { if (logged) onLogged() }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onImagePicked(uri) }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Add food", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // One-tap re-log of foods eaten before (auto, from history) — the fastest daily path.
                if (recentFoods.isNotEmpty()) {
                    SectionLabel("Your usuals — tap to log again")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentFoods.forEach { item ->
                            RecentChip(item = item, onClick = { viewModel.quickLog(item) })
                        }
                    }
                }

                SectionLabel("Add something new")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SquareTile(Icons.Default.CameraAlt, "Take photo", "Snap your plate", onTakePhoto, Modifier.weight(1f))
                    SquareTile(
                        Icons.Default.PhotoLibrary, "From gallery", "Pick a photo",
                        {
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SquareTile(Icons.Default.ChatBubble, "Describe to AI", "Just type what you ate", onDescribeToAI, Modifier.weight(1f))
                    SquareTile(Icons.Default.QrCodeScanner, "Scan barcode", "Packaged product", onScanBarcode, Modifier.weight(1f))
                }

                SectionLabel("More ways")
                WideTile(Icons.Default.Bookmark, "Saved foods", "Your bookmarked collection", onClick = onSelectSaved)
                WideTile(Icons.Default.Search, "Search foods", "Find any food — online & imported", onClick = onManualEntry)
                WideTile(Icons.Default.Edit, "Custom food", "Type in your own calories & macros", onClick = onCustomFood)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WideTile(Icons.Default.FitnessCenter, "Exercise", "Calories burned", modifier = Modifier.weight(1f), onClick = onLogExercise)
                    WideTile(Icons.Default.MonitorWeight, "Weight", "Log current weight", modifier = Modifier.weight(1f), onClick = { showWeightDialog = true })
                }
            }
        }
    }

    if (showWeightDialog) {
        var weightText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text("Log weight") },
            text = {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    weightText.toFloatOrNull()?.let { kg ->
                        viewModel.logWeight(kg)
                        showWeightDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showWeightDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = CreamMuted,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** A one-tap "log this again" chip for a recently eaten food. */
@Composable
private fun RecentChip(item: MealLogItemEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 96.dp, max = 150.dp)
            .glass(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Cream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${item.calories.toInt()} kcal · +",
            style = MaterialTheme.typography.labelMedium,
            color = CalorieColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SquareTile(icon: ImageVector, label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.height(140.dp).glass().clickable(onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = GoldLight, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = Cream, textAlign = TextAlign.Center)
        Text(description, style = MaterialTheme.typography.labelSmall, color = CreamMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WideTile(
    icon: ImageVector,
    label: String,
    description: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit
) {
    Row(
        modifier = modifier.glass().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = GoldLight, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, style = MaterialTheme.typography.bodySmall, color = CreamMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
