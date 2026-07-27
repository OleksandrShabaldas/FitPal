package com.fitpal.app.ui.screen.customize

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.ReorderableColumn
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.glass

@Composable
fun CustomizeWidgetsScreen(
    onBack: () -> Unit,
    viewModel: CustomizeWidgetsViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Customize ${viewModel.screenTitle}", onBack = onBack)

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Long-press a card and drag to reorder it. Use the switch to show or hide it.",
                    style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                )
                Spacer(Modifier.height(12.dp))

                ReorderableColumn(
                    count = rows.size,
                    onMove = viewModel::move,
                    rowHeight = 66.dp,
                    modifier = Modifier.fillMaxWidth()
                ) { index, dragHandle, isDragging ->
                    val row = rows[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .glass()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth().then(dragHandle),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = if (isDragging) Cream else CreamFaint,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                row.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (row.enabled) Cream else CreamFaint
                            )
                        }
                        Switch(
                            checked = row.enabled,
                            onCheckedChange = { viewModel.toggle(row.key) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = viewModel::resetToDefault) {
                    Text("Reset to default order")
                }
            }
        }
    }
}
