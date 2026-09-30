package com.fitpal.app.ui.screen.widgets

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InkBlack
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.widget.WidgetData
import com.fitpal.app.widget.WidgetDataSource
import com.fitpal.app.widget.WidgetKind
import com.fitpal.app.widget.WidgetPinning
import com.fitpal.app.widget.WidgetStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Today's numbers for the previews, loaded once. Until there's a calorie target to draw against (a
 * brand-new user), the previews use sample numbers instead — a gallery of empty rings sells nothing.
 */
@HiltViewModel
class WidgetGalleryViewModel @Inject constructor(dataSource: WidgetDataSource) : ViewModel() {
    private val _data = MutableStateFlow(WidgetData.sample())
    val data: StateFlow<WidgetData> = _data

    init {
        viewModelScope.launch {
            runCatching { dataSource.load(WidgetKind.entries.toSet()) }.getOrNull()
                ?.takeIf { it.hasGoal }
                ?.let { _data.value = it }
        }
    }
}

/**
 * Settings → Home-screen widgets: every widget, drawn by the real renderer in the look you pick, each
 * with an "Add to home screen" button (the launcher confirms where it goes). Where a launcher can't
 * add from an app, it says how to add it by hand instead.
 */
@Composable
fun WidgetGalleryScreen(onBack: () -> Unit, viewModel: WidgetGalleryViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val data by viewModel.data.collectAsStateWithLifecycle()
    var styleIndex by rememberSaveable { mutableIntStateOf(0) }
    val style = WidgetStyle.entries[styleIndex]
    val canPin = remember { WidgetPinning.isSupported(context) }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Home-screen widgets", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        "FitPal at a glance, right on your home screen. Pick a look, then add the ones you like. " +
                            "Each one can be resized, and you can change its look any time from its long-press menu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CreamMuted
                    )
                }
                item {
                    Column {
                        SegmentedPills(
                            labels = WidgetStyle.entries.map { it.label },
                            selectedIndex = styleIndex,
                            accent = GoldLight,
                            onSelect = { styleIndex = it }
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(style.blurb, style = MaterialTheme.typography.labelMedium, color = CreamFaint)
                    }
                }
                items(WidgetKind.entries, key = { it.id }) { kind ->
                    GalleryCard(
                        kind = kind,
                        style = style,
                        data = data,
                        canPin = canPin,
                        onAdd = {
                            if (!WidgetPinning.request(context, kind, style)) {
                                Toast.makeText(
                                    context,
                                    "Long-press your home screen, tap Widgets, and find FitPal.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryCard(kind: WidgetKind, style: WidgetStyle, data: WidgetData, canPin: Boolean, onAdd: () -> Unit) {
    val size = kind.previewSize
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text(stringResource(kind.label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Cream)
        Text(stringResource(kind.description), style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        Spacer(Modifier.height(12.dp))
        WallpaperSwatch(Modifier.fillMaxWidth()) {
            WidgetPreview(
                kind, style, data,
                Modifier.widthIn(max = size.width.dp).fillMaxWidth().aspectRatio(size.width / size.height)
            )
        }
        Spacer(Modifier.height(12.dp))
        if (canPin) {
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = GoldLight, contentColor = InkBlack),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add to home screen", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                "To add it: long-press your home screen, tap Widgets, and find FitPal.",
                style = MaterialTheme.typography.labelMedium,
                color = CreamFaint
            )
        }
    }
}
