package com.datadragon.app.navigation

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MenuNavigationTest {
    private lateinit var nav: NavHostController
    private lateinit var navigator: ScreenNavigator

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        navigator = ScreenNavigator()
        nav = NavHostController(activity).apply {
            setLifecycleOwner(activity)
            navigatorProvider.addNavigator(navigator)
            graph = createGraph(startDestination = "home") {
                listOf("home", "settings", "backup", "about", "dailyPreferences").forEach { route ->
                    addDestination(navigator.createDestination().apply { this.route = route })
                }
            }
        }
        navigator.finishTransitions()
    }

    @Test
    fun repeatedBackFromDepartingScreenLeavesHomeVisible() {
        listOf("settings", "backup", "about", "dailyPreferences").forEach { route ->
            repeat(30) {
                val home = nav.currentBackStackEntry!!
                nav.navigateFromResumed(home, route)
                navigator.finishTransitions()
                val menu = nav.currentBackStackEntry!!
                repeat(10) { nav.popFromResumed(menu) }
                navigator.finishTransitions()
                assertEquals("home", nav.currentDestination?.route)
                assertNotNull(nav.currentBackStackEntry)
            }
        }
    }

    @Test
    fun repeatedMenuSelectionCreatesOnlyOneDestination() {
        val home = nav.currentBackStackEntry!!
        repeat(10) { nav.navigateFromResumed(home, "about") }
        navigator.finishTransitions()
        nav.popFromResumed(nav.currentBackStackEntry!!)
        navigator.finishTransitions()
        assertEquals("home", nav.currentDestination?.route)
    }

    @Test
    fun tapsDuringEntranceTransitionAreIgnoredUntilResumed() {
        nav.navigateFromResumed(nav.currentBackStackEntry!!, "settings")
        val menu = nav.currentBackStackEntry!!
        assertEquals(Lifecycle.State.STARTED, menu.lifecycle.currentState)
        nav.popFromResumed(menu)
        nav.navigateFromResumed(menu, "about")
        assertEquals("settings", nav.currentDestination?.route)
        navigator.finishTransitions()
        assertEquals(Lifecycle.State.RESUMED, menu.lifecycle.currentState)
        nav.popFromResumed(menu)
        navigator.finishTransitions()
        assertEquals("home", nav.currentDestination?.route)
    }

    @Test
    fun backOnOnlyDestinationDoesNotEmptyGraph() {
        repeat(10) { nav.popFromResumed(nav.currentBackStackEntry!!) }
        assertEquals("home", nav.currentDestination?.route)
    }

    @Navigator.Name("screen")
    private class ScreenNavigator : Navigator<NavDestination>() {
        override fun createDestination() = NavDestination(this)

        override fun navigate(
            entries: List<NavBackStackEntry>,
            navOptions: NavOptions?,
            navigatorExtras: Extras?,
        ) {
            entries.forEach { state.pushWithTransition(it) }
        }

        override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
            state.popWithTransition(popUpTo, savedState)
        }

        fun finishTransitions() {
            state.transitionsInProgress.value.toList().forEach { state.markTransitionComplete(it) }
        }
    }
}
