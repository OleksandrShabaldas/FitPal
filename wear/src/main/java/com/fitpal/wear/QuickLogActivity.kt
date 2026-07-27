package com.fitpal.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.fitpal.wear.data.PhoneComms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Invisible activity the water **tile** launches to quick-log a fixed amount (tiles can only launch
 * an Activity, not fire a broadcast, so this is the tile's equivalent of [QuickLogReceiver]). It
 * messages the phone and finishes immediately — no UI. Complication taps use the broadcast instead.
 */
class QuickLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ml = intent.getIntExtra(EXTRA_ML, 0)
        if (ml > 0) {
            val app = applicationContext
            // App-scoped (not tied to this finishing activity) so the send completes.
            CoroutineScope(Dispatchers.IO).launch {
                PhoneComms.logWater(app, ml)
                PhoneComms.requestStats(app)
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_ML = "ml"
    }
}
