package com.datadragon.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * The type-specific configuration for one [Calendar], serialized into
 * [Calendar.configJson]. Kept as a growing blob so later phases can add fields
 * (data source, calculation rule + condition, Yes/No option rows) without a
 * schema change — [CalendarConfigCodec] decodes leniently so an older row missing
 * newer fields still loads.
 *
 * This phase carries the color configuration used by the range calendar types
 * (Heat Map, Min/Max Value): how many color values ([colorCount], one of 3/5/10),
 * the chosen [colorPreset], and the per-value [colorRows] (color + the blank
 * Min/Max range the user fills in). The Min/Max values are stored as typed text;
 * how a day's calculated result maps against them is the viewing screen's job.
 */
@Serializable
data class CalendarConfig(
    val colorCount: Int? = null,
    val colorPreset: String = ColorPresets.GRADIATED,
    val colorRows: List<CalendarColorRow> = emptyList(),
)

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
