package com.fitpal.app.ml

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
 */
@Singleton
class AppForegroundState @Inject constructor() {

    private val startedActivities = AtomicInteger(0)

    private val _isForeground = MutableStateFlow(false)
    val isForegroundFlow: StateFlow<Boolean> = _isForeground.asStateFlow()

    val isForeground: Boolean get() = startedActivities.get() > 0

    fun onActivityStarted() {
        if (startedActivities.incrementAndGet() >= 1) _isForeground.value = true
    }

    fun onActivityStopped() {
        if (startedActivities.decrementAndGet() <= 0) {
            // Never let the counter go negative (defensive against unbalanced callbacks).
            if (startedActivities.get() < 0) startedActivities.set(0)
            _isForeground.value = false
        }
    }
}
