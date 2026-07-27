package com.fitpal.app.ui.screen.aireview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass

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
                            Text(state.review!!, style = MaterialTheme.typography.bodyMedium, color = Cream)
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
