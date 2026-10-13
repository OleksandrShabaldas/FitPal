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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .swipeNavigation(onSwipeLeft = {}, onSwipeRight = onSwipeToCollection)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassTopBar(title = "Settings")
            SettingsSearchField(query = query, onQueryChange = { query = it })

            if (query.isNotBlank()) {
                SearchResults(query = query, onOpen = open)
            } else {
                SETTINGS_GROUPS.forEach { group ->
                    Text(
                        group.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = CreamMuted,
                        modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                    )
                    Column(modifier = Modifier.fillMaxWidth().glass()) {
                        group.categories.forEachIndexed { index, category ->
                            if (index > 0) HorizontalDivider(color = Cream.copy(alpha = 0.07f), modifier = Modifier.padding(start = 64.dp))
                            CategoryRow(category) { open(category.id, null) }
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

/** The glass search pill at the top of the hub — the same shape as Home's search bar, but live. */
@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
