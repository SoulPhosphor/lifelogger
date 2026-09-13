package com.datadragon.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

// FieldType is referenced by the Heat Map calculation-rule matrix below.

/**
 * The type-specific configuration for one [Calendar], serialized into
 * [Calendar.configJson]. Kept as a growing blob so later phases can add fields
 * (data source, calculation rule + condition, Yes/No option rows) without a
 * schema change — [CalendarConfigCodec] decodes leniently so an older row missing
 * newer fields still loads.
 *
 * It carries the color configuration used by every calendar type: how many color
 * values ([colorCount], one of 3/5/10), the chosen [colorPreset], and the
 * per-value [colorRows] (color + the blank Min/Max range the user fills in). The
 * Min/Max range values are stored as typed text; how a day's calculated result
 * maps against them is the viewing screen's job.
 */
@Serializable
data class CalendarConfig(
    // Heat Map data source + calculation ("Map Heat Map to" and "Calculation Rule").
    // The source is either Log Frequency (the count of that day's logs) or one of
    // the form's fields, named by its label.
    val sourceLogFrequency: Boolean = false,
    val sourceField: String? = null,
    /** A [CalendarCalcRules] token, or null until a rule is chosen. */
    val calculationRule: String? = null,
    /** For Count Matching on a number/scale field: a condition token + value. */
    val matchCondition: String? = null,
    val matchValue: String = "",
    /** For Count Matching on a choice field: the option whose occurrences to count. */
    val matchOption: String? = null,
    /**
     * Which timestamp assigns a log to a calendar day: null means the log's
     * automatic created-at time; otherwise the label of a Date / Date & Time field
     * on the form. Used only by the viewing screen.
     */
    val dayTimestampField: String? = null,
    // Color configuration (range types).
    val colorCount: Int? = null,
    val colorPreset: String = ColorPresets.GRADIATED,
    val colorRows: List<CalendarColorRow> = emptyList(),
)

/**
 * The daily Calculation Rule tokens (stable values stored in
 * [CalendarConfig.calculationRule]) and which rules a given data source offers.
 * The owner-facing rule names live with the UI.
 */
object CalendarCalcRules {
    const val COUNT_LOGS = "count_logs"
    const val HIGHEST_VALUE = "highest_value"
    const val LOWEST_VALUE = "lowest_value"
    const val AVERAGE = "average"
    const val TOTAL = "total"
    const val COUNT_ENTRIES = "count_entries"
    const val COUNT_MATCHING = "count_matching"
    const val COUNT_YES = "count_yes"
    const val COUNT_NO = "count_no"
    const val COUNT_UNKNOWN = "count_unknown"

    /** Log Frequency's only rule: count the day's logs. */
    fun forLogFrequency(): List<String> = listOf(COUNT_LOGS)

    /**
     * The rules a field of [type] offers. Number and Scale fields take the numeric
     * rules (including Count Matching, which needs a condition); a Yes/No field
     * counts a specific response (Unknown only when the field allows it); a choice
     * field (Dropdown / Multiple) counts occurrences of one chosen option (Count
     * Matching, whose "value" is the target option). Field types without a defined
     * daily rule return an empty list.
     */
    fun forField(type: FieldType, allowUnknown: Boolean): List<String> = when (type) {
        FieldType.NUMBER, FieldType.SCALE ->
            listOf(HIGHEST_VALUE, LOWEST_VALUE, AVERAGE, TOTAL, COUNT_ENTRIES, COUNT_MATCHING)
        FieldType.YESNO ->
            listOf(COUNT_YES, COUNT_NO) + if (allowUnknown) listOf(COUNT_UNKNOWN) else emptyList()
        FieldType.DROPDOWN, FieldType.MULTIPLE ->
            listOf(COUNT_MATCHING)
        else -> emptyList()
    }

    /** Only Count Matching reveals the extra condition/value or target-option control. */
    fun requiresCondition(rule: String?): Boolean = rule == COUNT_MATCHING
}

/** Whether a form field can be mapped by a Heat Map (it has defined daily rules). */
fun FieldType.heatMapApplicable(): Boolean =
    CalendarCalcRules.forField(this, allowUnknown = true).isNotEmpty()

/**
 * The Count Matching condition tokens (stored in [CalendarConfig.matchCondition]).
 * The owner-facing names live with the UI.
 */
object CalendarConditions {
    const val GREATER_THAN = "greater_than"
    const val GREATER_OR_EQUAL = "greater_or_equal"
    const val EQUAL_TO = "equal_to"
    const val LESS_OR_EQUAL = "less_or_equal"
    const val LESS_THAN = "less_than"

    /** All conditions, in the order the dropdown shows them. */
    val all: List<String> = listOf(GREATER_THAN, GREATER_OR_EQUAL, EQUAL_TO, LESS_OR_EQUAL, LESS_THAN)
}

@Serializable
data class CalendarColorRow(
    val colorHex: String,
    val minValue: String = "",
    val maxValue: String = "",
)

/** Encodes / decodes a [CalendarConfig] to and from [Calendar.configJson]. */
object CalendarConfigCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Blank or unparseable config decodes to defaults rather than throwing. */
    fun decode(configJson: String): CalendarConfig =
        if (configJson.isBlank()) {
            CalendarConfig()
        } else {
            runCatching { json.decodeFromString<CalendarConfig>(configJson) }.getOrDefault(CalendarConfig())
        }

    fun encode(config: CalendarConfig): String = json.encodeToString(config)
}

/**
 * The built-in color presets and the rule for choosing colors when fewer than the
 * full set is used. The names are the exact owner-facing labels in the
 * "Color Preset" dropdown.
 */
object ColorPresets {
    const val GRADIATED = "Gradiated"
    const val PRIMARY = "Primary"

    /** The two built-in presets, in dropdown order. */
    val builtInNames = listOf(GRADIATED, PRIMARY)

    /** The color-value counts offered: 3, 5, or 10. */
    val counts = listOf(3, 5, 10)

    /**
     * The Gradient preset: deep vivid blue → pure red. Fewer than 10 colors are
     * chosen evenly across this ordered list so the progression stays even.
     */
    private val gradient = listOf(
        "#0033FF", "#0066FF", "#00AAFF", "#33E0FF", "#DFFFFF",
        "#FFF280", "#FFC200", "#FF7A00", "#FF3B00", "#FF0000",
    )

    private val primary = listOf(
        "#4A35A8", "#3D54C2", "#5273F2", "#00A3F6", "#009688",
        "#2E8B3B", "#8BC34A", "#FFC107", "#FF9800", "#FF5722",
    )

    /** The initial colors for [count] values from the named built-in preset. */
    fun colorsFor(presetName: String, count: Int): List<String> = when (presetName) {
        PRIMARY -> pickEvenly(primary, count)
        else -> pickEvenly(gradient, count)
    }

    /**
     * Pick [count] colors spread as evenly as possible across [source]. With
     * count == source size the whole list is used; with fewer, indices are spaced
     * evenly end to end so the first and last colors are always included.
     */
    fun pickEvenly(source: List<String>, count: Int): List<String> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(source.first())
        if (count >= source.size) return source.take(count)
        return (0 until count).map { i ->
            val index = (i.toDouble() * (source.size - 1) / (count - 1)).roundToInt()
            source[index]
        }
    }
}
