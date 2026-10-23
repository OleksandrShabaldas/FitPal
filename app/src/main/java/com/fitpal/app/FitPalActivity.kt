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
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
class FitPalActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var updateManager: UpdateManager
    @Inject lateinit var focusedDate: com.fitpal.app.domain.FocusedDate
    @Inject lateinit var appForeground: com.fitpal.app.ml.AppForegroundState

    // A route a notification / widget / share asked us to open.
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The look before anything draws (Hilt injects in super.onCreate) — with this activity's own
        // light/dark configuration, so a cold start never flashes the wrong colours.
        applyLook(settingsRepository.appearance.value)
        enableEdgeToEdge()
        lifecycleScope.launch {
            settingsRepository.appearance.collect { applyLook(it) }
        }
        pendingRoute = routeFor(intent)
        // First launch (and no shared/notification route) → run onboarding to set accurate targets.
        val startOnboarding = !settingsRepository.hasOnboarded.value && pendingRoute == null
        setContent {
            FitPalTheme {
                // Liquid glass's tilt-lit rims (idle in every other look).
                com.fitpal.app.ui.component.TiltLight()
                // The three online model slots, so every AI badge can say "Fallback 2" instead of
                // spelling out a model id. Blanks are kept so slot numbers match Settings' labels.
                val model1 by settingsRepository.geminiModel.collectAsStateWithLifecycle()
                val model2 by settingsRepository.geminiModel2.collectAsStateWithLifecycle()
                val model3 by settingsRepository.geminiModel3.collectAsStateWithLifecycle()
                // Fasting schedule, so the Home strip and the log-time warning share one source.
                val fastingSchedule by settingsRepository.fastingSchedule.collectAsStateWithLifecycle()
                // The "I ate earlier" allowance for the fasting warning — remembered so a FitPalActivity
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
                    val navConfig by settingsRepository.navConfig.collectAsStateWithLifecycle()
                    val trailOn by settingsRepository.trailEnabled.collectAsStateWithLifecycle()
                    val coachOn by settingsRepository.coachEnabled.collectAsStateWithLifecycle()
                    FitPalNavHost(
                        // A feature's tab goes with the feature (Settings → Features), and comes back with it.
                        navConfig = navConfig.copy(
                            tabs = navConfig.tabs.filter {
                                (it != com.fitpal.app.domain.model.NavTab.TRAIL || trailOn) &&
                                    (it != com.fitpal.app.domain.model.NavTab.COACH || coachOn)
                            }
                        ),
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

    /**
     * Every screen FitPal opens in another app — the photo picker, a file picker, the mail app —
     * goes through here (`startActivity` does too). Coming back from one of those isn't a fresh
     * visit, so things shown "once per visit" (the intense-fasting reminder) don't come back.
     */
    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        appForeground.markExternalTrip()
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    private fun applyLook(appearance: com.fitpal.app.ui.theme.Appearance) {
        val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        com.fitpal.app.ui.theme.LookState.apply(appearance, this, systemDark = night == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        window?.decorView?.setBackgroundColor(com.fitpal.app.ui.theme.LookState.colors.inkBlack.toArgb())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFor(intent)?.let { pendingRoute = it }
    }

    private fun routeFor(intent: Intent?): String? {
        if (intent == null) return null
        // A widget showing today's numbers was tapped: bring Home (and Analytics) back to today, even
        // if they were left browsing another day.
        if (intent.getBooleanExtra(EXTRA_FOCUS_TODAY, false)) focusedDate.set(java.time.LocalDate.now())
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (uri != null) return Screen.Analysis.buildRoute(uri.toString())
        }
        // A weigh-in notification posted by an older version still carries the old "open Home, then
        // pop the dialog" action — send it to the weigh-in destination like the new ones.
        if (intent.getStringExtra(EXTRA_HOME_ACTION) == HOME_ACTION_LOG_WEIGHT) return Screen.WeighIn.route
        return intent.getStringExtra(EXTRA_NAV_ROUTE)
    }

    companion object {
        const val EXTRA_NAV_ROUTE = "fitpal.nav_route"
        /** Set by widgets that show today: open on today rather than whatever day was being browsed. */
        const val EXTRA_FOCUS_TODAY = "fitpal.focus_today"
        /** Legacy: older weigh-in notifications asked Home to open its weight dialog (see [routeFor]). */
        private const val EXTRA_HOME_ACTION = "fitpal.home_action"
        private const val HOME_ACTION_LOG_WEIGHT = "log_weight"
    }
}
