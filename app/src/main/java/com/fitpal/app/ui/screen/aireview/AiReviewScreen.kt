package com.fitpal.app.ui.screen.aireview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.model.ContextQuestion
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.MarkdownText
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.ScoreGreen
import com.fitpal.app.ui.theme.ScoreRed
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

@Composable
fun AiReviewScreen(
    onBack: () -> Unit,
    viewModel: AiReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GradientBackdrop(theme = BackdropTheme.TRENDS) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = state.title.ifEmpty { "AI overview" },
                onBack = onBack,
                actions = {
                    if (state.review != null && state.modelReady) {
                        Box(
                            modifier = Modifier.size(40.dp).glass(CircleShape).clickable { viewModel.regenerate() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = Cream, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.contextQuestion != null -> Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        ContextQuestionCard(
                            question = state.contextQuestion!!,
                            onAnswer = viewModel::answerQuestion,
                            onSkip = viewModel::skipQuestion
                        )
                    }

                    state.isLoading -> Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Thinking about your nutrition…", style = MaterialTheme.typography.bodyLarge, color = Cream)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.progress.ifBlank { "Uses the online AI when available, or your phone's model offline." },
                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                        )
                    }

                    state.review != null -> Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
                    ) {
                        if (state.subtitle.isNotEmpty()) {
                            Text(state.subtitle, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                            Spacer(Modifier.height(12.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Your AI nutrition coach", style = MaterialTheme.typography.titleSmall, color = Cream)
                            state.aiSource?.let {
                                Spacer(Modifier.width(8.dp))
                                AiSourceBadge(it)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                            // The model writes Markdown — headings, bold, bullets. Render it.
                            MarkdownText(
                                markdown = state.review!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Cream
                            )
                        }

                        // The coach's two takeaway cards: one thing to DO (green) + one to WATCH (red).
                        if (state.focus != null || state.watch != null) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                state.focus?.let {
                                    CoachActionCard(
                                        accent = ScoreGreen,
                                        label = "Focus next",
                                        icon = Icons.Filled.TrendingUp,
                                        body = it,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                state.watch?.let {
                                    CoachActionCard(
                                        accent = ScoreRed,
                                        label = "Keep an eye on",
                                        icon = Icons.Filled.Visibility,
                                        body = it,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    else -> Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(state.error ?: "No overview available.", style = MaterialTheme.typography.bodyLarge, color = CreamMuted)
                        if (state.modelReady) {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.glass(CircleShape).clickable { viewModel.regenerate() }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Try again", style = MaterialTheme.typography.labelLarge, color = Cream)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One of the coach's two takeaway cards — a tinted glass panel with an accent icon + label and the
 * one-sentence action. Green ("Focus next") for what to do, red ("Keep an eye on") for what to watch;
 * both parsed from the review's FOCUS:/WATCH: lines. Mirrors [com.fitpal.app.ui.component.CoachingTipCard]'s style.
 */
@Composable
private fun CoachActionCard(
    accent: Color,
    label: String,
    icon: ImageVector,
    body: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.10f), shape)
            .border(1.dp, accent.copy(alpha = 0.30f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(30.dp).background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Cream)
    }
}

/**
 * The pre-review check-in: one AI-written question with tappable answer chips, or a quiet "Skip".
 * The chosen answer is remembered and folded into this review (and future ones).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextQuestionCard(
    question: ContextQuestion,
    onAnswer: (String) -> Unit,
    onSkip: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().glass().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("A quick check-in", style = MaterialTheme.typography.titleSmall, color = Cream)
        }
        Spacer(Modifier.height(10.dp))
        Text(question.question, style = MaterialTheme.typography.bodyLarge, color = Cream)
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            question.options.forEach { option ->
                Box(
                    modifier = Modifier
                        .glassSoft(CircleShape)
                        .clickable { onAnswer(option) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(option, style = MaterialTheme.typography.labelLarge, color = Cream)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Skip",
            style = MaterialTheme.typography.labelLarge,
            color = CreamMuted,
            modifier = Modifier.clickable { onSkip() }.padding(vertical = 4.dp, horizontal = 4.dp)
        )
    }
}
