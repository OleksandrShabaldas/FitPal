package com.fitpal.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.fitpal.app.ui.screen.widgets.WidgetConfigScreen
import com.fitpal.app.ui.theme.FitPalTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The launcher opens this when a FitPal widget is added (and from the widget's long-press menu on
 * Android 12+): pick its look, see it live with your own numbers, done. Backing out of a fresh add
 * cancels it, as the launcher expects.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var dataSource: WidgetDataSource
    @Inject lateinit var updater: WidgetUpdater

    private var data by mutableStateOf(WidgetData.sample())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val manager = AppWidgetManager.getInstance(this)
        val providerClass = manager.getAppWidgetInfo(id)?.provider?.className
        val kind = WidgetKind.entries.firstOrNull { it.provider.name == providerClass }
        if (kind == null) {
            finish()
            return
        }
        // A widget being restyled keeps its current look selected; one pinned from the gallery arrives
        // with the look picked there.
        val initial = WidgetPrefs.peekPendingPinStyle(this, kind) ?: WidgetPrefs.style(this, id)
        val size = WidgetRender.sizeOf(this, manager, id, kind).let {
            if (it.width < 60f || it.height < 40f) SizeF(kind.defaultWidthDp.toFloat(), kind.defaultHeightDp.toFloat()) else it
        }
        lifecycleScope.launch { runCatching { dataSource.load(setOf(kind)) }.getOrNull()?.let { data = it } }

        setContent {
            FitPalTheme {
                WidgetConfigScreen(
                    kind = kind,
                    size = size,
                    data = data,
                    initial = initial,
                    onDone = { style ->
                        WidgetPrefs.setStyle(this, id, style)
                        WidgetPrefs.takePendingPinStyle(this, kind)
                        lifecycleScope.launch {
                            runCatching {
                                updater.update(kind, intArrayOf(id))
                                updater.startWatching()
                            }
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        }
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}
