package com.fitpal.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitpal.app.data.local.dao.AiReviewDao
import com.fitpal.app.data.local.dao.ExerciseDao
import com.fitpal.app.data.local.dao.GalleryDao
import com.fitpal.app.data.local.dao.GardenDao
import com.fitpal.app.data.local.dao.MealLogDao
import com.fitpal.app.data.local.dao.NutritionDao
import com.fitpal.app.data.local.dao.StepDao
import com.fitpal.app.data.local.dao.WeightDao
import com.fitpal.app.data.local.entity.AiReviewEntity
import com.fitpal.app.data.local.entity.CollectedPlantEntity
import com.fitpal.app.data.local.entity.ExerciseEntryEntity
import com.fitpal.app.data.local.entity.GalleryCategoryEntity
import com.fitpal.app.data.local.entity.GalleryFoodEntity
import com.fitpal.app.data.local.entity.GalleryIngredientEntity
import com.fitpal.app.data.local.entity.GardenStateEntity
import com.fitpal.app.data.local.entity.MealLogEntity
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.data.local.entity.SavedWorkoutEntity
import com.fitpal.app.data.local.entity.StepEntryEntity
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.data.local.entity.WeightEntryEntity

@Database(
    entities = [
        AiReviewEntity::class,
        CollectedPlantEntity::class,
        ExerciseEntryEntity::class,
        GalleryCategoryEntity::class,
        GalleryFoodEntity::class,
        GalleryIngredientEntity::class,
        GardenStateEntity::class,
        MealLogEntity::class,
        MealLogItemEntity::class,
        SavedWorkoutEntity::class,
        StepEntryEntity::class,
        UsdaFoodEntity::class,
        WeightEntryEntity::class
    ],
    version = 17,
    exportSchema = true
)
abstract class FitPalDatabase : RoomDatabase() {
    abstract fun aiReviewDao(): AiReviewDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun galleryDao(): GalleryDao
    abstract fun gardenDao(): GardenDao
    abstract fun mealLogDao(): MealLogDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun stepDao(): StepDao
    abstract fun weightDao(): WeightDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN isDrink INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN waterMl REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_foods ADD COLUMN isDrink INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN waterMlPer100g REAL NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN fiber REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN vitaminA REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN vitaminC REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN vitaminD REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN calcium REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN iron REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN potassium REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN sodium REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN fiberPer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN vitaminAPer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN vitaminCPer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN vitaminDPer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN calciumPer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN ironPer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN potassiumPer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN sodiumPer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_foods ADD COLUMN totalFiber REAL NOT NULL DEFAULT 0")
            }
        }

        /** v3 -> v4: weight tracking table. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS weight_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        weightKg REAL NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """)
            }
        }

        /** v4 -> v5: notes + thumbnail for gallery foods, step tracking table. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE gallery_foods ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE gallery_foods ADD COLUMN thumbnailPath TEXT NOT NULL DEFAULT ''")
                // Step tracking
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS step_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        steps INTEGER NOT NULL,
                        source TEXT NOT NULL DEFAULT 'manual',
                        caloriesBurned REAL NOT NULL DEFAULT 0,
                        timestamp INTEGER NOT NULL
                    )
                """)
            }
        }

        /**
         * v5 -> v6: cache ingredients + AI insights on logged items (so the meal
         * detail can show/edit them and not regenerate), plus a table of saved
         * AI period overviews (daily/weekly/monthly).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN ingredientsJson TEXT")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN insightsJson TEXT")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN insightsGeneratedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ai_reviews (
                        period TEXT NOT NULL,
                        periodKey TEXT NOT NULL,
                        review TEXT NOT NULL,
                        generatedAt INTEGER NOT NULL,
                        PRIMARY KEY(period, periodKey)
                    )
                """)
            }
        }

        /** v6 -> v7: the garden (a single state row + a collection of bloomed plants). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS garden_state (
                        id INTEGER PRIMARY KEY NOT NULL,
                        growthPoints INTEGER NOT NULL DEFAULT 0,
                        wiltLevel INTEGER NOT NULL DEFAULT 0,
                        buffers INTEGER NOT NULL DEFAULT 2,
                        bufferProgress INTEGER NOT NULL DEFAULT 0,
                        loggedThisPlant INTEGER NOT NULL DEFAULT 0,
                        onGoalThisPlant INTEGER NOT NULL DEFAULT 0,
                        lastEvaluatedDate TEXT,
                        bloomCount INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS collected_plants (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        speciesId TEXT NOT NULL,
                        rarity TEXT NOT NULL,
                        bloomedDate TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL
                    )
                """)
            }
        }

        /** v7 -> v8: logged exercise / activity entries. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS exercise_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        name TEXT NOT NULL,
                        minutes INTEGER NOT NULL,
                        met REAL NOT NULL,
                        caloriesBurned REAL NOT NULL,
                        source TEXT NOT NULL DEFAULT 'manual',
                        timestamp INTEGER NOT NULL
                    )
                """)
            }
        }

        /** v8 -> v9: garden weekly-bonus + bloom-celebration bookkeeping. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE garden_state ADD COLUMN lastWeeklyEvalWeek TEXT")
                db.execSQL("ALTER TABLE garden_state ADD COLUMN onTrackWeeks INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE garden_state ADD COLUMN lastSeenBloomCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v9 -> v10: garden currency loop — 💧 water + ⭐ points + last-watered. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE garden_state ADD COLUMN water INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE garden_state ADD COLUMN points INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE garden_state ADD COLUMN lastWateredDate TEXT")
            }
        }

        /**
         * v10 -> v11: six more tracked micronutrients (B12, folate, B6, magnesium,
         * zinc, vitamin E) on logged items and on gallery ingredients.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN vitaminB12 REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN folate REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN vitaminB6 REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN magnesium REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN zinc REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN vitaminE REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN vitaminB12Per100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN folatePer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN vitaminB6Per100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN magnesiumPer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN zincPer100g REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gallery_ingredients ADD COLUMN vitaminEPer100g REAL NOT NULL DEFAULT 0")
            }
        }

        /** v11 -> v12: remember which AI (online Gemini vs on-device) produced a logged item. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_log_items ADD COLUMN aiSource TEXT")
            }
        }

        /** v12 -> v13: remember which AI produced a saved daily/weekly/monthly review. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_reviews ADD COLUMN source TEXT")
            }
        }

        /** v13 -> v14: keep a saved food's generated AI analysis + which engine made it. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE gallery_foods ADD COLUMN insightsJson TEXT")
                db.execSQL("ALTER TABLE gallery_foods ADD COLUMN aiSource TEXT")
            }
        }

        /** v14 -> v15: keep a logged exercise's AI analysis (engine + coaching tips). */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_entries ADD COLUMN aiSource TEXT")
                db.execSQL("ALTER TABLE exercise_entries ADD COLUMN suggestionsJson TEXT")
            }
        }

        /** v15 -> v16: user-created collection categories + subcategories, and a food's filing. */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS gallery_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        parentId INTEGER,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("ALTER TABLE gallery_foods ADD COLUMN categoryId INTEGER")
            }
        }

        /** v16 -> v17: saved workout templates (the exercise side of the collection). */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS saved_workouts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        met REAL NOT NULL,
                        minutes INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastUsed INTEGER NOT NULL
                    )
                """)
            }
        }
    }
}
