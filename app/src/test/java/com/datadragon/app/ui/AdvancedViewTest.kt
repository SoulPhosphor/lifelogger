package com.datadragon.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.datadragon.app.data.AdvancedSearchCriteria
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
class AdvancedViewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headerCollapsesWithoutDiscardingResultsOrDraftAndSortHasNoSummary() {
        val state = AdvancedViewState()
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AdvancedView(state, 8) { Text("Existing Sorting") }
            }
        } }
        compose.onNodeWithText("Advanced View")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            .performClick()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithText("Existing Sorting").assertExists()
        compose.onNodeWithText("Partial Match").assertIsSelected()
        compose.onNodeWithText("Whole Words").assertIsNotSelected().performClick().assertIsSelected()
        compose.onNode(hasSetTextAction()).performTextInput("vacuum")
        compose.runOnIdle { assertFalse(state.applied.active) }
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Advanced View").performScrollTo().performClick()
        compose.onNodeWithText("Existing Sorting").assertDoesNotExist()
        compose.onNodeWithText("\"vacuum\" · 8 matching entries").assertExists()
        compose.runOnIdle { assertTrue(state.applied.wholeWords); assertEquals("vacuum", state.draft.query) }
        compose.onNodeWithText("Advanced View").performScrollTo().performClick()
        compose.onNodeWithText("Clear Search").performScrollTo().performClick()
        compose.onNodeWithText("\"vacuum\" · 8 matching entries").assertDoesNotExist()
        compose.runOnIdle { assertFalse(state.applied.active) }
    }
}
