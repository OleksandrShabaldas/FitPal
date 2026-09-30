package com.fitpal.app.ui.screen.widgets

import android.util.SizeF
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InkBlack
import com.fitpal.app.widget.WidgetData
import com.fitpal.app.widget.WidgetKind
import com.fitpal.app.widget.WidgetStyle
import com.fitpal.app.widget.WidgetTheme

/**
 * Picking a widget's look — shown when it's added to the home screen, and again from its long-press
 * menu. A big live preview at the widget's real size, the four looks below it, and one button.
 */
@Composable
fun WidgetConfigScreen(
    kind: WidgetKind,
    size: SizeF,
    data: WidgetData,
    initial: WidgetStyle,
    onDone: (WidgetStyle) -> Unit,
    onCancel: () -> Unit
) {
    var styleId by rememberSaveable { mutableStateOf(initial.id) }
    val style = WidgetStyle.fromId(styleId)
    val theme = when (kind.theme) {
        WidgetTheme.WARM -> BackdropTheme.TODAY
        WidgetTheme.BLUE -> BackdropTheme.TRENDS
        WidgetTheme.GREEN -> BackdropTheme.GARDEN
    }
    GradientBackdrop(theme = theme) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            GlassTopBar(title = stringResource(kind.label), onBack = onCancel)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp)
            ) {
                Text("Pick a look", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Change it any time — long-press the widget and open its settings, or use Settings → Home-screen widgets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamMuted
                )
                Spacer(Modifier.height(16.dp))
                WallpaperSwatch(Modifier.fillMaxWidth()) {
                    WidgetPreview(
                        kind, style, data,
                        Modifier.widthIn(max = size.width.dp).fillMaxWidth().aspectRatio(size.width / size.height),
                        size = size
                    )
                }
                Spacer(Modifier.height(16.dp))
                WidgetStylePicker(kind = kind, data = data, selected = style, onSelect = { styleId = it.id })
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onDone(style) },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldLight, contentColor = InkBlack),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
