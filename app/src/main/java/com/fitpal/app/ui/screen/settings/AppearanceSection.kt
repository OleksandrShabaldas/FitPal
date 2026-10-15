package com.fitpal.app.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.theme.Appearance
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.Look
import com.fitpal.app.ui.theme.LookMode
import com.fitpal.app.ui.theme.LookPalette
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

/**
 * Settings → Appearance: the look (style, light/dark, palette) and its fine-tuning. Changes apply
 * live — the whole app, this screen included, re-colours as you tap. Each tile previews its look in
 * miniature, drawn from that look's own colours rather than the current tokens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceLookSection(viewModel: SettingsViewModel) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Look", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Cream)
        Text(
            "How the whole app is drawn. Tap one — it changes right away.",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted
        )
        Spacer(Modifier.height(12.dp))
        LookStyle.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { style ->
                    val preview = remember(appearance, style, systemDark) {
                        LookState.resolve(appearance.copy(style = style), context, systemDark)
                    }
                    StyleTile(
                        look = preview,
                        selected = appearance.style == style,
                        onClick = { viewModel.updateAppearance { it.copy(style = style) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (appearance.style.hasLight && appearance.style.hasDark) {
            Spacer(Modifier.height(4.dp))
            Text("Light or dark", style = MaterialTheme.typography.labelMedium, color = GoldLight)
            Spacer(Modifier.height(6.dp))
            val modes = listOf(LookMode.SYSTEM, LookMode.LIGHT, LookMode.DARK)
            SegmentedPills(
                labels = modes.map { it.label },
                selectedIndex = modes.indexOf(appearance.mode).coerceAtLeast(0),
                accent = GoldLight,
                onSelect = { i -> viewModel.updateAppearance { it.copy(mode = modes[i]) } }
            )
            Spacer(Modifier.height(12.dp))
        }

        val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (appearance.style == LookStyle.MATERIAL && dynamicAvailable) {
            ToggleRow(
                title = "Colours from your wallpaper",
                blurb = "Material You picks the accent from your home-screen wallpaper.",
                checked = appearance.dynamicColor,
                onChange = { on -> viewModel.updateAppearance { it.copy(dynamicColor = on) } }
            )
        }
        val paletteMatters = !(appearance.style == LookStyle.MATERIAL && appearance.dynamicColor && dynamicAvailable)
        if (paletteMatters) {
            Text("Colours", style = MaterialTheme.typography.labelMedium, color = GoldLight)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LookPalette.entries.forEach { p ->
                    PaletteDot(
                        palette = p,
                        selected = appearance.palette == p,
                        onClick = { viewModel.updateAppearance { it.copy(palette = p) } }
                    )
                }
            }
        }
    }
}

/** The fine-tuning knobs — what each look shows depends on what it has (grain, glow…). */
@Composable
internal fun AppearanceTuningSection(viewModel: SettingsViewModel) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val style = appearance.style
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Fine-tune", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Cream)
        Spacer(Modifier.height(8.dp))
        if (style == LookStyle.GLOW || style == LookStyle.LIQUID) {
            SliderRow("Glow", "How bright the light behind the cards is.", appearance.glow, 0.4f..1.6f) { v ->
                viewModel.updateAppearance { it.copy(glow = v) }
            }
        }
        SliderRow("Card opacity", "See-through or solid.", appearance.cardOpacity, 0.5f..2f) { v ->
            viewModel.updateAppearance { it.copy(cardOpacity = v) }
        }
        SliderRow("Corner rounding", "Squarer or rounder cards and buttons.", appearance.roundness, 0.4f..1.4f) { v ->
            viewModel.updateAppearance { it.copy(roundness = v) }
        }
        if (style == LookStyle.GLOW || style == LookStyle.PAPER) {
            ToggleRow("Grain", "A fine film-grain texture over the background.", appearance.grain) { on ->
                viewModel.updateAppearance { it.copy(grain = on) }
            }
        }
        ToggleRow("Compact", "Tighter spacing, so more fits on screen.", appearance.compact) { on ->
            viewModel.updateAppearance { it.copy(compact = on) }
        }
        ToggleRow("Reduce motion", "No drifting colour, tilt light or wiggling cards.", appearance.reduceMotion) { on ->
            viewModel.updateAppearance { it.copy(reduceMotion = on) }
        }
        TextButton(onClick = { viewModel.updateAppearance { Appearance(style = it.style, palette = it.palette, mode = it.mode, dynamicColor = it.dynamicColor) } }) {
            Text("Reset fine-tuning")
        }
    }
}

