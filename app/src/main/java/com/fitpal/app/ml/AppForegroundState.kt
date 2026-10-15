package com.fitpal.app.ml

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the app is currently in the foreground (any activity started), updated by
 * [com.fitpal.app.FitPalApplication] via ActivityLifecycleCallbacks.
 *
 * Used by the meal-analysis flow to decide what to do when the ONLINE model fails: if the user
 * is looking at the app, ask them (retry / use on-device); if not, there's no one to tap a
 * button, so fall back to on-device automatically.
 *
 * It also counts **visits**: a visit starts each time the app comes back from the background (or
 * opens fresh). Moving between FitPal's own screens never starts one, and neither do the two
 * "fake" trips out — a configuration-change restart (dark mode, font size, a look change) and a
 * quick trip to a screen FitPal itself opened (the photo picker, a file picker, the email app).
 * Things that should appear "once each time you open the app" — the intense-fasting reminder —
 * claim themselves once per visit with [claimOncePerVisit].
 */
@Singleton
class AppForegroundState @Inject constructor() {

    private val startedActivities = AtomicInteger(0)

    private val _isForeground = MutableStateFlow(false)
    val isForegroundFlow: StateFlow<Boolean> = _isForeground.asStateFlow()

    val isForeground: Boolean get() = startedActivities.get() > 0

    private var visit = 0
    private var restartingForConfig = false
    private var externalTripAt = 0L
    private val claimed = HashSet<String>()

    fun onActivityStarted() {
        if (startedActivities.incrementAndGet() == 1) startVisitIfNew()
        _isForeground.value = true
    }

    /** [changingConfigurations] = the activity is only being recreated, not left. */
    fun onActivityStopped(changingConfigurations: Boolean = false) {
        if (startedActivities.decrementAndGet() <= 0) {
            // Never let the counter go negative (defensive against unbalanced callbacks).
            if (startedActivities.get() < 0) startedActivities.set(0)
            if (changingConfigurations) restartingForConfig = true
            _isForeground.value = false
        }
    }

    /** FitPal is about to open another app's screen (a picker, the mail app) — coming back isn't a new visit. */
    fun markExternalTrip() {
        externalTripAt = SystemClock.elapsedRealtime()
    }

    @Synchronized
    private fun startVisitIfNew() {
        val now = SystemClock.elapsedRealtime()
        val fromOwnTrip = externalTripAt != 0L && now - externalTripAt < EXTERNAL_TRIP_MAX_MS
        val sameVisit = visit > 0 && (restartingForConfig || fromOwnTrip)
        restartingForConfig = false
        externalTripAt = 0L
        if (!sameVisit) {
            visit++
            claimed.clear()
        }
    }

    /** True the first time [key] asks during this visit; false for the rest of it. */
    @Synchronized
    fun claimOncePerVisit(key: String): Boolean = claimed.add(key)

    private companion object {
        /** A trip out that FitPal started still counts as the same visit if it's back within this. */
        const val EXTERNAL_TRIP_MAX_MS = 10 * 60_000L
    }
}
