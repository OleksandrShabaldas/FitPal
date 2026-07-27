package com.fitpal.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.fitpal.shared.StatsSnapshot
import com.fitpal.wear.QuickLogActivity
import com.fitpal.wear.data.WearStats
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A quick-add water tile: shows today's water vs goal and two preset buttons that log straight to
 * the phone (via [QuickLogActivity], since a tile clickable can only launch an activity). The tile
 * reads the last synced [StatsSnapshot]; [com.fitpal.wear.WearListenerService] asks it to refresh
 * whenever the phone pushes a new snapshot.
 *
 * NOTE: the Tiles + ProtoLayout APIs churn between versions; if a builder signature differs in the
 * version Android Studio resolves, this is the file to reconcile.
 */
class WaterTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = CallbackToFutureAdapter.getFuture { completer ->
        val job = scope.launch {
            runCatching {
                val snapshot = WearStats.latest(this@WaterTileService)
                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setFreshnessIntervalMillis(0L) // refreshed on demand, not on a timer
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(snapshot)))
                    .build()
                completer.set(tile)
            }.onFailure { completer.setException(it) }
        }
        job.invokeOnCompletion { cause -> if (cause != null) completer.setException(cause) }
        "WaterTileRequest"
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = CallbackToFutureAdapter.getFuture { completer ->
        completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        "WaterTileResources"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun layout(snapshot: StatsSnapshot?): LayoutElementBuilders.LayoutElement {
        val cream = ColorBuilders.argb(0xFFF2E7D5.toInt())
        val water = snapshot?.waterMl ?: 0
        val goal = snapshot?.waterGoalMl ?: 2000
        val presets = snapshot?.waterPresets ?: listOf(250, 500)
        val first = presets.getOrElse(0) { 250 }
        val second = presets.getOrElse(1) { 500 }

        return LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, "Water")
                    .setColor(cream)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .build()
            )
            .addContent(
                Text.Builder(this, "$water / $goal ml")
                    .setColor(cream)
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .build()
            )
            .addContent(spacerH(12f))
            .addContent(
                LayoutElementBuilders.Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(waterButton(first))
                    .addContent(spacerW(8f))
                    .addContent(waterButton(second))
                    .build()
            )
            .build()
    }

    private fun waterButton(ml: Int): LayoutElementBuilders.LayoutElement =
        Button.Builder(this, quickLogClickable(ml))
            .setTextContent("+$ml")
            .build()

    private fun quickLogClickable(ml: Int): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId("water_$ml")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(QuickLogActivity::class.java.name)
                            .addKeyToExtraMapping(
                                QuickLogActivity.EXTRA_ML,
                                ActionBuilders.AndroidIntExtra.Builder().setValue(ml).build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    private fun spacerH(dp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(dp)).build()

    private fun spacerW(dp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(dp)).build()

    companion object {
        private const val RESOURCES_VERSION = "1"
    }
}
