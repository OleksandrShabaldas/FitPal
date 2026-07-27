package com.fitpal.wear.data

import android.content.Context
import com.fitpal.shared.WearContract
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Sends commands from the watch to the phone over the Data Layer.
 *
 * Delivery targets the node that actually advertises the FitPal phone capability
 * ([WearContract.CAPABILITY_PHONE_APP]) so we talk to the phone app rather than blindly to every
 * paired device, falling back to all connected nodes if the capability lookup comes back empty
 * (it can lag right after install). Play Services cold-starts the phone's listener service, so the
 * phone app does NOT need to be open for any of this.
 */
object PhoneComms {

    /** Whether the FitPal phone app is currently reachable — drives the connection indicator. */
    suspend fun isPhoneAppReachable(context: Context): Boolean = phoneNodeIds(context).isNotEmpty()

    suspend fun logWater(context: Context, ml: Int): Boolean =
        sendMessage(context, WearContract.PATH_LOG_WATER, ml.toString().toByteArray())

    suspend fun describeMeal(context: Context, text: String): Boolean =
        sendMessage(context, WearContract.PATH_DESCRIBE_MEAL, text.toByteArray())

    suspend fun describeExercise(context: Context, text: String): Boolean =
        sendMessage(context, WearContract.PATH_DESCRIBE_EXERCISE, text.toByteArray())

    suspend fun requestStats(context: Context): Boolean =
        sendMessage(context, WearContract.PATH_REQUEST_STATS, ByteArray(0))

    /** Report the watch's own daily step count. [payload] = "yyyy-MM-dd|steps". */
    suspend fun sendSteps(context: Context, payload: String): Boolean =
        sendMessage(context, WearContract.PATH_LOG_STEPS, payload.toByteArray())

    /**
     * Nodes running the FitPal phone app (reachable right now). Falls back to every connected node
     * when the capability isn't resolvable yet, so a fresh install still works.
     */
    private suspend fun phoneNodeIds(context: Context): List<String> {
        val app = context.applicationContext
        val capable = runCatching {
            Wearable.getCapabilityClient(app)
                .getCapability(WearContract.CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE)
                .await().nodes.map { it.id }
        }.getOrDefault(emptyList())
        if (capable.isNotEmpty()) return capable
        return runCatching { Wearable.getNodeClient(app).connectedNodes.await().map { it.id } }
            .getOrDefault(emptyList())
    }

    private suspend fun sendMessage(context: Context, path: String, payload: ByteArray): Boolean {
        val app = context.applicationContext
        val nodes = phoneNodeIds(app)
        if (nodes.isEmpty()) return false
        val client = Wearable.getMessageClient(app)
        var anyOk = false
        for (nodeId in nodes) {
            val ok = runCatching { client.sendMessage(nodeId, path, payload).await() }.isSuccess
            anyOk = anyOk || ok
        }
        return anyOk
    }
}
