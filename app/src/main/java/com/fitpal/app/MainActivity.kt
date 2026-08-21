package com.fitpal.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.ui.component.FastingBackdate
import com.fitpal.app.ui.component.LocalAiModelSlots
import com.fitpal.app.ui.component.LocalFastingBackdate
import com.fitpal.app.ui.component.LocalFastingSchedule
import com.fitpal.app.ui.component.UpdatePromptDialog
import com.fitpal.app.ui.navigation.FitPalNavHost
import com.fitpal.app.ui.navigation.Screen
import com.fitpal.app.ui.theme.FitPalTheme
import com.fitpal.app.update.UpdateManager
import com.fitpal.app.update.UpdatePhase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var updateManager: UpdateManager

    // A route a notification / widget / share asked us to open.
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = routeFor(intent)
        // First launch (and no shared/notification route) → run onboarding to set accurate targets.
        val startOnboarding = !settingsRepository.hasOnboarded.value && pendingRoute == null
        setContent {
            FitPalTheme {
                // The three online model slots, so every AI badge can say "Fallback 2" instead of
                // spelling out a model id. Blanks are kept so slot numbers match Settings' labels.
                val model1 by settingsRepository.geminiModel.collectAsStateWithLifecycle()
                val model2 by settingsRepository.geminiModel2.collectAsStateWithLifecycle()
                val model3 by settingsRepository.geminiModel3.collectAsStateWithLifecycle()
                // Fasting schedule, so the Home strip and the log-time warning share one source.
                val fastingSchedule by settingsRepository.fastingSchedule.collectAsStateWithLifecycle()
                // The "I ate earlier" allowance for the fasting warning — remembered so a MainActivity
                // recomposition doesn't hand the guard a fresh instance and dismiss an open dialog.
                val fastingGrace by settingsRepository.fastingGrace.collectAsStateWithLifecycle()
                val fastingBackdate = remember(fastingGrace) {
                    val month = java.time.LocalDate.now().toString().take(7)
                    val used = fastingGrace.keys.count { it.take(7) == month }
                    FastingBackdate((SettingsRepository.FASTING_BACKDATE_LIMIT - used).coerceAtLeast(0)) { date, min ->
                        settingsRepository.recordFastingGrace(date, min)
                    }
                }
                CompositionLocalProvider(
                    LocalAiModelSlots provides listOf(model1, model2, model3),
                    LocalFastingSchedule provides fastingSchedule,
                    LocalFastingBackdate provides fastingBackdate
                ) {
                    FitPalNavHost(
                        pendingRoute = pendingRoute,
                        onPendingRouteHandled = { pendingRoute = null },
                        startOnboarding = startOnboarding
                    )
                }

                // "A new version is out" prompt, over whichever screen is showing. Only for a
                // version the user hasn't skipped, and never during first-run onboarding.
                val updateState by updateManager.state.collectAsStateWithLifecycle()
                val skipped by settingsRepository.skippedUpdateVersion.collectAsStateWithLifecycle()
                val show = !startOnboarding &&
                    updateState.available != null &&
                    updateState.available?.version != skipped &&
                    updateState.phase != UpdatePhase.IDLE &&
                    updateState.phase != UpdatePhase.UP_TO_DATE
                if (show) {
                    UpdatePromptDialog(
                        state = updateState,
                        onUpdate = {
                            if (updateState.phase == UpdatePhase.READY_PHONE) updateManager.installDownloadedPhoneApk()
                            else updateManager.downloadAndInstallPhone()
                        },
                        onUpdateWatch = { updateManager.updateWatch() },
                        onSkipVersion = { updateManager.skipVersion() },
                        onDismiss = { updateManager.dismiss() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFor(intent)?.let { pendingRoute = it }
    }

    private fun routeFor(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (uri != null) return Screen.Analysis.buildRoute(uri.toString())
        }
        return intent.getStringExtra(EXTRA_NAV_ROUTE)
    }

    companion object {
        const val EXTRA_NAV_ROUTE = "fitpal.nav_route"
    }
}
