package com.datadragon.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** The default number of rendered lines a multiline field is limited to. */
const val DEFAULT_IDEA_LINES = 20

/** The bounds every "number of lines" box in Ideas accepts. */
const val MIN_IDEA_LINES = 1
const val MAX_IDEA_LINES = 999

/**
 * One field in an Idea Log's schema. Serialized as part of the log's
 * `fieldsJson` (a JSON array of these).
 *
 * [id] is the field's permanent identity and the key its values are stored
 * under, so renaming or reordering a field never disconnects it from the values
 * already saved against it. It is generated once when the field is created and
 * never changes; it is never shown to the user.
 *
 * The field [type]s are the shared ones from [FieldType] — Ideas only exposes a
 * subset of them, under its own names (see [IdeaFieldKind]).
 *
 * [includeInPreview] governs Preview Mode only: an excluded field is simply not
 * drawn on the card. Its stored value is untouched and always shows in the full
 * Idea Detail view.
 *
 * The last five settings apply to multiline fields only and all default off:
 * [wordCount] / [characterCount] add a right-aligned count under the text,
 * [allowCopying] adds a copy button, and [truncateInEntireCard] limits the text
 * to [entireCardLines] rendered lines while the log is in Entire Idea Card mode.
 */
@Serializable
data class IdeaFieldDef(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    val includeInPreview: Boolean = true,
    /** categories: the list of choices. */
    val options: List<String> = emptyList(),
    /** multiline: visible input height while filling the idea in. */
    val lines: Int? = null,
    /** date / date&time: offer this label as an order-sorting category. */
    val allowOrderFiltering: Boolean = false,
    val wordCount: Boolean = false,
    val characterCount: Boolean = false,
    val allowCopying: Boolean = false,
    val truncateInEntireCard: Boolean = false,
    val entireCardLines: Int = DEFAULT_IDEA_LINES,
)

/**
 * The field types an Idea Log can be built from, under the names the Ideas field
 * picker shows. Each maps onto one shared [FieldType], so Ideas reuses the
 * existing field mechanics rather than duplicating them — the mapping is
 * one-to-one in both directions.
 */
@Serializable
enum class IdeaFieldKind(val label: String, val fieldType: FieldType) {
    TITLE("Title", FieldType.TEXT),
    TEXT("Text (Multiline)", FieldType.MULTILINE),
    TAGS("Tags", FieldType.TAGS),
    CATEGORIES("Categories", FieldType.DROPDOWN),
    WEBPAGES("Webpages", FieldType.WEBPAGE),
    DATE("Date Only", FieldType.DATE),
    TIME("Time Only", FieldType.TIME),
    DATETIME("Date/Time", FieldType.DATETIME);

    companion object {
        fun of(type: FieldType): IdeaFieldKind = entries.firstOrNull { it.fieldType == type } ?: TEXT
    }
}

/** The Ideas name for this field's type, e.g. "Text (Multiline)". */
val IdeaFieldDef.kind: IdeaFieldKind
    get() = IdeaFieldKind.of(type)

/**
 * Clamp a typed "number of lines" value into the accepted range. A blank or
 * unparseable box falls back to the default rather than collapsing the display
 * to zero height.
 */
fun ideaLineCount(raw: String?): Int =
    raw?.trim()?.toIntOrNull()?.coerceIn(MIN_IDEA_LINES, MAX_IDEA_LINES) ?: DEFAULT_IDEA_LINES
