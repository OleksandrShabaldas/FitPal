package com.fitpal.app.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.swipeNavigation
import com.fitpal.app.ui.theme.glass

/**
 * Settings hub — Android-style. Each category is a button that opens its own focused sub-screen
 * (see [SettingsCategoryScreen]) instead of cramming everything onto one long page.
 */
@Composable
fun SettingsScreen(
    onOpenCategory: (String) -> Unit,
    onSwipeToCollection: () -> Unit = {},
    onBack: () -> Unit = {},
) {
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
            SETTINGS_CATEGORIES.forEach { category ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass()
                        .clickable { onOpenCategory(category.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                category.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
