package com.datadragon.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.datadragon.app.data.*
import com.datadragon.app.ui.components.*
import com.datadragon.app.ui.theme.DataDragonTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChoiceOptionsEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun alphabeticalToggleSortsVisiblyAndRemovesGripSpaceThenRestoresManualOrder() {
        val choices = mutableStateOf(listOf(ChoiceOption("z", "Zed"), ChoiceOption("a", "Alpha")))
        val alphabetical = mutableStateOf(false)
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            ChoiceOptionsEditor(choices = choices.value,
                onChoicesChange = { choices.value = it },
                wording = ChoiceEditorWordings.formOptions, preselection = ChoicePreselection.MULTIPLE,
                capitalizeNew = false, onDeletePermanently = {}, alphabetical = alphabetical.value,
                onAlphabeticalChange = { alphabetical.value = it })
        } }
        compose.onAllNodesWithContentDescription("Reorder").assertCountEquals(2)
        val manualLeft = compose.onNodeWithText("Zed").getUnclippedBoundsInRoot().left
        compose.onNodeWithText("Order Options Alphabetically").performClick().assertIsOn()
        compose.onAllNodesWithContentDescription("Reorder").assertCountEquals(0)
        compose.runOnIdle { assertEquals(listOf("a", "z"), choices.value.map { it.id }) }
        assertTrue(compose.onNodeWithText("Zed").getUnclippedBoundsInRoot().left < manualLeft)
        compose.onNodeWithText("Order Options Alphabetically").performClick().assertIsOff()
        compose.onAllNodesWithContentDescription("Reorder").assertCountEquals(2)
        compose.runOnIdle { assertEquals(listOf("a", "z"), choices.value.map { it.id }) }
    }

    @Test fun aNewOptionCanBeDraggedWhileItsTextEditorStillHasFocus() {
        val choices = mutableStateOf(listOf(ChoiceOption("a", "Alpha"), ChoiceOption("z", "Zed")))
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            ChoiceOptionsEditor(choices = choices.value, onChoicesChange = { choices.value = it },
                wording = ChoiceEditorWordings.formOptions, preselection = ChoicePreselection.MULTIPLE,
                capitalizeNew = false, onDeletePermanently = {})
        } }
        compose.onNodeWithText("Add Option", substring = true).performClick()
        compose.onNodeWithContentDescription("Option Name").performTextInput("Middle")
        compose.onNode(hasSetTextAction()).assertIsFocused()
        val handles = compose.onAllNodesWithContentDescription("Reorder")
        val first = handles[0].getUnclippedBoundsInRoot()
        val last = handles[2].getUnclippedBoundsInRoot()
        val distance = with(compose.density) { (last.top - first.top).toPx() }
        handles[2].performTouchInput { swipe(center, Offset(center.x, center.y - distance), durationMillis = 800) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Middle", choices.value.first().label)
            assertEquals(setOf("Alpha", "Middle", "Zed"), choices.value.map { it.label }.toSet())
        }
    }

    @Test fun alphabeticalRenameKeepsFocusAndSortsAfterEditingFinishes() {
        val choices = mutableStateOf(listOf(ChoiceOption("z", "Zed"), ChoiceOption("a", "Alpha")))
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            ChoiceOptionsEditor(choices = choices.value, onChoicesChange = { choices.value = it },
                wording = ChoiceEditorWordings.formOptions, preselection = ChoicePreselection.SINGLE,
                capitalizeNew = false, onDeletePermanently = {}, alphabetical = true)
        } }
        compose.onNodeWithText("Zed").performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("Beta")
        compose.onNode(hasSetTextAction()).assertIsFocused().performImeAction()
        compose.runOnIdle {
            assertEquals(listOf("Alpha", "Beta"), choices.value.map { it.label })
            assertEquals("z", choices.value.last().id)
        }
    }

    @Test fun emptySaveNoticeUsesTheApprovedWordingAndDismisses() {
        val showing = mutableStateOf(true)
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            if (showing.value) NothingSavedDialog { showing.value = false }
        } }
        compose.onNodeWithText("Nothing Saved").assertExists()
        compose.onNodeWithText("No Data Was Entered").assertExists()
        compose.onNodeWithText("Okay").performClick()
        compose.onNodeWithText("Nothing Saved").assertDoesNotExist()
    }
}
