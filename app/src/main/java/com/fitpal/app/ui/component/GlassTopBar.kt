package com.fitpal.app.ui.component

import com.fitpal.app.ui.component.kit.MarqueeText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.fitpal.app.ui.component.kit.LocalTopBarScroll
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.glass

/**
 * A screen's top bar, drawn per look:
 *  - Glow / Paper: transparent, a glass round back button and a serif title.
 *  - Liquid glass: an iPhone large title (34 sp, bold, on the left) that shrinks into a small
 *    centred title as the content scrolls; a glass capsule back button.
 *  - Material You: Material's collapsing top app bar (medium; large on a screen without a back
 *    arrow), tinted as content slides under it.
 * Liquid and Material collapse through [LocalTopBarScroll], which the screen's backdrop provides.
 *
 * [onTitleClick] makes the title tappable with a small pencil after it (a meal's name, renamed in
 * place); [titleMaxLines] lets a long one take two lines in Glow and Paper (docs/redesign/16); [marquee]
 * keeps it on one line that slides to show the rest, in every look (the meal page, the 17 Nov review, #9).
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    titleMaxLines: Int = 1,
    onTitleClick: (() -> Unit)? = null,
    marquee: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    when (LookState.style) {
        LookStyle.LIQUID -> LiquidTopBar(title, modifier, onBack, onTitleClick, marquee, actions)
        LookStyle.MATERIAL -> MaterialTopBar(title, modifier, onBack, onTitleClick, marquee, actions)
        else -> Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier.size(40.dp).glass(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Cream,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
            }
            // One line unless the screen asks for two: titles can be user-written (a named meal).
            Row(
                Modifier.weight(1f).then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (marquee) {
                    MarqueeText(title, MaterialTheme.typography.headlineSmall, Cream, Modifier.weight(1f, fill = false))
                } else {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Cream,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                TitlePencil(title, onTitleClick)
            }
            actions()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiquidTopBar(title: String, modifier: Modifier, onBack: (() -> Unit)?, onTitleClick: (() -> Unit)?, marquee: Boolean, actions: @Composable RowScope.() -> Unit) {
    val behavior = LocalTopBarScroll.current
    // With a back button the large title sits under the button row and the bar loses that row as
    // it collapses; without one (a tab's root) the large title simply turns into the small one.
    val top = 52.dp
    val large = if (onBack != null) 48.dp else 54.dp
    val collapsedHeight = if (onBack != null) top else 44.dp
    val expandedHeight = if (onBack != null) top + large else large
    val range = expandedHeight - collapsedHeight
    val rangePx = with(LocalDensity.current) { range.toPx() }
    if (behavior != null) {
        SideEffect { if (behavior.state.heightOffsetLimit != -rangePx) behavior.state.heightOffsetLimit = -rangePx }
    }
    val f = behavior?.state?.collapsedFraction ?: 0f
    val height = lerp(expandedHeight, collapsedHeight, f)
    Box(modifier.fillMaxWidth().height(height).padding(horizontal = 16.dp)) {
        // The small centred title, with the back button and the actions around it.
        Row(Modifier.fillMaxWidth().height(if (onBack != null) top else collapsedHeight), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    modifier = Modifier.size(40.dp).glass(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Back",
                        tint = Cream,
                        modifier = Modifier.size(18.dp).padding(start = 4.dp)
                    )
                }
            }
            Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                val smallAlpha = Modifier.alpha(((f - 0.55f) / 0.45f).coerceIn(0f, 1f))
                if (marquee) {
                    MarqueeText(title, MaterialTheme.typography.titleMedium, Cream, smallAlpha)
                } else {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Cream,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = smallAlpha
                    )
                }
            }
            actions()
        }
        // The large title.
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 4.dp, bottom = 4.dp)
                .alpha((1f - f * 1.7f).coerceIn(0f, 1f))
                .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (marquee) {
                MarqueeText(title, MaterialTheme.typography.headlineLarge, Cream, Modifier.weight(1f, fill = false))
            } else {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            TitlePencil(title, onTitleClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialTopBar(title: String, modifier: Modifier, onBack: (() -> Unit)?, onTitleClick: (() -> Unit)?, marquee: Boolean, actions: @Composable RowScope.() -> Unit) {
    val behavior = LocalTopBarScroll.current
    val scheme = MaterialTheme.colorScheme
    val colors = TopAppBarDefaults.mediumTopAppBarColors(
        containerColor = scheme.surface,
        scrolledContainerColor = scheme.surfaceContainer,
        navigationIconContentColor = scheme.onSurface,
        titleContentColor = scheme.onSurface,
        actionIconContentColor = scheme.onSurfaceVariant
    )
    val titleText: @Composable () -> Unit = {
        Row(
            Modifier.then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (marquee) {
                MarqueeText(title, androidx.compose.material3.LocalTextStyle.current, androidx.compose.material3.LocalContentColor.current, Modifier.weight(1f, fill = false))
            } else {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            TitlePencil(title, onTitleClick)
        }
    }
    val nav: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }
    }
    val row: @Composable RowScope.() -> Unit = {
        actions()
        Spacer(Modifier.width(8.dp))
    }
    // The screens already sit below the status bar, so the bar adds no insets of its own.
    if (onBack == null) {
        LargeTopAppBar(
            title = titleText, modifier = modifier, navigationIcon = nav, actions = row,
            windowInsets = WindowInsets(0), colors = colors, scrollBehavior = behavior
        )
    } else {
        MediumTopAppBar(
            title = titleText, modifier = modifier, navigationIcon = nav, actions = row,
            windowInsets = WindowInsets(0), colors = colors, scrollBehavior = behavior
        )
    }
}

/** The small pencil after a title that can be renamed by tapping it. */
@Composable
private fun TitlePencil(title: String, onTitleClick: (() -> Unit)?) {
    if (onTitleClick == null) return
    Spacer(Modifier.width(8.dp))
    Icon(Icons.Default.Edit, contentDescription = "Rename $title", tint = CreamMuted, modifier = Modifier.size(16.dp))
}
