package com.fitpal.app.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitpal.app.R
import com.fitpal.app.ui.screen.addfood.AddFoodScreen
import com.fitpal.app.ui.screen.aireview.AiReviewScreen
import com.fitpal.app.ui.screen.analysis.AnalysisScreen
import com.fitpal.app.ui.screen.analytics.AnalyticsScreen
import com.fitpal.app.ui.screen.caloriedetail.CalorieDetailScreen
import com.fitpal.app.ui.screen.barcode.BarcodeScreen
import com.fitpal.app.ui.screen.camera.CameraScreen
import com.fitpal.app.ui.screen.collection.CollectionScreen
import com.fitpal.app.ui.screen.custom.CustomFoodScreen
import com.fitpal.app.ui.screen.customize.CustomizeWidgetsScreen
import com.fitpal.app.ui.screen.describe.DescribeFoodScreen
import com.fitpal.app.ui.screen.entrydetail.EntryDetailScreen
import com.fitpal.app.ui.screen.exercise.DescribeExerciseScreen
import com.fitpal.app.ui.screen.exercise.ExerciseDetailScreen
import com.fitpal.app.ui.screen.gallery.GalleryFoodDetailScreen
import com.fitpal.app.ui.screen.gallery.GalleryScreen
import com.fitpal.app.ui.screen.trail.TrailScreen
import com.fitpal.app.ui.screen.home.HomeScreen
import com.fitpal.app.ui.screen.manual.ManualEntryScreen
import com.fitpal.app.ui.screen.mealgroup.MealGroupScreen
import com.fitpal.app.ui.screen.modelsetup.ModelSetupScreen
import com.fitpal.app.ui.screen.onboarding.OnboardingScreen
import com.fitpal.app.ui.screen.settings.SettingsScreen
import com.fitpal.app.ui.screen.settings.SettingsCategoryScreen
import com.fitpal.app.ui.screen.water.WaterDetailScreen

