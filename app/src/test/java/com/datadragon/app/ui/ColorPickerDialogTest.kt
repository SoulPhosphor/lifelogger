package com.datadragon.app.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.datadragon.app.ui.components.ColorPickerDialog
import com.datadragon.app.ui.theme.DataDragonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Calendar color picker's contract with its caller, which must survive
 * colorpicker-compose upgrades: it opens on the saved color, the hex field and
 * wheel stay in sync, Okay returns an opaque #RRGGBB, and Cancel changes nothing.
 */
@RunWith(RobolectricTestRunner::class)
// The picker draws its wheel into a bitmap, which needs real graphics.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ColorPickerDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmed: String? = null
    private var dismissed = false

    private fun open(initialHex: String) {
        compose.setContent {
            DataDragonTheme(dynamicColor = false) {
                ColorPickerDialog(
                    initialHex = initialHex,
                    onConfirm = { confirmed = it },
                    onDismiss = { dismissed = true },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test fun okayWithoutChangesReturnsTheSavedBrightColor() {
        open("#FF0000")
        compose.onNodeWithText("#FF0000").assertExists()
        compose.onNodeWithText("Okay").performClick()
        assertEquals("#FF0000", confirmed)
    }

    @Test fun okayWithoutChangesReturnsTheSavedDarkerColor() {
        open("#3366CC")
        compose.onNodeWithText("#3366CC").assertExists()
        compose.onNodeWithText("Okay").performClick()
        assertEquals("#3366CC", confirmed)
    }

    @Test fun typedHexDrivesTheSelectionReturnedByOkay() {
        open("#FF0000")
        compose.onNode(hasSetTextAction()).performTextReplacement("#00FF00")
        compose.waitForIdle()
        compose.onNodeWithText("Okay").performClick()
        assertEquals("#00FF00", confirmed)
    }

    @Test fun invalidTypedHexKeepsThePreviousSelection() {
        open("#FF0000")
        compose.onNode(hasSetTextAction()).performTextReplacement("#12")
        compose.waitForIdle()
        compose.onNodeWithText("Okay").performClick()
        assertEquals("#FF0000", confirmed)
    }

    @Test fun cancelDismissesWithoutReturningAColor() {
        open("#3366CC")
        compose.onNode(hasSetTextAction()).performTextReplacement("#00FF00")
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
        assertNull(confirmed)
    }

    @Test fun okayResultIsAlwaysOpaqueSixDigitHex() {
        open("#80FF0000")
        compose.onNodeWithText("Okay").performClick()
        val result = confirmed!!
        assertTrue(result, Regex("#[0-9A-F]{6}").matches(result))
        assertFalse(dismissed)
    }
}
