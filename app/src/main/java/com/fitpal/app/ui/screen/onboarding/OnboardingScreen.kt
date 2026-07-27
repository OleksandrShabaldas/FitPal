package com.fitpal.app.ui.screen.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.domain.model.Sex
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var sex by remember { mutableStateOf(Sex.MALE) }
    var age by remember { mutableStateOf("25") }
    var height by remember { mutableStateOf("175") }
    var weight by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf(FitnessGoal.RECOMP) }

    val ageOk = age.toIntOrNull()?.let { it in 10..120 } == true
    val heightOk = height.toFloatOrNull()?.let { it in 50f..300f } == true
    val weightOk = weight.toFloatOrNull()?.let { it in 20f..400f } == true
    val canStart = ageOk && heightOk && weightOk

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Welcome to FitPal", style = MaterialTheme.typography.displaySmall, color = Cream)
            Text(
                "A few details so your calorie and macro targets are right from day one.",
                style = MaterialTheme.typography.bodyMedium, color = CreamMuted
            )

            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("About you", style = MaterialTheme.typography.titleMedium, color = Cream)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sex.entries.forEach { s ->
                        Choice(label = s.label, selected = sex == s) { sex = s }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it.filter { c -> c.isDigit() } },
                        label = { Text("Age") }, suffix = { Text("yr") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it.filter { c -> c.isDigit() } },
                        label = { Text("Height") }, suffix = { Text("cm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Current weight") }, suffix = { Text("kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }

            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your goal", style = MaterialTheme.typography.titleMedium, color = Cream)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FitnessGoal.entries.forEach { g ->
                        Choice(label = g.label, selected = goal == g) { goal = g }
                    }
                }
                Text(goal.description, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }

            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("How logging works", style = MaterialTheme.typography.titleMedium, color = Cream)
                IntroRow(Icons.Default.CameraAlt, "Snap a plate", "Photograph or pick a photo — the AI reads the food and portions.")
                IntroRow(Icons.Default.ChatBubble, "Or just describe it", "Type \"chicken & rice\" and it fills in the nutrition.")
                IntroRow(Icons.Default.Bookmark, "Your usuals", "Foods you eat often come back as one-tap re-logs.")
            }

            Button(
                onClick = {
                    viewModel.complete(
                        sex = sex,
                        ageYears = age.toIntOrNull() ?: 25,
                        heightCm = height.toFloatOrNull() ?: 175f,
                        weightKg = weight.toFloatOrNull() ?: 0f,
                        goal = goal
                    )
                    onFinish()
                },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Get started") }

            TextButton(onClick = { viewModel.skip(); onFinish() }, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now", color = CreamMuted)
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val base = if (selected) Modifier.accentGlass(GoldLight, RoundedCornerShape(50)) else Modifier.glassSoft(RoundedCornerShape(50))
    Box(modifier = base.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) GoldLight else Cream)
    }
}

@Composable
private fun IntroRow(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = GoldLight, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Cream)
            Text(body, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
    }
}
