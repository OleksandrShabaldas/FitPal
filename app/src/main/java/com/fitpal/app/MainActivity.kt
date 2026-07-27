package com.fitpal.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.repository.SettingsRepository
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
                FitPalNavHost(
                    pendingRoute = pendingRoute,
                    onPendingRouteHandled = { pendingRoute = null },
                    startOnboarding = startOnboarding
                )

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
