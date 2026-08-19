package com.fitpal.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.fitpal.shared.FastingWindow
import com.fitpal.shared.StatsSnapshot
import com.fitpal.wear.data.WearStats
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * An at-a-glance fasting tile: whether you're fasting or inside your eating window right now, and how
 * long is left. It reads the schedule mirrored onto the last [StatsSnapshot] and computes the phase +
 * countdown from the watch's own clock via [FastingWindow] — so nothing time-varying goes over the
 * wire. [com.fitpal.wear.WearListenerService] refreshes it when the phone pushes a new snapshot; the
 * freshness interval ticks the countdown roughly each minute.
 *
 * NOTE: the Tiles + ProtoLayout APIs churn between versions; if a builder signature differs in the
 * version Android Studio resolves, this (and [WaterTileService]) is the file to reconcile.
 */
class FastingTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = CallbackToFutureAdapter.getFuture { completer ->
        val job = scope.launch {
            runCatching {
                val snapshot = WearStats.latest(this@FastingTileService)
                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setFreshnessIntervalMillis(60_000L) // tick the countdown about once a minute
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(snapshot)))
                    .build()
                completer.set(tile)
            }.onFailure { completer.setException(it) }
        }
        job.invokeOnCompletion { cause -> if (cause != null) completer.setException(cause) }
        "FastingTileRequest"
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = CallbackToFutureAdapter.getFuture { completer ->
        completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        "FastingTileResources"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun layout(snapshot: StatsSnapshot?): LayoutElementBuilders.LayoutElement {
        val cream = ColorBuilders.argb(0xFFF2E7D5.toInt())
        val gold = ColorBuilders.argb(0xFFF3CE7C.toInt())
        val blue = ColorBuilders.argb(0xFF6FA8FF.toInt())

        val col = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        if (snapshot == null || !snapshot.fastingEnabled) {
            col.addContent(
                Text.Builder(this, "Fasting off").setColor(cream).setTypography(Typography.TYPOGRAPHY_TITLE3).build()
            )
            col.addContent(spacerH(4f))
            col.addContent(
                Text.Builder(this, "Turn it on in the app").setColor(cream).setTypography(Typography.TYPOGRAPHY_CAPTION2).build()
            )
            return col.build()
        }

        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val state = FastingWindow.stateAt(snapshot.fastEatStartMin, snapshot.fastEatEndMin, nowMin)
        val heading = if (state.isEating) "Eating window" else "Fasting"
        val accent = if (state.isEating) gold else blue

        col.addContent(
            Text.Builder(this, heading).setColor(accent).setTypography(Typography.TYPOGRAPHY_CAPTION1).build()
        )
        col.addContent(spacerH(4f))
        col.addContent(
            Text.Builder(this, "${countdown(state.minutesLeftInPhase)} left")
                .setColor(cream).setTypography(Typography.TYPOGRAPHY_TITLE2).build()
        )
        col.addContent(spacerH(4f))
        col.addContent(
            Text.Builder(this, (if (state.isEating) "Closes " else "Opens ") + clock(state.nextChangeMin))
                .setColor(cream).setTypography(Typography.TYPOGRAPHY_CAPTION2).build()
        )
        return col.build()
    }

    private fun countdown(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    private fun clock(minutes: Int): String {
        val mm = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
        val h = mm / 60
        val hr12 = ((h + 11) % 12) + 1
        return "$hr12:${(mm % 60).toString().padStart(2, '0')} ${if (h < 12) "AM" else "PM"}"
    }

    private fun spacerH(dp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(dp)).build()

    companion object {
        private const val RESOURCES_VERSION = "1"
    }
}
