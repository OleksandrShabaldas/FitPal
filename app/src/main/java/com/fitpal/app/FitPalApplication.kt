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
        fun updateManager(): com.fitpal.app.update.UpdateManager
        fun widgetUpdater(): com.fitpal.app.widget.WidgetUpdater
        fun mealRepository(): com.fitpal.app.data.repository.MealRepository
        fun settingsRepository(): com.fitpal.app.data.repository.SettingsRepository
    }

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        val foreground = entryPoint.foregroundState()
        // The look (Settings → Appearance) for every activity, the widget picker included. FitPalActivity
        // re-applies it with its own configuration (light/dark) and follows changes.
        runCatching {
            val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            com.fitpal.app.ui.theme.LookState.apply(
                entryPoint.settingsRepository().appearance.value, this,
                systemDark = night == android.content.res.Configuration.UI_MODE_NIGHT_YES
            )
        }
        // Home's layout from before the strip (2.8 and earlier) gets its placements, once.
        runCatching { entryPoint.settingsRepository().migrateHomeLayoutToStrip() }
        // Analytics' summary tiles went off by default in 2.10 (the takeaways replaced them), once.
        runCatching { entryPoint.settingsRepository().migrateAnalyticsTilesOff() }
        // The Coach tab takes Collection's place in the bar, once (2.13).
        runCatching { entryPoint.settingsRepository().migrateNavToCoach() }
        // Re-arm the daily reminder (alarms are cleared on reboot / app update).
        runCatching { entryPoint.reminderManager().reschedule() }

        // Push today's numbers to the watch whenever the phone process starts (opened by the user,
        // or cold-started by Play Services for a watch message), so the watch app, tile and
        // complications have fresh data without the user opening anything.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { entryPoint.watchLink().pushIfConnected() }
        }

        // Home-screen widgets: redraw them now (the app may have just been updated, or the day moved
        // on), then keep them in step with every change while the process lives. And, on Android 15+,
        // give the launcher's widget picker real previews.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                val widgets = entryPoint.widgetUpdater()
                if (widgets.hasAnyWidget()) {
                    widgets.startWatching()
                    widgets.updateAll()
                }
            }
            runCatching { com.fitpal.app.widget.WidgetPreviews.publishOnce(this@FitPalApplication) }
        }

        // One-time repair: give foods already logged from the built-in database the fibre they were
        // saved without (the database didn't carry fibre before DB v31).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                val settings = entryPoint.settingsRepository()
                if (!settings.isRepairDone(REPAIR_SEED_FIBER)) {
                    entryPoint.mealRepository().backfillSeedFiber()
                    settings.markRepairDone(REPAIR_SEED_FIBER)
                }
            }
        }

        // Once-a-day look for a newer GitHub release (this build is sideloaded, so there's no
        // store to do it). No-op if the user turned auto-check off or it already ran today.
        runCatching { entryPoint.updateManager().checkOnStartIfDue() }

        // Daily: refresh steps and nudge Calistapp to pull them (the "FitPal wakes Calistapp" leg
        // of the bridge). KEEP so we don't reset the period every launch.
        runCatching {
            val work = androidx.work.PeriodicWorkRequestBuilder<com.fitpal.app.sync.StepNudgeWorker>(
                1, java.util.concurrent.TimeUnit.DAYS
            ).build()
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                com.fitpal.app.sync.StepNudgeWorker.UNIQUE_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = foreground.onActivityStarted()
            override fun onActivityStopped(activity: Activity) = foreground.onActivityStopped(activity.isChangingConfigurations)
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private companion object {
        const val REPAIR_SEED_FIBER = "seed_fiber_v31"
    }
}
