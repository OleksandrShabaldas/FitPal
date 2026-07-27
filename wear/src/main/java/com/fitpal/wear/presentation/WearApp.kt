package com.fitpal.wear.presentation

import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

/** Watch navigation. Home is the stats screen; its action chips push the log screens. */
object WearRoutes {
    const val HOME = "home"
    const val WATER = "water"
    const val MEAL = "meal"
    const val EXERCISE = "exercise"
}

@Composable
fun WearApp() {
    val navController = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearRoutes.HOME
    ) {
        composable(WearRoutes.HOME) {
            StatsScreen(
                onLogWater = { navController.navigate(WearRoutes.WATER) },
                onDescribeMeal = { navController.navigate(WearRoutes.MEAL) },
                onLogExercise = { navController.navigate(WearRoutes.EXERCISE) }
            )
        }
        composable(WearRoutes.WATER) { WaterScreen() }
        composable(WearRoutes.MEAL) { DescribeMealScreen() }
        composable(WearRoutes.EXERCISE) { DescribeExerciseScreen() }
    }
}
