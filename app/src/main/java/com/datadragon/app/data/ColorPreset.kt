package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * A user-saved color preset (the result of "Save Colors as Preset"). Global to
 * the app: once saved, its [name] appears in the "Color Preset" dropdown for
 * every calendar. [colorsJson] is the ordered list of hex colors it was saved
 * with; when applied at a different color count they are spread with the same
 * even-selection rule as the built-in presets.
 */
@Entity(tableName = "color_presets")
data class ColorPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorsJson: String,
)

/** Encodes / decodes a preset's ordered hex list to and from [ColorPreset.colorsJson]. */
object ColorPresetCodec {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    fun encode(colors: List<String>): String = json.encodeToString(serializer, colors)

    fun decode(colorsJson: String): List<String> =
        if (colorsJson.isBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(serializer, colorsJson) }.getOrDefault(emptyList())
        }
}
