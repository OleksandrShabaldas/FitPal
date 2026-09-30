package com.fitpal.app.ui.screen.widgets

import android.util.SizeF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glassSoft
import com.fitpal.app.widget.WidgetData
import com.fitpal.app.widget.WidgetKind
import com.fitpal.app.widget.WidgetRender
import com.fitpal.app.widget.WidgetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The size a widget kind is shown at in previews (its default home-screen size). */
val WidgetKind.previewSize: SizeF get() = SizeF(defaultWidthDp.toFloat(), defaultHeightDp.toFloat())

/**
 * A stand-in wallpaper behind previews, so the see-through looks (Glass, Clear) show what they'd sit on.
 */
@Composable
fun WallpaperSwatch(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2E5B70), Color(0xFF6A4B70), Color(0xFFC4875C))))
            .padding(14.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * The widget exactly as the home screen will draw it — the real widget renderer, run off the main
 * thread. Sized by [modifier]; the image keeps its shape inside.
 */
@Composable
fun WidgetPreview(
    kind: WidgetKind,
    style: WidgetStyle,
    data: WidgetData,
    modifier: Modifier = Modifier,
    size: SizeF = kind.previewSize
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val image by produceState<ImageBitmap?>(initialValue = null, kind, style, data, size) {
        value = withContext(Dispatchers.Default) {
            runCatching { WidgetRender.previewBitmap(context, kind, style, data, size, density).asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = stringResource(kind.label),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** The four looks as tiles, each showing [kind] in that look; the chosen one glows gold. */
@Composable
fun WidgetStylePicker(
    kind: WidgetKind,
    data: WidgetData,
    selected: WidgetStyle,
    onSelect: (WidgetStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WidgetStyle.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { style ->
                    StyleTile(kind, data, style, style == selected, { onSelect(style) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StyleTile(
    kind: WidgetKind,
    data: WidgetData,
    style: WidgetStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .then(if (selected) Modifier.accentGlass(GoldLight, shape) else Modifier.glassSoft(shape))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        WallpaperSwatch(Modifier.fillMaxWidth().height(104.dp)) {
            WidgetPreview(kind, style, data, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(8.dp))
        Text(
            style.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) GoldLight else Cream
        )
        Text(style.blurb, style = MaterialTheme.typography.labelSmall, color = CreamMuted, maxLines = 2)
    }
}
