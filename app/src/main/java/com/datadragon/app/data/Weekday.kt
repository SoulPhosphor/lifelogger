package com.datadragon.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Settings specific to Day of the Week. No other date or timestamp is changed. */
@Serializable
data class WeekdaySettings(
    val defaultEnteredDay: Boolean = true,
    val onlyOne: Boolean = true,
    val choiceStyle: ChoiceStyle = ChoiceStyle.DROPDOWN,
    /** Original built-in identities survive removal, including removal before the first Save. */
    val weekdayIds: Map<Int, String> = emptyMap(),
)

object Weekday {
    const val LABEL = "Day of the Week"

    /** Called only for a genuinely new field, never while decoding stored data. */
    fun newChoices(newId: () -> String = StableUuid::createNew): List<ChoiceOption> =
        DayOfWeek.values().map { day ->
            ChoiceOption(id = newId(), label = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH), weekday = day.value)
        }

    /** Immediate permanent deletion also commits the prerequisite that allowed a built-in to be deleted. */
    fun afterDeleting(settings: WeekdaySettings, choices: List<ChoiceOption>, optionId: String): WeekdaySettings =
        if (choices.any { it.id == optionId && it.weekday != null }) settings.copy(defaultEnteredDay = false) else settings

    fun validChoices(choices: List<ChoiceOption>, settings: WeekdaySettings): Boolean =
        choices.activeChoices.any { it.label.isNotBlank() } &&
            (!settings.defaultEnteredDay || (1..7).all { day -> choices.any { it.weekday == day && !it.retired && it.label.isNotBlank() } })

    fun identities(choices: List<ChoiceOption>): Map<Int, String> =
        choices.mapNotNull { option -> option.weekday?.let { it to option.id } }.toMap()

    fun seeds(settings: WeekdaySettings): List<ChoiceOption> = settings.weekdayIds.map { (day, id) ->
        ChoiceOption(id = id, label = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.ENGLISH), weekday = day)
    }

    /** Reinstate built-in weekdays by association, never by their editable spelling. */
    fun restoreWeekdays(choices: List<ChoiceOption>, seeds: List<ChoiceOption>): List<ChoiceOption> {
        val restored = choices.map { if (it.weekday != null && it.retired) it.copy(retired = false) else it }
        val missing = seeds.filter { seed -> restored.none { it.weekday == seed.weekday } }
        return restored.activeChoices + missing.map { it.copy(retired = false, preselected = false) } + restored.retiredChoices
    }

    fun defaults(choices: List<ChoiceOption>, settings: WeekdaySettings, date: LocalDate): Set<String> {
        val today = choices.firstOrNull { !it.retired && it.weekday == date.dayOfWeek.value }?.id
        if (settings.defaultEnteredDay && settings.onlyOne) return setOfNotNull(today)
        val manual = choices.activeChoices.filter { it.preselected }.map { it.id }
        return if (settings.onlyOne) manual.take(1).toSet()
        else (manual + if (settings.defaultEnteredDay) listOfNotNull(today) else emptyList()).toSet()
    }

    /** Re-evaluate only untouched automatic defaults at the actual creation date (e.g. across midnight). */
    fun atCreation(
        values: Map<String, JsonElement>,
        fields: List<Triple<String, List<ChoiceOption>, WeekdaySettings>>,
        date: LocalDate,
        untouchedIds: Set<String>,
    ): Map<String, JsonElement> = values.toMutableMap().apply {
        fields.forEach { (id, choices, settings) ->
            if (id in untouchedIds && settings.defaultEnteredDay) {
                val selected = defaults(choices, settings, date)
                if (selected.isEmpty()) remove(id) else put(id, EntryValues.stringArray(selected))
            }
        }
    }

    /** Arrays in both modes preserve past selections when a field changes modes. */
    fun selected(raw: JsonElement?): List<String> = when (raw) {
        is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(raw.contentOrNull?.takeIf { it.isNotBlank() })
        else -> emptyList()
    }

    fun clickerDefaults(fields: List<ClickerField>, date: LocalDate): Map<String, String> = buildMap {
        fields.forEach { field ->
            when (field.type) {
                ClickerFieldType.CLICK_TRACKER -> put(field.id, field.startingNumber.toString())
                ClickerFieldType.DAY_OF_WEEK -> defaults(field.choices, field.weekday, date).takeIf { it.isNotEmpty() }
                    ?.let { put(field.id, clickerValue(it)) }
                else -> Unit
            }
        }
    }

    fun missingRequired(fields: List<ClickerField>, values: Map<String, String>): List<String> =
        fields.filter { it.type == ClickerFieldType.DAY_OF_WEEK && it.required && fromClicker(values[it.id]).isEmpty() }.map { it.id }

    fun fromClicker(raw: String?): List<String> =
        raw?.let { runCatching { selected(kotlinx.serialization.json.Json.parseToJsonElement(it)) }.getOrDefault(emptyList()) }.orEmpty()

    fun clickerValue(ids: Collection<String>): String = EntryValues.stringArray(ids).toString()

    fun labels(choices: List<ChoiceOption>, ids: Collection<String>): String? {
        val known = choices.filter { it.id in ids }.map { it.label }
        val unknown = ids.filter { id -> choices.none { it.id == id } }
        return (known + unknown).takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
}
