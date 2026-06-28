package com.datadragon.app.data

import kotlinx.serialization.Serializable

/**
 * One parsed field in a log's schema. Serialized as part of the template's
 * `schemaJson` (a JSON array of these).
 *
 * The supported types and their variables mirror docs/FORM_MARKDOWN_SPEC.md
 * exactly. Variables that don't apply to a given type stay null/empty.
 */
@Serializable
data class FieldDef(
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    /** multiline: visible height. */
    val lines: Int? = null,
    /** number: max digits allowed. */
    val digits: Int? = null,
    /** scale: inclusive range bounds. */
    val from: Int? = null,
    val to: Int? = null,
    /** dropdown / multiple: the list of choices. */
    val options: List<String> = emptyList(),
    /** datetime: pre-fill with the current time (`default: now`). */
    val defaultNow: Boolean = false,
)

/**
 * The closed set of field types from docs/FORM_MARKDOWN_SPEC.md §2. The
 * serialized name is the lowercase token the user writes after `type:`.
 */
@Serializable
enum class FieldType(val token: String) {
    TEXT("text"),
    MULTILINE("multiline"),
    NUMBER("number"),
    DROPDOWN("dropdown"),
    MULTIPLE("multiple"),
    SCALE("scale"),
    YESNO("yesno"),
    DATE("date"),
    TIME("time"),
    DATETIME("datetime");

    companion object {
        fun fromToken(token: String): FieldType? =
            entries.firstOrNull { it.token == token.trim().lowercase() }
    }
}
