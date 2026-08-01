package com.fitpal.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight

/**
 * Renders the small slice of Markdown that actually shows up in this app — GitHub release
 * notes and AI-written reviews. Without this the raw `**` and `#` characters leak into the UI.
 *
 * Supports: `#`/`##`/`###` headings, `**bold**`, `*italic*` / `_italic_`, `` `code` ``,
 * `-`/`*`/`•` bullets, `1.` numbered lists, `---` rules and blank-line paragraph spacing.
 * Anything else is shown as plain text, so unknown syntax degrades gracefully rather than
 * disappearing.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = CreamMuted,
    headingColor: Color = Cream,
    bulletColor: Color = GoldLight
) {
    val lines = markdown.replace("\r\n", "\n").split("\n")

    Column(modifier = modifier) {
        lines.forEachIndexed { index, raw ->
            val line = raw.trimEnd()
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> {
                    // Collapse runs of blank lines into a single gap.
                    if (index > 0 && lines[index - 1].isNotBlank()) Spacer(Modifier.height(8.dp))
                }

                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.HorizontalDivider(
                        color = Color.White.copy(alpha = 0.10f)
                    )
                    Spacer(Modifier.height(6.dp))
                }

                trimmed.startsWith("### ") -> Heading(
                    trimmed.removePrefix("### "),
                    MaterialTheme.typography.titleSmall, headingColor
                )

                trimmed.startsWith("## ") -> Heading(
                    trimmed.removePrefix("## "),
                    MaterialTheme.typography.titleMedium, headingColor
                )

                trimmed.startsWith("# ") -> Heading(
                    trimmed.removePrefix("# "),
                    MaterialTheme.typography.headlineSmall, headingColor
                )

                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") ->
                    Bullet("•", trimmed.drop(2), style, color, bulletColor)

                NUMBERED.matches(trimmed) -> {
                    val marker = NUMBERED.find(trimmed)!!.groupValues[1]
                    Bullet(marker, trimmed.removePrefix(marker).trimStart(), style, color, bulletColor)
                }

                else -> Text(
                    text = parseInline(trimmed),
                    style = style,
                    color = color,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

private val NUMBERED = Regex("^(\\d+[.)])\\s+.*")

@Composable
private fun Heading(text: String, style: TextStyle, color: Color) {
    Spacer(Modifier.height(6.dp))
    Text(text = parseInline(text), style = style, color = color)
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun Bullet(
    marker: String,
    text: String,
    style: TextStyle,
    color: Color,
    markerColor: Color
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = marker, style = style, color = markerColor)
        Spacer(Modifier.width(8.dp))
        Text(text = parseInline(text), style = style, color = color)
    }
}

/**
 * Inline emphasis. Unmatched markers are emitted literally rather than swallowed, so a stray
 * asterisk in ordinary prose can't eat the rest of the paragraph.
 */
fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '*' && text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(c); i++ }
            }
            c == '*' || c == '_' -> {
                val end = text.indexOf(c, i + 1)
                // Require non-empty content, and don't italicise snake_case mid-word.
                val validWordBoundary = c != '_' || (i == 0 || !text[i - 1].isLetterOrDigit())
                if (end > i + 1 && validWordBoundary) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(c); i++ }
            }
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(c); i++ }
            }
            else -> { append(c); i++ }
        }
    }
}
