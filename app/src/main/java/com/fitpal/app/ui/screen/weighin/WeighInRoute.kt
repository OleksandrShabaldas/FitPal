package com.fitpal.app.ui.screen.weighin

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.WeighInCard
import java.util.Locale

/**
 * The weigh-in as its own navigation destination (a dialog over whatever screen is behind it). The
 * weigh-in notification deep-links straight here, so tapping it always lands on the weigh-in — it no
 * longer relies on Home noticing a one-off flag, which could get lost on the way in.
 */
@Composable
fun WeighInRoute(
    onDone: () -> Unit,
    viewModel: WeighInViewModel = hiltViewModel()
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    data?.let { d ->
        WeighInCard(
            latest = d.latest,
            previous = d.previous,
            goal = d.goal,
            onSave = { kg ->
                viewModel.save(kg) {
                    Toast.makeText(context, "Saved ${String.format(Locale.US, "%.1f", kg)} kg", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            },
            onCancel = onDone,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
