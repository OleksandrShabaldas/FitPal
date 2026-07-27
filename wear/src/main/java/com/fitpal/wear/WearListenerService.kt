package com.fitpal.wear

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.fitpal.shared.WearContract
import com.fitpal.wear.complication.CaloriesComplicationService
import com.fitpal.wear.complication.WaterComplicationService
import com.fitpal.wear.tile.WaterTileService
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Watch-side Data Layer listener:
 *  - a fresh stats snapshot refreshes the water tile + both complications;
 *  - the phone can ask which app version is installed here (for its update card);
 *  - the phone can stream a new watch APK over a channel, which lands in [WatchUpdateInstaller].
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

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearContract.PATH_REQUEST_WATCH_VERSION -> {
                val version = runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName
                }.getOrNull().orEmpty()
                if (version.isNotEmpty()) {
                    runCatching {
                        runBlocking {
                            Wearable.getMessageClient(this@WearListenerService)
                                .sendMessage(event.sourceNodeId, WearContract.PATH_WATCH_VERSION, version.toByteArray())
                                .await()
                        }
                    }
                }
            }

            WearContract.PATH_APK_INCOMING -> {
                // Remember what's on the way so the install prompt can name the version.
                WatchUpdateInstaller.rememberIncomingVersion(this, String(event.data).trim())
            }
        }
    }

    /** The phone opened the APK channel — pull the bytes into a file we can hand to the installer. */
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != WearContract.PATH_APK_CHANNEL) return
        val client = Wearable.getChannelClient(applicationContext)
        runCatching {
            runBlocking {
                val target = WatchUpdateInstaller.stagingFile(this@WearListenerService)
                if (target.exists()) target.delete()
                client.getInputStream(channel).await().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target
            }
        }.onSuccess { file ->
            if (file.length() > 0L) WatchUpdateInstaller.onApkReceived(this, file)
        }.also {
            runCatching { runBlocking { client.close(channel).await() } }
        }
    }
}
