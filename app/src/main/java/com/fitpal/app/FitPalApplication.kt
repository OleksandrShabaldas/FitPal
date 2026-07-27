package com.fitpal.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.fitpal.app.ml.AppForegroundState
import com.fitpal.app.reminder.ReminderManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@HiltAndroidApp
class FitPalApplication : Application() {

    /** Lets us pull @Singleton helpers out of the Hilt graph from Application code. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun foregroundState(): AppForegroundState
        fun reminderManager(): ReminderManager
        fun watchLink(): com.fitpal.app.wear.WatchLink
    }

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        val foreground = entryPoint.foregroundState()
        // Re-arm the daily reminder (alarms are cleared on reboot / app update).
        runCatching { entryPoint.reminderManager().reschedule() }

        // Push today's numbers to the watch whenever the phone process starts (opened by the user,
        // or cold-started by Play Services for a watch message), so the watch app, tile and
        // complications have fresh data without the user opening anything.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { entryPoint.watchLink().pushIfConnected() }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = foreground.onActivityStarted()
            override fun onActivityStopped(activity: Activity) = foreground.onActivityStopped()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
