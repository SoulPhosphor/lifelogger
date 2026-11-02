package com.datadragon.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.datadragon.app.ui.components.HomeCard
import com.datadragon.app.ui.components.HomeGroupingList
import com.datadragon.app.ui.theme.DataDragonTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeGroupingListTest {
    @get:Rule val compose = createComposeRule()

    @Test fun handlesOnlyShowInReorderModeAndMovesSaveBeforeDoneWhileRowsStillOpen() {
        val reordering = mutableStateOf(false)
        val rows = mutableStateOf(listOf("One", "Two"))
        var saved = emptyList<String>()
        var opened = ""
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            HomeGroupingList(rows = rows.value, uuid = { it }, reordering = reordering.value,
                onOrder = { saved = it; rows.value = it }) { row, handle ->
                HomeCard(title = row, onClick = { opened = row }, dragHandle = handle)
            }
        } }
        compose.onAllNodesWithContentDescription("Drag to Reorder").assertCountEquals(0)
        compose.runOnIdle { reordering.value = true }
        compose.onAllNodesWithContentDescription("Drag to Reorder").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Drag to Reorder")[1].performCustomAccessibilityActionWithLabel("Move Up")
        compose.runOnIdle { assertEquals(listOf("Two", "One"), saved) }
        compose.onNodeWithText("One").performClick()
        compose.runOnIdle { assertEquals("One", opened); reordering.value = false }
        compose.onAllNodesWithContentDescription("Drag to Reorder").assertCountEquals(0)
    }
}
