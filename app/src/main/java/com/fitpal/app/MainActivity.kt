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
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.ui.navigation.FitPalNavHost
import com.fitpal.app.ui.navigation.Screen
import com.fitpal.app.ui.theme.FitPalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

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
