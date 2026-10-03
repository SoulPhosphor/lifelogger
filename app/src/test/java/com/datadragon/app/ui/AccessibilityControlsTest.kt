package com.datadragon.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.datadragon.app.ui.components.*
import com.datadragon.app.ui.screens.SettingSwitchRow
import com.datadragon.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccessibilityControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun measuringCaptionsAreHiddenUntilMenuOpens() {
        compose.setContent {
            DataDragonTheme(dynamicColor = false) {
                AppDropdown(listOf("Current", "Other longer option"), "Current", {}, { it })
            }
        }
        compose.onAllNodesWithText("Current", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Other longer option", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Current").performClick()
        compose.onNodeWithText("Other longer option").assertExists()
    }

    @Test fun externalLabelKeepsEditableTextAndSpecificError() {
        compose.setContent {
            DataDragonTheme(dynamicColor = false) {
                var value by remember { mutableStateOf("") }
                AccessibleOutlinedTextField(value, { value = it }, accessibleLabel = "Notes",
                    isError = true, accessibleError = "Check Notes")
            }
        }
        val field = compose.onNodeWithContentDescription("Notes")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Check Notes"))
        field.performTextInput("Test note")
        field.assertTextContains("Test note")
    }

    @Test fun switchRowHasOneLabeledActionAndUpdatesOnce() {
        var updates = 0
        compose.setContent {
            DataDragonTheme(dynamicColor = false) {
                var checked by remember { mutableStateOf(false) }
                SettingSwitchRow(checked, { checked = it; updates++ }, "Use Default Fields")
            }
        }
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Use Default Fields").assertIsOff().performClick().assertIsOn()
        assertEquals(1, updates)
    }

    @Test fun popupRolesStartIdenticalAndCanBeCustomizedIndependently() {
        lateinit var styles: PopupButtonStyles
        compose.setContent { DataDragonTheme(dynamicColor = false) { styles = AppTheme.popupButtons } }
        compose.runOnIdle {
            assertEquals(styles.primary, styles.destructive)
            assertNotSame(styles.primary, styles.destructive)
            val changed = styles.copy(destructive = styles.destructive.copy(outlineWidth = styles.destructive.outlineWidth * 2))
            assertEquals(styles.primary, changed.primary)
            assertNotEquals(changed.primary.outlineWidth, changed.destructive.outlineWidth)
        }
    }

    @Test fun reorderActionsRespectBoundsAndUseExistingIdentities() {
        var ids = listOf("existing-a", "existing-b", "existing-c")
        compose.setContent {
            Box(Modifier.accessibleReorder(0, ids.size) { offset ->
                val moved = reorderedItems(ids, "existing-a", offset)
                if (moved != null) ids = moved
                moved != null
            }) { Text("Reorder") }
        }
        val handle = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        val actions = handle.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Move Down"), actions.map { it.label })
        compose.runOnIdle { assertTrue(actions.single().action()) }
        assertEquals(listOf("existing-b", "existing-a", "existing-c"), ids)
        assertNull(reorderedItems(ids, "missing", 1))
        assertNull(reorderedItems(ids, "existing-b", -1))
        assertNull(reorderedItems(ids, "existing-c", 1))
    }
}
