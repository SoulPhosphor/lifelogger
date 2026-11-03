package com.datadragon.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.datadragon.app.data.ClickerFieldType
import com.datadragon.app.data.IdeaFieldKind
import com.datadragon.app.data.FieldType
import com.datadragon.app.ui.screens.AddFieldButton
import com.datadragon.app.ui.screens.IdeaKindDropdown
import com.datadragon.app.ui.screens.EditTypeDropdown
import com.datadragon.app.ui.screens.TypeDropdown
import com.datadragon.app.ui.theme.DataDragonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WeekdayFieldPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun newFormPickerOffersWeekdayBelowListItemQuantities() = checkPicker { selected, onSelected ->
        TypeDropdown(selected, onSelected)
    }

    @Test fun editFormPickerOffersWeekdayBelowListItemQuantities() = checkPicker { selected, onSelected ->
        EditTypeDropdown(selected, onSelected)
    }

    @Test fun ideaPickerOffersAndSelectsWeekday() {
        val selected = mutableStateOf(IdeaFieldKind.TEXT)
        compose.setContent {
            DataDragonTheme(dynamicColor = false) {
                IdeaKindDropdown(selected.value) { selected.value = it }
            }
        }
        compose.onNodeWithText(IdeaFieldKind.TEXT.label).performClick()
        compose.onNodeWithText("Day of the Week").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(IdeaFieldKind.DAY_OF_WEEK, selected.value) }
        compose.onNodeWithText("Day of the Week").assertIsDisplayed()
    }

    @Test fun clickerAddFieldMenuOffersAndSelectsWeekday() {
        var added: ClickerFieldType? = null
        compose.setContent {
            DataDragonTheme(dynamicColor = false) {
                AddFieldButton { added = it }
            }
        }
        compose.onNodeWithText("Add Field", substring = true).performClick()
        compose.onNodeWithText("Day of the Week").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(ClickerFieldType.DAY_OF_WEEK, added) }
    }

    private fun checkPicker(picker: @Composable (FieldType, (FieldType) -> Unit) -> Unit) {
        val selected = mutableStateOf(FieldType.TEXT)
        compose.setContent {
            DataDragonTheme(dynamicColor = false) {
                picker(selected.value) { selected.value = it }
            }
        }
        compose.onNodeWithText("Text (one line)").performClick()
        val weekday = compose.onNodeWithText("Day of the Week")
        weekday.performScrollTo().assertIsDisplayed()
        val quantities = compose.onNodeWithText("List Item Quantities").getUnclippedBoundsInRoot()
        val day = weekday.getUnclippedBoundsInRoot()
        val date = compose.onNodeWithText("Date").getUnclippedBoundsInRoot()
        assertTrue("Weekday should follow List Item Quantities", quantities.bottom <= day.top)
        assertTrue("Date should follow weekday", day.bottom <= date.top)
        weekday.performClick()
        compose.runOnIdle { assertEquals(FieldType.DAY_OF_WEEK, selected.value) }
        compose.onNodeWithText("Day of the Week").assertIsDisplayed()
    }
}
