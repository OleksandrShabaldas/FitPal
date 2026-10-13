package com.fitpal.app.ml

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Run a blocking socket call so coroutine cancellation can actually stop it. A blocked socket read
 * or write ignores cancellation, so a `withTimeout` around it used to wait out the full socket
 * timeout anyway (that's how a "22 s" classifier timeout could hold "Saving…" for much longer).
 * Here a watchdog closes the connection the moment we're cancelled, which makes the blocked call
 * throw and frees the thread straight away.
 */
internal suspend fun <T> disconnectOnCancel(conn: HttpURLConnection, block: () -> T): T = coroutineScope {
    val done = AtomicBoolean(false)
    // UNDISPATCHED so the watchdog is already parked in its try before the blocking call starts —
    // a cancellation can't slip in before it's listening.
    val watchdog = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!done.get()) runCatching { conn.disconnect() }
        }
    }
    try {
        block()
    } catch (e: Throwable) {
        // Closing the socket makes the blocked call throw an IOException — report it as what it
        // really is (a cancellation / timeout), so `withTimeoutOrNull` & co. see the right thing.
        ensureActive()
        throw e
    } finally {
        done.set(true)
        watchdog.cancel()
    }
}
