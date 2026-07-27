package com.fitpal.wear

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.fitpal.shared.WearContract
import com.fitpal.wear.complication.CaloriesComplicationService
import com.fitpal.wear.complication.WaterComplicationService
import com.fitpal.wear.tile.WaterTileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/**
 * Watch-side Data Layer listener. When the phone pushes a fresh stats snapshot, it refreshes the
 * water tile and both complications so they show current numbers without the watch app being open.
 */
class WearListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val statsChanged = dataEvents.any {
            it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WearContract.PATH_STATS
        }
        if (!statsChanged) return

        runCatching { TileService.getUpdater(this).requestUpdate(WaterTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(this, ComponentName(this, WaterComplicationService::class.java))
                .requestUpdateAll()
        }
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(this, ComponentName(this, CaloriesComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}
