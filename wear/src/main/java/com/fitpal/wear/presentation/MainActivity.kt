package com.fitpal.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fitpal.wear.StepsSync
import com.fitpal.wear.presentation.theme.FitPalWearTheme

/** The watch app's single activity. Compose + Wear navigation draw everything. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // (Re-)arm the passive daily-steps stream + flush a fresh count to the phone. No-op
        // until the user grants activity recognition via the home-screen chip.
        StepsSync.register(this)
        setContent {
            FitPalWearTheme {
                WearApp()
            }
        }
    }
}
