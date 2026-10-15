package com.fitpal.app.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.theme.AppIcon
import com.fitpal.app.ui.theme.AppIconCatalog
import com.fitpal.app.ui.theme.AppIconSwitcher
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glass

/**
 * Settings → Appearance → App icon: a motif (apple, ring, carrot, sprout — or the fun ones), then a
 * colourway made to match each look. Offers the icon that matches the current look, and warns
 * before switching that the launcher may need the shortcut re-added.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppIconSection() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIconSwitcher.current(context)) }
    val groups = listOf("apple" to "Apple", "ring" to "Ring", "carrot" to "Carrot", "sprout" to "Sprout", "fun" to "Fun")
    var group by remember { mutableStateOf(current.group) }
    var pending by remember { mutableStateOf<AppIcon?>(null) }
    val look = LookState.look
    val hint = AppIconSwitcher.lookHint(look)
    val suggestion = AppIconSwitcher.matchFor(current, look)

    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("App icon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Cream)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconImage(current, 52)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Now: ${current.label}", style = MaterialTheme.typography.bodyLarge, color = Cream)
                if (current.colourway.isNotEmpty()) Text(current.colourway, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
        }
        if (suggestion != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().accentGlass(GoldLight, RoundedCornerShape(16.dp)).clickable { pending = suggestion }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconImage(suggestion, 36)
                Spacer(Modifier.width(10.dp))
                Text("Match the icon to your look", style = MaterialTheme.typography.labelLarge, color = Cream, modifier = Modifier.weight(1f))
                Text("Use", style = MaterialTheme.typography.labelLarge, color = GoldLight)
            }
        }
        Spacer(Modifier.height(12.dp))
        SegmentedPills(
            labels = groups.map { it.second },
            selectedIndex = groups.indexOfFirst { it.first == group }.coerceAtLeast(0),
            accent = GoldLight,
            onSelect = { group = groups[it].first }
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIconCatalog.all.filter { it.group == group }.forEach { icon ->
                val selected = icon.id == current.id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(72.dp).clickable(enabled = !selected) { pending = icon }
                ) {
                    IconImage(icon, 56, selected)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (icon.group == "fun") icon.label else icon.colourway,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) GoldLight else if (icon.lookHint == hint) Cream else CreamMuted,
                        textAlign = TextAlign.Center, maxLines = 2
                    )
                }
            }
        }
        Text(
            "The launcher can take a few seconds to show the new icon. On some phones you'll need to add FitPal to your home screen again from the app drawer.",
            style = MaterialTheme.typography.labelSmall, color = CreamFaint, modifier = Modifier.padding(top = 12.dp)
        )
    }

    pending?.let { icon ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Switch to ${icon.label}${if (icon.colourway.isNotEmpty()) " · ${icon.colourway}" else ""}?") },
            text = { Text("Your home-screen shortcut may disappear while the launcher swaps the icon — add FitPal back from the app drawer if it does.") },
            confirmButton = {
                TextButton(onClick = {
                    AppIconSwitcher.apply(context, icon)
                    current = icon
                    group = icon.group
                    pending = null
                    Toast.makeText(context, "Icon changed — your launcher will show it shortly", Toast.LENGTH_SHORT).show()
                }) { Text("Switch") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun IconImage(icon: AppIcon, sizeDp: Int, selected: Boolean = false) {
    val context = LocalContext.current
    val px = (sizeDp * context.resources.displayMetrics.density).toInt()
    val bitmap = remember(icon.id, px) {
        ContextCompat.getDrawable(context, icon.mipmap)?.toBitmap(px, px)?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = icon.label,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape((sizeDp * 0.3f).dp))
                .then(if (selected) Modifier.border(2.5.dp, GoldLight, RoundedCornerShape((sizeDp * 0.3f).dp)) else Modifier)
        )
    }
}
