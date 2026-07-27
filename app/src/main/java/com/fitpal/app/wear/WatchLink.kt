package com.fitpal.app.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** What the phone knows about the paired watch right now (for the Settings status card). */
data class WatchStatus(
    val connected: Boolean = false,
    /** Friendly names of the connected watches, e.g. "Galaxy Watch5 Pro". */
    val names: List<String> = emptyList(),
    /** True once a fresh snapshot has been pushed in this check. */
    val lastPushOk: Boolean? = null
)

/**
 * Phone-side view of the watch link: who's connected, and a manual "push a fresh snapshot now"
 * used by the Settings reconnect button.
 *
 * Note the watch does NOT need the phone app open for day-to-day use — Play Services cold-starts
 * [WearDataLayerService] to answer its messages. This is for showing the user it's working and for
 * forcing a refresh when something looks stale.
 */
@Singleton
class WatchLink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsPublisher: WearStatsPublisher
) {
    /** Which watches are currently connected over the Data Layer. */
    suspend fun status(): WatchStatus {
        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.await() }
            .getOrDefault(emptyList())
        return WatchStatus(connected = nodes.isNotEmpty(), names = nodes.map { it.displayName })
    }

    /** Re-check the link and push today's numbers to the watch. */
    suspend fun reconnectAndPush(): WatchStatus {
        val base = status()
        if (!base.connected) return base.copy(lastPushOk = false)
        val ok = runCatching { statsPublisher.publishNow() }.isSuccess
        return base.copy(lastPushOk = ok)
    }

    /** Best-effort background refresh — used on app start so the watch/tiles stay current. */
    suspend fun pushIfConnected() {
        if (status().connected) runCatching { statsPublisher.publishNow() }
    }
}
