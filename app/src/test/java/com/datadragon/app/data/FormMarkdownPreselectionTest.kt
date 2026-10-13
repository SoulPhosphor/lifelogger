package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pre-selection in Form Markdown: `*` marks Multiple Choice options,
 * `preselected_option:` names the Single Choice default, and the legacy
 * `top_answer_preselected: true` is still accepted.
 */
class FormMarkdownPreselectionTest {

    private fun ids(): () -> String {
        var next = 0
        return { "id-${++next}" }
    }

    @Test
    fun anAsteriskPreselectsAMultipleChoiceOptionAndIsNotPartOfItsName() {
        val field = FormMarkdownParser.parse(
            "## Pets\ntype: multiple\noptions:\n- *Cat\n- Dog\n- * Cow\n",
            ids(),
        ).fields.single()
        assertEquals(listOf("Cat", "Dog", "Cow"), field.choices.map { it.label })
        assertEquals(setOf(field.choices[0].id, field.choices[2].id), field.preselectedMultipleChoices())
    }

    @Test
    fun everyMultipleChoiceOptionCanBePreselected() {
        val field = FormMarkdownParser.parse(
            "## Pets\ntype: multiple\noptions:\n- *Cat\n- *Dog\n",
            ids(),
        ).fields.single()
        assertEquals(field.choices.map { it.id }.toSet(), field.preselectedMultipleChoices())
    }

    @Test
    fun singleChoiceAsteriskIsLiteralAndPreselectedOptionNamesTheDefault() {
        val field = FormMarkdownParser.parse(
            "## Pet\ntype: dropdown\noptions:\n- *Cat\n- Dog\npreselected_option: dog\n",
            ids(),
        ).fields.single()
        assertEquals(listOf("*Cat", "Dog"), field.choices.map { it.label })
        assertEquals(field.choices[1].id, field.preselectedSingleChoice())
    }

    @Test
    fun aPreselectedOptionThatIsNotListedIsReported() {
        val result = FormMarkdownParser.parse(
            "## Pet\ntype: dropdown\noptions:\n- Cat\npreselected_option: Dog\n",
            ids(),
        )
        assertEquals(null, result.fields.single().preselectedSingleChoice())
        assertTrue(result.issues.any { it.contains("preselected_option") })
    }

    @Test
    fun legacyTopAnswerPreselectedStillPreselectsTheFirstOption() {
        val field = FormMarkdownParser.parse(
            "## Pet\ntype: dropdown\noptions:\n- Cat\n- Dog\ntop_answer_preselected: true\n",
            ids(),
        ).fields.single()
        assertEquals(field.choices[0].id, field.preselectedSingleChoice())
    }

    @Test
    fun parsedFieldsAndOptionsAllGetIds() {
        val field = FormMarkdownParser.parse("## Pet\ntype: dropdown\noptions:\n- Cat\n- Dog\n", ids())
            .fields.single()
        val all = listOf(field.id) + field.choices.map { it.id }
        assertEquals(setOf("id-1", "id-2", "id-3"), all.toSet())
    }

    @Test
    fun aRepeatedOptionIsSkippedAndReported() {
        val result = FormMarkdownParser.parse("## Pet\ntype: multiple\noptions:\n- Cat\n- *cat\n", ids())
        val field = result.fields.single()
        assertEquals(listOf("Cat"), field.choices.map { it.label })
        assertTrue(field.choices.single().preselected)
        assertTrue(result.issues.any { it.contains("duplicate option") })
    }

    @Test
    fun generatedMarkdownListsActiveOptionsAndRoundTripsPreselection() {
        val multiple = FieldDef(
            id = "m",
            label = "Pets",
            type = FieldType.MULTIPLE,
            choices = listOf(
                ChoiceOption("a", "Cat", preselected = true),
                ChoiceOption("b", "Dog"),
                ChoiceOption("c", "Ferret", retired = true),
            ),
        )
        val single = FieldDef(
            id = "s",
            label = "Best",
            type = FieldType.DROPDOWN,
            choices = listOf(ChoiceOption("d", "Cat"), ChoiceOption("e", "Dog", preselected = true)),
        )
        val markdown = FormMarkdownGenerator.generate("Pets", listOf(multiple, single))
        assertTrue(markdown.contains("- *Cat\n- Dog\n"))
        assertTrue(!markdown.contains("Ferret"))
        assertTrue(markdown.contains("preselected_option: Dog\n"))
        assertTrue(!markdown.contains("top_answer_preselected"))

        val reparsed = FormMarkdownParser.parse(markdown, ids()).fields
        assertEquals(listOf("Cat", "Dog"), reparsed[0].choices.map { it.label })
        assertEquals(listOf(true, false), reparsed[0].choices.map { it.preselected })
        assertEquals(listOf(false, true), reparsed[1].choices.map { it.preselected })
    }
}
