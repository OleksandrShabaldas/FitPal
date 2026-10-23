package com.fitpal.app.ui.screen.coach

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.model.FoodProfile
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.SetupPill
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class FoodProfileViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val profile: StateFlow<FoodProfile> = settings.foodProfile
    fun update(change: (FoodProfile) -> FoodProfile) = settings.setFoodProfile(change(settings.foodProfile.value))
}

/**
 * "Your food profile" (docs/redesign/10): the form filled in once — every meal plan uses it, and
 * allergies are never used. One scroll of glass sections, all optional, saved as you go.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodProfileScreen(onBack: () -> Unit, viewModel: FoodProfileViewModel = hiltViewModel()) {
    val p by viewModel.profile.collectAsStateWithLifecycle()
    fun set(change: (FoodProfile) -> FoodProfile) = viewModel.update(change)

    GradientBackdrop(theme = BackdropTheme.TRENDS) {
        Column(Modifier.fillMaxSize().imePadding()) {
            GlassTopBar(title = "Your food profile", onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Every meal plan is made with this. All optional — the more you say, the better the ideas.",
                    style = MaterialTheme.typography.bodySmall, color = CreamMuted
                )

                Section("Diet") {
                    Pills(FoodProfile.DIETS, setOf(p.diet)) { d -> set { it.copy(diet = d) } }
                    if (p.diet == FoodProfile.DIET_OTHER) Line(p.dietOther, "Describe it") { v -> set { it.copy(dietOther = v) } }
                }

                Section("Allergies & intolerances", "Never used in a plan — and checked again after the AI answers.") {
                    Pills(FoodProfile.ALLERGIES, p.allergies) { a ->
                        set { it.copy(allergies = if (a in it.allergies) it.allergies - a else it.allergies + a) }
                    }
                    Line(p.allergyOther, "Anything else, separated by commas (e.g. kiwi)") { v -> set { it.copy(allergyOther = v) } }
                }

                Section("Won't eat") {
                    ChipInput(p.wontEat, "e.g. mushrooms, coriander", onAdd = { v -> set { it.copy(wontEat = (it.wontEat + v).distinct()) } }) { v ->
                        set { it.copy(wontEat = it.wontEat - v) }
                    }
                }

                Section("Love") {
                    ChipInput(p.love, "e.g. spicy food, feta", onAdd = { v -> set { it.copy(love = (it.love + v).distinct()) } }) { v ->
                        set { it.copy(love = it.love - v) }
                    }
                }

                Section("Favourite cuisines") {
                    Pills(FoodProfile.CUISINES, p.cuisines) { c ->
                        set { it.copy(cuisines = if (c in it.cuisines) it.cuisines - c else it.cuisines + c) }
                    }
                }

                Section("Cooking skill") {
                    Pills(FoodProfile.SKILLS, setOf(p.skill)) { s -> set { it.copy(skill = s) } }
                }

                Section("Kitchen") {
                    Pills(FoodProfile.KITCHEN, p.kitchen) { k ->
                        set { it.copy(kitchen = if (k in it.kitchen) it.kitchen - k else it.kitchen + k) }
                    }
                }

                Section("Usually cooking for") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Round(Icons.Default.Remove, "Fewer") { set { it.copy(cookingFor = (it.cookingFor - 1).coerceAtLeast(1)) } }
                        Text(
                            if (p.cookingFor == 1) "Just me" else "${p.cookingFor} people",
                            style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Round(Icons.Default.Add, "More") { set { it.copy(cookingFor = (it.cookingFor + 1).coerceAtMost(8)) } }
                    }
                }

                Section("Typical budget", runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()) {
                    var text by remember { mutableStateOf(p.budget?.let { if (it % 1f == 0f) it.toInt().toString() else "%.2f".format(it) }.orEmpty()) }
                    Box(Modifier.fillMaxWidth().glassSoft(RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (text.isEmpty()) Text("No budget", style = MaterialTheme.typography.bodyMedium, color = CreamFaint)
                        BasicTextField(
                            value = text,
                            onValueChange = { v ->
                                text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                                set { it.copy(budget = text.replace(',', '.').toFloatOrNull()?.takeIf { b -> b > 0f }) }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Cream),
                            cursorBrush = SolidColor(GoldLight),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Pills(listOf("meal", "day"), setOf(p.budgetPer), label = { "per $it" }) { per -> set { it.copy(budgetPer = per) } }
                }

                Section("Anything else") {
                    Line(p.notes, "e.g. I cook big batches on Sundays", minHeight = 70) { v -> set { it.copy(notes = v) } }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Cream)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pills(options: List<String>, selected: Set<String>, label: (String) -> String = { it }, onTap: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> SetupPill(label(o), selected = o in selected) { onTap(o) } }
    }
}

@Composable
private fun Line(value: String, placeholder: String, minHeight: Int = 0, onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    Box(Modifier.fillMaxWidth().glassSoft(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = CreamFaint)
        BasicTextField(
            value = text, onValueChange = { text = it; onValue(it) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Cream),
            cursorBrush = SolidColor(GoldLight),
            modifier = Modifier.fillMaxWidth().then(if (minHeight > 0) Modifier.height(minHeight.dp) else Modifier)
        )
    }
}

@Composable
private fun Round(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).glassSoft(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Cream, modifier = Modifier.size(18.dp))
    }
}
