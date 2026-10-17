package com.datadragon.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.datadragon.app.data.*
import com.datadragon.app.ui.screens.DailyListBody
import com.datadragon.app.ui.theme.DataDragonTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DailyAdvancedViewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disabledKeepsSortingAndEnabledSearchRevealsWholeDayWithoutChangingPreferences() {
        val enabled = mutableStateOf(false)
        val card = DailyList(id = 1, uuid = "day", date = LocalDate.of(2026, 10, 17), title = "Housework", createdAt = 1)
        val hidden = DailyListItem(id = 1, uuid = "task", dailyListId = 1, text = "Vacuum upstairs", completed = true, position = 0)
        val surrounding = hidden.copy(id = 2, uuid = "other", text = "Kitchen context", completed = false, position = 1)
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            DailyListBody(
                cards = listOf(card), cardItems = mapOf(1L to emptyList()),
                allCardItems = mapOf(1L to listOf(hidden, surrounding)),
                celebrationEnabled = false, celebrationIcon = CelebrationIcon.CHECK_CIRCLE,
                celebrationVisible = { _, _ -> false }, newestFirst = true, onNewestFirst = {},
                onOpenCard = {}, onToggleFavorite = {}, onDeleteCard = {}, advancedSearchEnabled = enabled.value,
            )
        } }
        compose.onNodeWithText("Advanced View").assertDoesNotExist()
        compose.onNodeWithText("Sort: Newest").assertExists()
        compose.onNodeWithText("Vacuum upstairs").assertDoesNotExist()
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithText("Sort: Newest").assertDoesNotExist()
        compose.onNodeWithText("Advanced View").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("vacuum")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Advanced View").performScrollTo().performClick()
        compose.onNodeWithText(card.date.format(DAILY_LIST_DATE_FORMAT)).assertExists()
        compose.onNodeWithText("Housework").assertExists()
        compose.onNodeWithText("Vacuum upstairs").assertExists()
        compose.onNodeWithText("Kitchen context").assertExists()
        compose.onNodeWithText("\"vacuum\" · 1 matching entries").assertExists()
        compose.onNodeWithText("Advanced View").performScrollTo().performClick()
        compose.onNodeWithText("Clear Search").performScrollTo().performClick()
        compose.onNodeWithText("Advanced View").performScrollTo().performClick()
        compose.onNodeWithText("Vacuum upstairs").assertDoesNotExist()
        compose.onNodeWithText("Housework").assertExists()
    }
}
