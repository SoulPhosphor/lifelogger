package com.datadragon.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object CardContent {
    fun hasData(values: Map<String, JsonElement>): Boolean = values.values.any(::hasValue)
    fun hasFormData(fields: List<FieldDef>, values: Map<String, JsonElement>): Boolean =
        hasTypedData(fields.map { it.id to it.type }, values)

    fun hasIdeaData(fields: List<IdeaFieldDef>, values: Map<String, JsonElement>): Boolean =
        hasTypedData(fields.map { it.id to it.type }, values)

    private fun hasTypedData(fields: List<Pair<String, FieldType>>, values: Map<String, JsonElement>): Boolean {
        val types = fields.toMap()
        return values.any { (id, value) ->
            if (types[id] in listOf(FieldType.RATIO, FieldType.BLOOD_PRESSURE) && value is JsonPrimitive) {
                value.content.replace("/", "").isNotBlank()
            } else hasValue(value)
        }
    }

    private fun hasValue(value: JsonElement): Boolean = when (value) {
        JsonNull -> false
        is JsonPrimitive -> value.content.isNotBlank()
        is JsonArray -> value.any(::hasValue)
        is JsonObject -> value.values.any(::hasValue)
    }
}
