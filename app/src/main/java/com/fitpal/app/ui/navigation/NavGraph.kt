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
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import com.fitpal.app.ui.screen.feedback.FeedbackScreen
import com.fitpal.app.ui.screen.weighin.WeighInRoute
import com.fitpal.app.ui.screen.widgets.WidgetGalleryScreen
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
import com.fitpal.app.ui.screen.search.SearchScreen
import com.fitpal.app.ui.screen.manual.ManualEntryScreen
import com.fitpal.app.ui.screen.mealgroup.MealGroupScreen
import com.fitpal.app.ui.screen.modelsetup.ModelSetupScreen
import com.fitpal.app.ui.screen.onboarding.OnboardingScreen
import com.fitpal.app.ui.screen.settings.SettingsScreen
import com.fitpal.app.ui.screen.settings.SettingsCategoryScreen
import com.fitpal.app.ui.screen.water.WaterDetailScreen

/** Bottom space the floating/docked bar takes on the current screen (0 when there's no bar). */
val LocalBottomBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** The screen behind a bottom-bar tab. */
private fun com.fitpal.app.domain.model.NavTab.route(): String = when (this) {
    com.fitpal.app.domain.model.NavTab.HOME -> Screen.Home.route
    com.fitpal.app.domain.model.NavTab.ANALYTICS -> Screen.Analytics.route
    com.fitpal.app.domain.model.NavTab.COLLECTION -> Screen.Collection.route
    com.fitpal.app.domain.model.NavTab.TRAIL -> Screen.Trail.route
    com.fitpal.app.domain.model.NavTab.SETTINGS -> Screen.Settings.route
}

