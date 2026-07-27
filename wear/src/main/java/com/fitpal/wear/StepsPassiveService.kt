package com.fitpal.wear

import android.os.SystemClock
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.fitpal.wear.data.PhoneComms
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId

/**
 * Receives the watch's STEPS_DAILY updates from Health Services (batched, battery-friendly) and
 * forwards the freshest count per day to the phone over the Data Layer. The phone stores it as the
 * day's step floor — max(Health Connect, this) — so watch steps count even when Samsung Health
 * never writes them into Health Connect. Registered by [StepsSync].
 */
class StepsPassiveService : PassiveListenerService() {

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val points = dataPoints.getData(DataType.STEPS_DAILY)
        if (points.isEmpty()) return
        // Date each point by its own END time (not "now") — a batch delivered just after midnight
        // can still carry yesterday's final total, which must not become today's floor.
        val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
        val zone = ZoneId.systemDefault()
        // STEPS_DAILY values are running day totals, so per day only the largest matters.
        val bestByDate = HashMap<String, Long>()
        points.forEach { point ->
            val date = point.getEndInstant(bootInstant).atZone(zone).toLocalDate().toString()
            val prev = bestByDate[date] ?: 0L
            if (point.value > prev) bestByDate[date] = point.value
        }
        // Quick local Bluetooth sends; fine to block this short-lived callback. If the phone is
        // out of reach the report is simply dropped — the next batch (or app open) retries.
        runCatching {
            runBlocking {
                bestByDate.forEach { (date, steps) ->
                    if (steps > 0L) PhoneComms.sendSteps(this@StepsPassiveService, "$date|$steps")
                }
            }
        }
    }
}
