package com.fitpal.wear.data

import android.content.Context
import android.net.Uri
import com.fitpal.shared.StatsSnapshot
import com.fitpal.shared.WearContract
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reads the today-snapshot the phone syncs to the watch ([WearContract.PATH_STATS]). The Wearable
 * API caches DataItems locally on each node, so this works even with the phone momentarily out of
 * range — it just returns the last thing the phone pushed.
 */
object WearStats {

    /** The most recently synced snapshot, or null if the phone has never pushed one. */
    suspend fun latest(context: Context): StatsSnapshot? {
        val app = context.applicationContext
        val uri = Uri.parse("wear://*" + WearContract.PATH_STATS)
        val buffer = runCatching {
            Wearable.getDataClient(app).getDataItems(uri).await()
        }.getOrNull() ?: return null
        return try {
            buffer.firstOrNull()?.let { item ->
                StatsSnapshot.fromDataMap(DataMapItem.fromDataItem(item).dataMap)
            }
        } catch (e: Exception) {
            null
        } finally {
            buffer.release()
        }
    }

    /** Emits the current snapshot immediately, then again whenever the phone pushes a fresh one. */
    fun flow(context: Context): Flow<StatsSnapshot?> = callbackFlow {
        val app = context.applicationContext
        val client = Wearable.getDataClient(app)
        val listener = DataClient.OnDataChangedListener { events ->
            for (event in events) {
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == WearContract.PATH_STATS
                ) {
                    trySend(StatsSnapshot.fromDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap))
                }
            }
        }
        client.addListener(listener)
        trySend(latest(app))
        awaitClose { client.removeListener(listener) }
    }
}
