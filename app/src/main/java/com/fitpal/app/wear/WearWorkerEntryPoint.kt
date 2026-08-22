package com.fitpal.app.wear

import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.ml.AnalysisJobManager
import com.fitpal.app.ml.AppForegroundState
import com.fitpal.app.ml.ExerciseAnalysisJobManager
import com.fitpal.app.ml.FoodAnalysisPipeline
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets the watch-triggered [MealAnalysisWorker] / [ExerciseAnalysisWorker] pull the app's
 * `@Singleton` collaborators out of the Hilt graph without adding `hilt-work` (which would force a
 * custom WorkManager configuration on the Application). Fetch with
 * `EntryPointAccessors.fromApplication(appContext, WearWorkerEntryPoint::class.java)`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WearWorkerEntryPoint {
    fun pipeline(): FoodAnalysisPipeline
    fun insightsGenerator(): com.fitpal.app.ml.InsightsGenerator
    fun auxAiGenerator(): com.fitpal.app.ml.AuxAiGenerator
    fun galleryRepository(): com.fitpal.app.data.repository.GalleryRepository
    fun analysisJobManager(): AnalysisJobManager
    fun exerciseAnalysisJobManager(): ExerciseAnalysisJobManager
    fun mealRepository(): MealRepository
    fun exerciseRepository(): ExerciseRepository
    fun weightRepository(): WeightRepository
    fun settingsRepository(): SettingsRepository
    fun appForegroundState(): AppForegroundState
    fun statsPublisher(): WearStatsPublisher
}
