package com.fitpal.app.ui.component.kit

import android.provider.Settings
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.LookState

/**
 * One line of text that slides sideways to show the rest when it doesn't fit (the 17 Nov review, #9: the
 * meal's name and the dish names). It keeps sliding, at the platform's reading speed, with a pause before
 * each loop, and fades at the right edge so the cut never looks like a mistake. Text that fits is just text.
 *
 * Still with Reduce motion — FitPal's own (Settings → Appearance) or the phone's "Remove animations" —
 * where it ends in "…" instead.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val systemOff = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    val still = LookState.appearance.reduceMotion || systemOff
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val fits = remember(text, style, width) {
            measurer.measure(text, style, maxLines = 1, softWrap = false).size.width <= width
        }
        if (fits || still) {
            Text(text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        } else {
            Text(
                text, style = style, color = color, maxLines = 1, softWrap = false,
                modifier = Modifier
                    .fadeEnd()
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 1_200,
                        repeatDelayMillis = 1_500,
                        spacing = MarqueeSpacing(40.dp)
                    )
            )
        }
    }
}

/** Fades the last few dp of what it draws to transparent (a mask, so it works over any backdrop). */
private fun Modifier.fadeEnd(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = 20.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black, 1f to Color.Transparent,
                startX = size.width - fade, endX = size.width
            ),
            blendMode = BlendMode.DstIn
        )
    }
