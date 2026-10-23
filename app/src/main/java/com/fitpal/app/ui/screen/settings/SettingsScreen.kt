package com.fitpal.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.swipeNavigation
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glass

/**
 * Settings hub — Android-style. A search field on top finds any setting by what it's called or what
 * it does; below it the categories sit in a few titled groups (You · Tracking · Notifications ·
 * Connections & AI · App), each opening its own focused sub-screen ([SettingsCategoryScreen]).
 */
@Composable
fun SettingsScreen(
    /** Open a category, optionally scrolled to one of its sections (a search result). */
    onOpenCategory: (category: String, focus: String?) -> Unit,
    onOpenFeedback: () -> Unit = {},
    /** Settings → Home-screen widgets: the widget gallery. */
    onOpenWidgets: () -> Unit = {},
    onSwipeToCollection: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val open: (String, String?) -> Unit = { id, focus ->
        focusManager.clearFocus()
        when (id) {
            SETTINGS_WIDGETS -> onOpenWidgets()
            SETTINGS_FEEDBACK -> onOpenFeedback()
            else -> onOpenCategory(id, focus)
        }
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        // Liquid glass and Material You pin the large title above the scrolling list (it collapses
        // as the list scrolls); Glow and Paper scroll it away with the content, as before.
        val pinnedBar = LookState.style == LookStyle.LIQUID || LookState.style == LookStyle.MATERIAL
        Column(Modifier.fillMaxSize()) {
        if (pinnedBar) GlassTopBar(title = "Settings")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .swipeNavigation(onSwipeLeft = {}, onSwipeRight = onSwipeToCollection)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = if (pinnedBar) 4.dp else 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!pinnedBar) GlassTopBar(title = "Settings")
            SettingsSearchField(query = query, onQueryChange = { query = it })

            if (query.isNotBlank()) {
                SearchResults(query = query, onOpen = open)
            } else {
                val style = LookState.style
                SETTINGS_GROUPS.forEach { group ->
                    Text(
                        group.title,
                        style = MaterialTheme.typography.labelLarge,
                        // Material You titles its sections in the accent colour, as Pixel's Settings does.
                        color = if (style == LookStyle.MATERIAL) MaterialTheme.colorScheme.primary else CreamMuted,
                        modifier = Modifier.padding(start = if (style == LookStyle.MATERIAL) 16.dp else 6.dp, top = 6.dp)
                    )
                    Column(modifier = Modifier.fillMaxWidth().glass()) {
                        group.categories.forEachIndexed { index, category ->
                            when (style) {
                                LookStyle.LIQUID -> {
                                    // iPhone grouped list: the hairline starts after the icon.
                                    if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = Cream.copy(alpha = 0.14f), modifier = Modifier.padding(start = 58.dp))
                                    LiquidCategoryRow(category) { open(category.id, null) }
                                }
                                LookStyle.MATERIAL -> MaterialCategoryRow(category) { open(category.id, null) }
                                else -> {
                                    if (index > 0) HorizontalDivider(color = Cream.copy(alpha = 0.07f), modifier = Modifier.padding(start = 64.dp))
                                    CategoryRow(category) { open(category.id, null) }
                                }
                            }
                        }
                    }
                }

                // Not a settings category — an action — so it's set apart with a warm accent.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .accentGlass(GoldLight)
                        .clickable(onClick = onOpenFeedback)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MailOutline, contentDescription = null, tint = GoldLight, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Send feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Cream)
                            Text(
                                "Ideas, bugs, praise — it goes straight to the developer",
                                style = MaterialTheme.typography.bodySmall,
                                color = CreamMuted
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GoldLight)
                    }
                }
            }
        }
        }
    }
}

/** One category row inside a hub group: a tinted icon, its name and a one-line description. */
@Composable
private fun CategoryRow(category: SettingsCategoryInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(category.tint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(category.icon, contentDescription = null, tint = category.tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Cream)
            Text(category.description, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = CreamMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Liquid glass: an iPhone Settings row — the category's icon white on a rounded square of its
 * colour, one line of title and a small chevron.
 */
@Composable
private fun LiquidCategoryRow(category: SettingsCategoryInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(30.dp).background(liquidIconColor(category), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(category.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(category.title, style = MaterialTheme.typography.bodyLarge, color = Cream, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = CreamFaint,
            modifier = Modifier.size(14.dp)
        )
    }
}

/** iPhone Settings' saturated icon squares: a fixed system colour per category (white icon on top). */
private fun liquidIconColor(category: SettingsCategoryInfo): Color = Color(
    when (category.id) {
        "features" -> 0xFF34C759
        "presets" -> 0xFFFF9500
        "fasting" -> 0xFF5E5CE6
        "rules" -> 0xFFFF2D55
        "caffeine" -> 0xFFA2845E
        "reminders" -> 0xFFFF3B30
        "activity" -> 0xFF30B0C7
        "ai" -> 0xFFAF52DE
        "appearance" -> 0xFF007AFF
        "personalize" -> 0xFF5856D6
        SETTINGS_WIDGETS -> 0xFFFF9F0A
        "data" -> 0xFF636366
        else -> 0xFF8E8E93
    }
)

/**
 * Material You: a Material list item — the icon in a tonal circle, the title over its one-line
 * description, no chevron (Pixel's Settings has none).
 */
@Composable
private fun MaterialCategoryRow(category: SettingsCategoryInfo, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(scheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(category.icon, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(category.title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text(category.description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

/** The glass search pill at the top of the hub — the same shape as Home's search bar, but live. */
@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    // Per look: Glow's glass pill; Liquid glass an iPhone search field (a filled, gently rounded
    // bar); Material You a Material search bar (a tall full-round bar in a raised surface).
    val bar = when (LookState.style) {
        LookStyle.LIQUID -> Modifier.clip(RoundedCornerShape(12.dp)).background(filmColor(0.10f)).padding(horizontal = 10.dp, vertical = 9.dp)
        LookStyle.MATERIAL -> Modifier.clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 16.dp)
        else -> Modifier.glass(RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(bar),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = CreamMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search settings", style = MaterialTheme.typography.bodyLarge, color = CreamFaint)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Cream),
                cursorBrush = SolidColor(GoldLight),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear search",
                tint = CreamMuted,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onQueryChange(""); focusManager.clearFocus() }
                    .padding(5.dp)
            )
        }
    }
}

/** What the search matched, best first — each opens its category at that section. */
@Composable
private fun SearchResults(query: String, onOpen: (String, String?) -> Unit) {
    val results = remember(query) { searchSettings(query) }
    if (results.isEmpty()) {
        Text(
            "Nothing matches “${query.trim()}”. Try another word — like “weigh-in”, “backup” or “protein”.",
            style = MaterialTheme.typography.bodyMedium,
            color = CreamMuted,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth().glass()) {
        results.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = Cream.copy(alpha = 0.07f), modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(entry.category, entry.anchor) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge, color = Cream)
                    Text(entry.where, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = CreamMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
