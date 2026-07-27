package com.fitpal.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig

/**
 * Registers the watch's passive daily-step stream ([StepsPassiveService]) with Health Services.
 *
 * Why this exists: Samsung Health doesn't reliably write the WATCH's steps into the phone's
 * Health Connect (or writes them hours late), so the phone's step sync used to under-count for
 * watch-heavy days. Reading STEPS_DAILY here — the same counter Samsung Health uses on-watch —
 * and reporting it to the phone makes the watch a first-class source that can't go missing.
 *
 * Registration is idempotent; call it freely on app start. Requires ACTIVITY_RECOGNITION,
 * which the watch app asks for with a chip on the home screen.
 */
object StepsSync {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Point Health Services' passive daily-steps stream at [StepsPassiveService], then flush so a
     * current value is delivered soon (instead of waiting for the next batch). Safe to repeat.
     */
    fun register(context: Context) {
        if (!hasPermission(context)) return
        runCatching {
            val client = HealthServices.getClient(context.applicationContext).passiveMonitoringClient
            val config = PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.STEPS_DAILY))
                .build()
            client.setPassiveListenerServiceAsync(StepsPassiveService::class.java, config)
            // Ask for any batched-but-undelivered data now, so the phone gets a fresh count.
            client.flushAsync()
        }
    }
}
