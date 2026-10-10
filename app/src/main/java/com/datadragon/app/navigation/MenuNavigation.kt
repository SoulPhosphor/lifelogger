package com.datadragon.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import kotlinx.coroutines.flow.collect

// A screen remains composed while its exit animation runs. Ignore taps from
// that departing screen so repeated Back taps cannot pop Home as well.
internal fun NavController.navigateFromResumed(source: NavBackStackEntry, route: String) {
    if (currentBackStackEntry !== source || source.lifecycle.currentState != Lifecycle.State.RESUMED) return
    navigate(route) { launchSingleTop = true }
}

internal fun NavController.popFromResumed(source: NavBackStackEntry) {
    if (currentBackStackEntry !== source || source.lifecycle.currentState != Lifecycle.State.RESUMED) return
    // Never remove the last visible destination, even if a stale callback fires.
    if (previousBackStackEntry == null) return
    popBackStack()
}

/** System Back uses the same guard as the menu screens' toolbar Back. */
@Composable
internal fun MenuBackHandler(navController: NavController, source: NavBackStackEntry) {
    BackHandler { navController.popFromResumed(source) }
}

/** Recover an empty restored/live stack instead of leaving only the background visible. */
@Composable
internal fun EnsureVisibleDestination(navController: NavController) {
    LaunchedEffect(navController) {
        navController.visibleEntries.collect { visible ->
            val destination = navController.currentBackStackEntry?.destination
            if (visible.isEmpty() && (destination == null || destination is NavGraph)) {
                navController.navigate(Routes.HOME) { launchSingleTop = true }
            }
        }
    }
}