@Composable
fun FitPalNavHost(
    navConfig: com.fitpal.app.domain.model.NavConfig = com.fitpal.app.domain.model.NavConfig(),
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {},
    startOnboarding: Boolean = false
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Shared blur source: the nav bar samples whatever the screens draw behind it.
    val hazeState = remember { HazeState() }

    // Ask once for notification permission (Android 13+) so background-analysis updates show. On a
    // first launch the intro asks at its reminders step instead (with the reason next to it), so the
    // system prompt doesn't pop over the welcome screen.
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (!startOnboarding &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
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
    // The bottom bar's tabs, in the user's order (Settings → Home & screens → Bottom bar).
    val tabs = navConfig.shownTabs
    val tabRoutes = tabs.map { it.route() }
    val currentTab = tabs.firstOrNull { it.route() == currentRoute }
    val showBottomBar = currentTab != null

    val navTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Swiping sideways on a tab moves to its neighbour in the bar's order (nothing past the ends).
    fun neighbour(tab: com.fitpal.app.domain.model.NavTab, step: Int): () -> Unit = {
        val i = tabs.indexOf(tab)
        tabs.getOrNull(i + step)?.let { navTo(it.route()) }
    }
    val openAddMenu: () -> Unit = { navController.navigate(Screen.AddFood.route) }
    val onPlus: () -> Unit = {
        when (navConfig.plus) {
            com.fitpal.app.domain.model.PlusAction.MENU -> openAddMenu()
            com.fitpal.app.domain.model.PlusAction.CAMERA -> navController.navigate(Screen.Camera.route)
            com.fitpal.app.domain.model.PlusAction.DESCRIBE -> navController.navigate(Screen.DescribeFood.route)
        }
    }

    // A notification ("meal analysed — tap to review") or a home-screen widget asked for a screen.
    // A main tab lands on the tab itself, clearing whatever was open over it — not a tab-bar switch,
    // which would restore that tab's saved sub-screens (a widget tap would reopen the last screen
    // instead of Home). Anything else opens over whatever's showing.
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            val mainRoutes = setOf(Screen.Home.route, Screen.Analytics.route, Screen.Collection.route, Screen.Settings.route) + tabRoutes
            if (pendingRoute in mainRoutes) {
                navController.navigate(pendingRoute) {
                    popUpTo(navController.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            } else {
                navController.navigate(pendingRoute) { launchSingleTop = true }
            }
            onPendingRouteHandled()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // How much of the bottom the bar covers on this screen, for screens that weren't built as tabs.
        androidx.compose.runtime.CompositionLocalProvider(LocalBottomBarInset provides (if (showBottomBar) 110.dp else 0.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = if (startOnboarding) Screen.Onboarding.route else Screen.Home.route,
            modifier = Modifier.padding(innerPadding).hazeSource(hazeState)
        ) {
            composable(
                route = Screen.Onboarding.route,
                arguments = listOf(
                    navArgument(Screen.Onboarding.ARG_REPLAY) {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                val replay = entry.arguments?.getBoolean(Screen.Onboarding.ARG_REPLAY) == true
                OnboardingScreen(
                    replay = replay,
                    onFinish = {
                        if (replay) {
                            safeBack()
                        } else {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            // The weigh-in floats over whatever's behind it — the notification, the Home weight card
            // and the Analytics weight card all open this one destination.
            dialog(
                route = Screen.WeighIn.route,
                dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                WeighInRoute(onDone = safeBack)
            }

            composable(Screen.Feedback.route) {
                FeedbackScreen(onBack = safeBack)
            }

            composable(Screen.WidgetGallery.route) {
                WidgetGalleryScreen(onBack = safeBack)
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onOpenWeighIn = { navController.navigate(Screen.WeighIn.route) },
                    onAddFood = { navController.navigate(Screen.AddFood.route) },
                    onOpenSearch = { navController.navigate(Screen.Search.route) },
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
                    onSwipeToNextScreen = neighbour(com.fitpal.app.domain.model.NavTab.HOME, +1)
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(onBack = safeBack)
            }

            composable(Screen.Analytics.route) {
                AnalyticsScreen(
                    onOpenReview = { period, key ->
                        navController.navigate(Screen.AiReview.buildRoute(period, key))
                    },
                    onSwipeToHome = neighbour(com.fitpal.app.domain.model.NavTab.ANALYTICS, -1),
                    onSwipeToCollection = neighbour(com.fitpal.app.domain.model.NavTab.ANALYTICS, +1),
                    onOpenDay = { navTo(Screen.Home.route) },
                    onOpenCalorieDetail = { range, anchor ->
                        navController.navigate(Screen.CalorieDetail.buildRoute(range, anchor))
                    },
                    onOpenWeighIn = { navController.navigate(Screen.WeighIn.route) }
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
                    onSwipeToAnalytics = neighbour(com.fitpal.app.domain.model.NavTab.COLLECTION, -1),
                    onSwipeToSettings = neighbour(com.fitpal.app.domain.model.NavTab.COLLECTION, +1)
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
                    onOpenWeighIn = { navController.navigate(Screen.WeighIn.route) },
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
                    onOpenCategory = { id, focus -> navController.navigate(Screen.SettingsCategory.buildRoute(id, focus)) },
                    onOpenFeedback = { navController.navigate(Screen.Feedback.route) },
                    onOpenWidgets = { navController.navigate(Screen.WidgetGallery.route) },
                    onSwipeToCollection = neighbour(com.fitpal.app.domain.model.NavTab.SETTINGS, -1),
                    onBack = safeBack
                )
            }

            composable(
                route = Screen.SettingsCategory.route,
                arguments = listOf(
                    navArgument(Screen.SettingsCategory.ARG_CATEGORY) { type = NavType.StringType },
                    navArgument(Screen.SettingsCategory.ARG_FOCUS) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val category = backStackEntry.arguments?.getString(Screen.SettingsCategory.ARG_CATEGORY) ?: ""
                SettingsCategoryScreen(
                    category = category,
                    focus = backStackEntry.arguments?.getString(Screen.SettingsCategory.ARG_FOCUS),
                    onManageModels = { navController.navigate(Screen.ModelSetup.route) },
                    onCustomizeScreen = { screenId -> navController.navigate(Screen.CustomizeWidgets.buildRoute(screenId)) },
                    onReplayIntro = { navController.navigate(Screen.Onboarding.buildRoute(replay = true)) },
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
                    tabs = tabs,
                    current = currentTab,
                    onTab = { navTo(it.route()) },
                    onAdd = onPlus,
                    onAddLong = openAddMenu,
                    hazeState = hazeState,
                    showLabels = navConfig.labels,
                    style = navConfig.style,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
    }
}
