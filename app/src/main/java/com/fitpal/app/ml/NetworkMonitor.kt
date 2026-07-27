package com.fitpal.app.ml

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A tiny "is there internet right now?" check, used by [RoutingIngredientEngine] to decide
 * whether it's even worth trying the online engine before falling back to on-device.
 *
 * This only reports whether a network with internet capability is present — not whether a
 * given server is reachable. That's intentional: the actual request still has its own
 * timeout + fallback, so a captive-portal / flaky connection just fails fast and we drop
 * to local. No extra permission is needed beyond ACCESS_NETWORK_STATE.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