@Composable
private fun StyleTile(look: Look, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .then(if (selected) Modifier.accentGlass(GoldLight, shape) else Modifier.glassSoft(shape))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LookThumbnail(look, Modifier.fillMaxWidth().aspectRatio(0.72f))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                look.style.label,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = if (selected) GoldLight else Cream
            )
        }
        Text(look.style.blurb, style = MaterialTheme.typography.labelSmall, color = CreamMuted, textAlign = TextAlign.Center)
    }
}

/**
 * A tiny phone in [look]'s colours: its backdrop, a card, the calorie ring and the tab bar. Drawn
 * from the look's own palette (not the global tokens), so every tile shows its own look.
 */
@Composable
fun LookThumbnail(look: Look, modifier: Modifier = Modifier) {
    val c = look.colors
    val spec = c.backdrops.values.first()
    Canvas(modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, c.cream.copy(alpha = 0.12f), RoundedCornerShape(14.dp))) {
        val w = size.width
        val h = size.height
        // Backdrop
        when (look.style) {
            LookStyle.GLOW -> {
                drawRect(Brush.radialGradient(spec.base, center = Offset(w * 0.78f, -h * 0.06f), radius = maxOf(w, h) * 1.15f))
                spec.orbs.forEach { o ->
                    val cc = Offset(w * o.cx, h * o.cy)
                    val r = minOf(w, h) * o.rFrac
                    drawCircle(Brush.radialGradient(listOf(o.color.copy(alpha = o.alpha), Color.Transparent), cc, r), r, cc)
                }
                drawRect(Color.Black, alpha = c.backdropShade)
            }
            LookStyle.LIQUID -> {
                drawRect(c.inkBlack)
                spec.orbs.forEach { o ->
                    val cc = Offset(w * o.cx, h * o.cy)
                    val r = minOf(w, h) * o.rFrac * 1.4f
                    drawCircle(Brush.radialGradient(listOf(o.color.copy(alpha = o.alpha), Color.Transparent), cc, r), r, cc)
                }
            }
            else -> drawRect(c.inkBlack)
        }
        val pad = w * 0.08f
        // Greeting line
        drawRoundRect(c.cream.copy(alpha = 0.85f), Offset(pad, h * 0.07f), Size(w * 0.45f, h * 0.035f), CornerRadius(6f))
        // Ring
        val ringR = w * 0.22f
        val ringC = Offset(w / 2f, h * 0.30f)
        val sw = w * 0.05f
        drawCircle(c.cream.copy(alpha = 0.12f), ringR, ringC, style = Stroke(sw))
        drawArc(
            c.goldLight, -90f, 250f, false,
            Offset(ringC.x - ringR, ringC.y - ringR), Size(ringR * 2, ringR * 2),
            style = Stroke(sw, cap = StrokeCap.Round)
        )
        // Card
        val cardTop = h * 0.52f
        val cardH = h * 0.22f
        val radius = when (look.style) {
            LookStyle.PAPER -> 6f; LookStyle.LIQUID -> 18f; LookStyle.MATERIAL -> 12f; LookStyle.GLOW -> 14f
        }
        drawRoundRect(c.glassFill, Offset(pad, cardTop), Size(w - pad * 2, cardH), CornerRadius(radius))
        if (c.glassBorder.alpha > 0f) {
            drawRoundRect(c.glassBorder, Offset(pad, cardTop), Size(w - pad * 2, cardH), CornerRadius(radius), style = Stroke(1.5f))
        }
        drawRoundRect(c.cream.copy(alpha = 0.7f), Offset(pad * 2, cardTop + cardH * 0.25f), Size(w * 0.4f, h * 0.025f), CornerRadius(4f))
        drawRoundRect(c.cream.copy(alpha = 0.4f), Offset(pad * 2, cardTop + cardH * 0.6f), Size(w * 0.3f, h * 0.025f), CornerRadius(4f))
        // Nav
        val navH = h * 0.10f
        val navTop = h - navH - pad * 0.8f
        if (look.style == LookStyle.MATERIAL || look.style == LookStyle.PAPER) {
            drawRect(c.glassFill, Offset(0f, h - navH - pad * 0.4f), Size(w, navH + pad))
        } else {
            drawRoundRect(c.cream.copy(alpha = 0.12f), Offset(pad, navTop), Size(w - pad * 2, navH), CornerRadius(navH / 2))
        }
        drawCircle(c.gold, navH * 0.42f, Offset(w / 2f, navTop + navH / 2f))
    }
}

@Composable
private fun PaletteDot(palette: LookPalette, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(palette.swatch), Color(palette.swatchDeep))))
                .border(if (selected) 2.5.dp else 1.dp, if (selected) Cream else Cream.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF1A1A1A), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(palette.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Cream else CreamMuted)
    }
}

@Composable
private fun ToggleRow(title: String, blurb: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Cream)
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: String, blurb: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Cream)
        Text(blurb, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
