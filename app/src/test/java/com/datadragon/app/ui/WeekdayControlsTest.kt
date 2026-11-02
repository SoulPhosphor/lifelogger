package com.datadragon.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.datadragon.app.data.*
import com.datadragon.app.ui.screens.*
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
class WeekdayControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun protectedDaysHaveNoRemovalMenuAndEnablingRestoresTheRetiredUUID() {
        val days = Weekday.newChoices()
        val choices = mutableStateOf(ChoiceEdits.retired(days, days.first().id))
        val settings = mutableStateOf(WeekdaySettings(defaultEnteredDay = false, weekdayIds = Weekday.identities(days)))
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                WeekdaySettingsControls(settings.value, choices.value, Weekday.seeds(settings.value), days.map { it.id }.toSet(),
                    { settings.value = it }, { choices.value = it }, false, {}, false, {})
            }
        } }
        compose.onNodeWithText("Default Entered Day").performClick()
        compose.onNodeWithText("When enabled all 7 week days must be displayed.").assertExists()
        compose.onAllNodesWithContentDescription("Option Menu").assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(7, choices.value.activeChoices.size)
            assertEquals(days.first().id, choices.value.single { it.weekday == 1 }.id)
            assertTrue(choices.value.none { it.retired })
        }
    }

    @Test fun switchingToSingleWithSeveralDefaultsUsesTheExactConfirmationAndCancelKeepsMultiple() {
        val days = Weekday.newChoices().mapIndexed { index, day -> day.copy(preselected = index < 2) }
        val choices = mutableStateOf(days)
        val settings = mutableStateOf(WeekdaySettings(defaultEnteredDay = false, onlyOne = false, weekdayIds = Weekday.identities(days)))
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                WeekdaySettingsControls(settings.value, choices.value, Weekday.seeds(settings.value), days.map { it.id }.toSet(),
                    { settings.value = it }, { choices.value = it }, false, {}, false, {})
            }
        } }
        compose.onNodeWithText("Only Allow Choosing One Day").performClick()
        compose.onNodeWithText("Default Options Will Be Reset").assertExists()
        compose.onNodeWithText("You had multiple items selected as default. These will be reset and you will have to select what you would like your new default option to be if any.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertFalse(settings.value.onlyOne); assertEquals(2, choices.value.count { it.preselected }) }
        compose.onNodeWithText("Only Allow Choosing One Day").performClick()
        compose.onNodeWithText("Okay").performClick()
        compose.runOnIdle { assertTrue(settings.value.onlyOne); assertTrue(choices.value.none { it.preselected }); assertEquals(days.map { it.id }, choices.value.map { it.id }) }
    }

    @Test fun optionalSingleDropdownStartsAtSelectAndSavesTheChosenUUID() {
        val days = Weekday.newChoices()
        val selected = mutableStateOf(emptySet<String>())
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            WeekdayEntryControl("Day of the Week", days, WeekdaySettings(defaultEnteredDay = false), selected.value, { selected.value = it })
        } }
        compose.onNodeWithText("Select").assertExists().performClick()
        compose.onNodeWithText("Tuesday").performClick()
        compose.runOnIdle { assertEquals(setOf(days[1].id), selected.value) }
    }

    @Test fun removingAnUnsavedOptionSkipsRetiredAndDoesNotCommitADelete() {
        val option = ChoiceOption.createNew("New")
        val choices = mutableStateOf(listOf(option))
        var deleted = false
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            ChoiceOptionsEditor(choices.value, { choices.value = it }, ChoiceEditorWordings.formOptions,
                ChoicePreselection.SINGLE, false, { deleted = true }, persistedIds = emptySet())
        } }
        compose.onNodeWithContentDescription("Option Menu").performClick()
        compose.onNodeWithText("Retire").assertDoesNotExist()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertTrue(choices.value.isEmpty()); assertFalse(deleted) }
    }

    @Test fun requiredFormsAndIdeasBlockEmptyWeekdaysInBothModes() {
        val form = FieldDef(id = "days", label = "Days", type = FieldType.DAY_OF_WEEK, required = true)
        val idea = IdeaFieldDef(id = "days", label = "Days", type = FieldType.DAY_OF_WEEK, required = true)
        assertEquals(listOf("days"), missingRequiredFieldIds(listOf(form), emptyMap(), emptyMap()))
        assertTrue(missingRequiredFieldIds(listOf(form), emptyMap(), mapOf("days" to setOf("monday"))).isEmpty())
        assertEquals(listOf("days"), blockingIdeaFieldIds(listOf(idea), emptyMap(), emptyMap()))
        assertTrue(blockingIdeaFieldIds(listOf(idea), emptyMap(), mapOf("days" to listOf("monday"))).isEmpty())
    }
}
