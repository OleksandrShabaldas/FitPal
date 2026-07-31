package com.fitpal.app.di

import android.content.Context
import androidx.room.Room
import com.fitpal.app.data.local.FitPalDatabase
import com.fitpal.app.data.local.dao.AiReviewDao
import com.fitpal.app.data.local.dao.ChallengeDao
import com.fitpal.app.data.local.dao.ExerciseDao
import com.fitpal.app.data.local.dao.GalleryDao
import com.fitpal.app.data.local.dao.MealLogDao
import com.fitpal.app.data.local.dao.NutritionDao
import com.fitpal.app.data.local.dao.StepDao
import com.fitpal.app.data.local.dao.TrailDao
import com.fitpal.app.data.local.dao.WeightDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FitPalDatabase {
        return Room.databaseBuilder(
            context,
            FitPalDatabase::class.java,
            "fitpal.db"
        )
            .addMigrations(
                FitPalDatabase.MIGRATION_1_2,
                FitPalDatabase.MIGRATION_2_3,
                FitPalDatabase.MIGRATION_3_4,
                FitPalDatabase.MIGRATION_4_5,
                FitPalDatabase.MIGRATION_5_6,
                FitPalDatabase.MIGRATION_6_7,
                FitPalDatabase.MIGRATION_7_8,
                FitPalDatabase.MIGRATION_8_9,
                FitPalDatabase.MIGRATION_9_10,
                FitPalDatabase.MIGRATION_10_11,
                FitPalDatabase.MIGRATION_11_12,
                FitPalDatabase.MIGRATION_12_13,
                FitPalDatabase.MIGRATION_13_14,
                FitPalDatabase.MIGRATION_14_15,
                FitPalDatabase.MIGRATION_15_16,
                FitPalDatabase.MIGRATION_16_17,
                FitPalDatabase.MIGRATION_17_18,
                FitPalDatabase.MIGRATION_18_19,
                FitPalDatabase.MIGRATION_19_20,
                FitPalDatabase.MIGRATION_20_21
            )
            .build()
        // TODO: When USDA database is ready, use .createFromAsset("usda_foods.db")
        // to ship pre-populated nutritional data with the app
    }

    @Provides
    fun provideAiReviewDao(db: FitPalDatabase): AiReviewDao = db.aiReviewDao()

    @Provides
    fun provideChallengeDao(db: FitPalDatabase): ChallengeDao = db.challengeDao()

    @Provides
    fun provideGalleryDao(db: FitPalDatabase): GalleryDao = db.galleryDao()

    @Provides
    fun provideExerciseDao(db: FitPalDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideMealLogDao(db: FitPalDatabase): MealLogDao = db.mealLogDao()

    @Provides
    fun provideNutritionDao(db: FitPalDatabase): NutritionDao = db.nutritionDao()

    @Provides
    fun provideStepDao(db: FitPalDatabase): StepDao = db.stepDao()

    @Provides
    fun provideTrailDao(db: FitPalDatabase): TrailDao = db.trailDao()

    @Provides
    fun provideWeightDao(db: FitPalDatabase): WeightDao = db.weightDao()
}
