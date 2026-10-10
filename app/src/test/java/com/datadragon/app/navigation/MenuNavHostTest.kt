package com.datadragon.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import android.content.Context
import com.datadragon.app.ui.theme.DataDragonTheme
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise the real animated Compose navigator, including Android Back and restoration. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MenuNavHostTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController
    private lateinit var activity: ComponentActivity

    private fun content(): @androidx.compose.runtime.Composable () -> Unit = {
        activity = LocalContext.current as ComponentActivity
        nav = rememberNavController()
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) { Text("Home visible") }
            composable(Routes.SETTINGS) { entry ->
                MenuBackHandler(nav, entry)
                Text("Settings visible")
            }
        }
        EnsureVisibleDestination(nav)
    }

    @Test fun rapidToolbarAndSystemBackCannotBlankTheHostAndSettingsCanReopen() {
        compose.setContent(content())
        compose.mainClock.autoAdvance = false
        repeat(12) {
            compose.runOnUiThread { nav.navigateFromResumed(nav.currentBackStackEntry!!, Routes.SETTINGS) }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText("Settings visible").assertIsDisplayed()
            compose.runOnUiThread {
                val departing = nav.currentBackStackEntry!!
                nav.popFromResumed(departing)
                // System Back can arrive in the same frame, before handler enablement updates.
                activity.onBackPressedDispatcher.onBackPressed()
                nav.popFromResumed(departing)
                nav.navigateFromResumed(nav.currentBackStackEntry!!, Routes.SETTINGS)
            }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText("Home visible").assertIsDisplayed()
            compose.runOnUiThread { assertEquals(Routes.HOME, nav.currentDestination?.route) }
        }
        compose.runOnUiThread { nav.navigateFromResumed(nav.currentBackStackEntry!!, Routes.SETTINGS) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Settings visible").assertIsDisplayed()
    }

    @Test fun reopeningDuringTheReturnTransitionRestoresVisibleHome() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent(content())
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { nav.navigateFromResumed(nav.currentBackStackEntry!!, Routes.SETTINGS) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnUiThread { nav.popFromResumed(nav.currentBackStackEntry!!) }
        compose.mainClock.advanceTimeByFrame()
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Home visible").assertIsDisplayed()
        compose.runOnUiThread { nav.navigateFromResumed(nav.currentBackStackEntry!!, Routes.SETTINGS) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Settings visible").assertIsDisplayed()
    }

    @Test fun anEmptyRestoredNavigationStackRecoversVisibleHome() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent(content())
        compose.runOnUiThread { nav.popBackStack(Routes.HOME, inclusive = true) }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText("Home visible").assertIsDisplayed()
        compose.runOnUiThread { assertEquals(Routes.HOME, nav.currentDestination?.route) }
    }

    @Test fun actualAppGearCanReopenAfterRapidSettingsClose() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.getSharedPreferences("data_dragon_settings", Context.MODE_PRIVATE).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        compose.setContent {
            nav = rememberNavController()
            DataDragonTheme(dynamicColor = false) { DataDragonNavHost(nav) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        repeat(5) {
            compose.onNodeWithContentDescription("Open App Menu").performClick()
            compose.onNode(hasText("Settings") and hasClickAction()).performClick()
            // Settle the entrance before freezing the clock for the rapid return.
            compose.mainClock.advanceTimeBy(1_000)
            try {
                compose.onNodeWithContentDescription("Back").assertIsDisplayed()
            } catch (failure: AssertionError) {
                println("Current route: ${nav.currentDestination?.route}; lifecycle: ${nav.currentBackStackEntry?.lifecycle?.currentState}")
                println(compose.onRoot().printToString())
                throw failure
            }
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("Back").performClick()
            // Allow Home to compose, while staying inside the 700 ms return animation.
            compose.mainClock.advanceTimeBy(64)
            // A rapid cog tap must wait for Home to finish returning before opening a popup.
            compose.onNodeWithContentDescription("Open App Menu").performClick()
            compose.onNode(hasText("Settings") and hasClickAction()).assertDoesNotExist()
            compose.mainClock.advanceTimeBy(1_000)
            compose.mainClock.autoAdvance = true
            compose.onNodeWithContentDescription("Open App Menu").assertIsDisplayed()
            compose.runOnUiThread { assertEquals(Routes.HOME, nav.currentDestination?.route) }
        }
        compose.onNodeWithContentDescription("Open App Menu").performClick()
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
