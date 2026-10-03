package com.datadragon.app.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

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