@Composable
fun FitPalNavHost(
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {},
    startOnboarding: Boolean = false
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Shared blur source: the nav bar samples whatever the screens draw behind it.
    val hazeState = remember { HazeState() }

    // A notification (e.g. "meal analysed — tap to review") asked us to open a screen.
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.navigate(pendingRoute) { launchSingleTop = true }
            onPendingRouteHandled()
        }
    }

    // Ask once for notification permission (Android 13+) so background-analysis updates show.
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val safeBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    val goHome: () -> Unit = {
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = true }
        }
    }

    val currentRoute = currentDestination?.route
    val isHome = currentRoute == Screen.Home.route
    val isAnalytics = currentRoute == Screen.Analytics.route
    val isCollection = currentRoute == Screen.Collection.route
    val isSettings = currentRoute == Screen.Settings.route
    val showBottomBar = isHome || isAnalytics || isCollection || isSettings

    val navTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = if (startOnboarding) Screen.Onboarding.route else Screen.Home.route,
            modifier = Modifier.padding(innerPadding).hazeSource(hazeState)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinish = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onAddFood = { navController.navigate(Screen.AddFood.route) },
                    onEntryClick = { entryId ->
                        navController.navigate(Screen.EntryDetail.buildRoute(entryId))
                    },
                    onGroupClick = { mealLogId ->
                        navController.navigate(Screen.MealGroup.buildRoute(mealLogId))
                    },
                    onOpenReview = { period, key ->
                        navController.navigate(Screen.AiReview.buildRoute(period, key))
                    },
                    onOpenTrail = { navController.navigate(Screen.Trail.route) },
                    onLogExercise = { navController.navigate(Screen.LogExercise.route) },
                    onExerciseClick = { id -> navController.navigate(Screen.ExerciseDetail.buildRoute(id)) },
                    onOpenWater = { date -> navController.navigate(Screen.WaterDetail.buildRoute(date)) },
                    onSwipeToNextScreen = { navTo(Screen.Analytics.route) }
                )
            }

            composable(Screen.Analytics.route) {
                AnalyticsScreen(
                    onOpenReview = { period, key ->
                        navController.navigate(Screen.AiReview.buildRoute(period, key))
                    },
                    onSwipeToHome = { navTo(Screen.Home.route) },
                    onSwipeToCollection = { navTo(Screen.Collection.route) },
                    onOpenDay = { navTo(Screen.Home.route) },
                    onOpenCalorieDetail = { range, anchor ->
                        navController.navigate(Screen.CalorieDetail.buildRoute(range, anchor))
                    }
                )
            }

            composable(
                route = Screen.CalorieDetail.route,
                arguments = listOf(
                    navArgument(Screen.CalorieDetail.ARG_RANGE) { type = NavType.StringType },
                    navArgument(Screen.CalorieDetail.ARG_ANCHOR) { type = NavType.StringType }
                )
            ) {
                CalorieDetailScreen(
                    onBack = safeBack,
                    onOpenDay = { navTo(Screen.Home.route) }
                )
            }

            composable(Screen.Collection.route) {
                CollectionScreen(
                    onOpenFood = { foodId -> navController.navigate(Screen.GalleryFoodDetail.buildRoute(foodId)) },
                    onSwipeToAnalytics = { navTo(Screen.Analytics.route) },
                    onSwipeToSettings = { navTo(Screen.Settings.route) }
                )
            }

            composable(Screen.AddFood.route) {
                AddFoodScreen(
                    onTakePhoto = { navController.navigate(Screen.Camera.route) },
                    onImagePicked = { uri ->
                        navController.navigate(Screen.Analysis.buildRoute(uri.toString()))
                    },
                    onDescribeToAI = { navController.navigate(Screen.DescribeFood.route) },
                    onScanBarcode = { navController.navigate(Screen.Barcode.route) },
                    onSelectSaved = { navController.navigate(Screen.Gallery.route) },
                    onManualEntry = { navController.navigate(Screen.ManualEntry.route) },
                    onCustomFood = { navController.navigate(Screen.CustomFood.buildRoute()) },
                    onLogExercise = { navController.navigate(Screen.LogExercise.route) },
                    onLogged = goHome,
                    onBack = safeBack
                )
            }

            composable(Screen.Camera.route) {
                CameraScreen(
                    onPhotoCaptured = { uri ->
                        navController.navigate(Screen.Analysis.buildRoute(uri.toString())) {
                            popUpTo(Screen.Camera.route) { inclusive = true }
                        }
                    },
                    onBack = safeBack
                )
            }

            composable(
                route = Screen.Analysis.route,
                arguments = listOf(
                    navArgument(Screen.Analysis.ARG_IMAGE_URI) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                AnalysisScreen(
                    onMealLogged = goHome,
                    onSetupModel = { navController.navigate(Screen.ModelSetup.route) },
                    onBack = safeBack
                )
            }

            composable(Screen.Gallery.route) {
                GalleryScreen(
                    onLogged = goHome,
                    onBack = safeBack,
                    onOpenDetail = { foodId -> navController.navigate(Screen.GalleryFoodDetail.buildRoute(foodId)) }
                )
            }

            composable(
                route = Screen.GalleryFoodDetail.route,
                arguments = listOf(navArgument(Screen.GalleryFoodDetail.ARG_FOOD_ID) { type = NavType.LongType })
            ) {
                GalleryFoodDetailScreen(onBack = safeBack, onLogged = goHome)
            }

            composable(Screen.ManualEntry.route) {
                ManualEntryScreen(
                    onLogged = goHome,
                    onBack = safeBack
                )
            }

            composable(
                route = Screen.CustomFood.route,
                arguments = listOf(
                    navArgument(Screen.CustomFood.ARG_BARCODE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                CustomFoodScreen(
                    onLogged = goHome,
                    onBack = safeBack
                )
            }

            composable(Screen.DescribeFood.route) {
                DescribeFoodScreen(
                    onLogged = goHome,
                    onGoToManual = { navController.navigate(Screen.ManualEntry.route) },
                    onSetupModel = { navController.navigate(Screen.ModelSetup.route) },
                    onBack = safeBack
                )
            }

            composable(Screen.Barcode.route) {
                BarcodeScreen(
                    onLogged = goHome,
                    onBack = safeBack,
                    onAddManually = { code -> navController.navigate(Screen.CustomFood.buildRoute(code)) }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onOpenCategory = { id -> navController.navigate(Screen.SettingsCategory.buildRoute(id)) },
                    onSwipeToCollection = { navTo(Screen.Collection.route) },
                    onBack = safeBack
                )
            }

            composable(
                route = Screen.SettingsCategory.route,
                arguments = listOf(navArgument(Screen.SettingsCategory.ARG_CATEGORY) { type = NavType.StringType })
            ) { backStackEntry ->
                val category = backStackEntry.arguments?.getString(Screen.SettingsCategory.ARG_CATEGORY) ?: ""
                SettingsCategoryScreen(
                    category = category,
                    onManageModels = { navController.navigate(Screen.ModelSetup.route) },
                    onCustomizeScreen = { screenId -> navController.navigate(Screen.CustomizeWidgets.buildRoute(screenId)) },
                    onBack = safeBack
                )
            }

            composable(
                route = Screen.CustomizeWidgets.route,
                arguments = listOf(
                    navArgument(Screen.CustomizeWidgets.ARG_SCREEN_ID) { type = NavType.StringType }
                )
            ) {
                CustomizeWidgetsScreen(onBack = safeBack)
            }

            composable(Screen.ModelSetup.route) {
                ModelSetupScreen(onBack = safeBack)
            }

            composable(
                route = Screen.WaterDetail.route,
                arguments = listOf(navArgument(Screen.WaterDetail.ARG_DATE) { type = NavType.StringType })
            ) {
                WaterDetailScreen(onBack = safeBack)
            }

            composable(
                route = Screen.EntryDetail.route,
                arguments = listOf(
                    navArgument(Screen.EntryDetail.ARG_ENTRY_ID) {
                        type = NavType.LongType
                    }
                )
            ) {
                EntryDetailScreen(
                    onBack = safeBack,
                    onDeleted = goHome
                )
            }

            composable(
                route = Screen.MealGroup.route,
                arguments = listOf(
                    navArgument(Screen.MealGroup.ARG_MEAL_LOG_ID) {
                        type = NavType.LongType
                    }
                )
            ) {
                MealGroupScreen(
                    onBack = safeBack,
                    onDishClick = { entryId -> navController.navigate(Screen.EntryDetail.buildRoute(entryId)) }
                )
            }

            composable(
                route = Screen.AiReview.route,
                arguments = listOf(
                    navArgument(Screen.AiReview.ARG_PERIOD) { type = NavType.StringType },
                    navArgument(Screen.AiReview.ARG_PERIOD_KEY) { type = NavType.StringType }
                )
            ) {
                AiReviewScreen(onBack = safeBack)
            }

            composable(Screen.Trail.route) {
                TrailScreen(onBack = safeBack)
            }

            composable(Screen.LogExercise.route) {
                DescribeExerciseScreen(
                    onLogged = goHome,
                    onSetupModel = { navController.navigate(Screen.ModelSetup.route) },
                    onBack = safeBack
                )
            }

            composable(
                route = Screen.ExerciseDetail.route,
                arguments = listOf(navArgument(Screen.ExerciseDetail.ARG_ID) { type = NavType.LongType })
            ) {
                ExerciseDetailScreen(onBack = safeBack, onDeleted = safeBack)
            }
        }

            if (showBottomBar) {
                FloatingGlassNav(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    hazeState = hazeState,
                    isHome = isHome,
                    isAnalytics = isAnalytics,
                    isCollection = isCollection,
                    isSettings = isSettings,
                    onHome = { navTo(Screen.Home.route) },
                    onAnalytics = { navTo(Screen.Analytics.route) },
                    onCollection = { navTo(Screen.Collection.route) },
                    onSettings = { navTo(Screen.Settings.route) },
                    onAdd = { navController.navigate(Screen.AddFood.route) }
                )
            }
        }
    }
}
