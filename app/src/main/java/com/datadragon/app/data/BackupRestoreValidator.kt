package com.datadragon.app.data

import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/** Complete semantic validation performed before restore preflight or database writes. */
object BackupRestoreValidator {
    private val json = Json { ignoreUnknownKeys = true }

    fun validate(backup: BackupFile) {
        require(backup.format == BackupFile.FORMAT) { "This file is not a Data Dragon backup." }
        require(backup.version in 1..BackupFile.VERSION) { "This backup version is not supported." }
        require(backup.includedCategories.size == backup.includedCategories.distinct().size) {
            "Backup category manifest contains duplicates."
        }
        require(backup.counts == BackupCounts.from(backup.payload)) {
            "Backup category counts do not match its payload."
        }

        validateForms(backup)
        validateLists(backup)
        validateIdeas(backup)
        validateDailyTasks(backup)
        validateClicker(backup)
        validatePresets(backup)
        backup.payload.portablePreferences?.let(::validatePreferences)
    }

    private fun validateForms(backup: BackupFile) {
        val forms = backup.payload.forms ?: return
        requireUniqueIdentities(forms.map { it.uuid }, "Form", allowBlank = backup.version < 3)
        forms.forEach { form ->
            validateFormSchema(form.schemaJson)
            requireUniquePositions(form.calendars.map { it.position }, "Form calendar")
            form.entries.forEach { requireJsonObject(it.valuesJson, "Form entry values") }
            form.calendars.forEach { calendar ->
                require(calendar.position >= 0) { "Form calendar position is invalid." }
                if (calendar.configJson.isNotBlank()) {
                    decode<CalendarConfig>(calendar.configJson, "Calendar configuration")
                }
            }
        }
    }

    private fun validateLists(backup: BackupFile) {
        val lists = backup.payload.lists ?: return
        requireUniqueIdentities(lists.map { it.uuid }, "List", allowBlank = backup.version < 3)
        lists.forEach { list -> validateFlatItems(list.items.map { it.position to it.indent }, "List") }
    }

    private fun validateIdeas(backup: BackupFile) {
        val logs = backup.payload.ideaLogs ?: return
        requireUniqueIdentities(logs.map { it.uuid }, "Idea Log")
        logs.forEach { log ->
            validateIdeaFields(log.fieldsJson)
            require(log.previewLines in MIN_IDEA_LINES..MAX_IDEA_LINES) { "Idea Log preview lines are invalid." }
            log.entries.forEach { requireJsonObject(it.valuesJson, "Idea entry values") }
        }
    }

    private fun validateDailyTasks(backup: BackupFile) {
        val cards = backup.payload.dailyTasks ?: return
        requireUniqueIdentities(cards.map { it.uuid }, "Daily Task card")
        require(cards.map { it.date }.size == cards.map { it.date }.distinct().size) {
            "Daily Task dates must be unique inside a backup."
        }
        requireUniqueIdentities(cards.flatMap { card -> card.items.map { it.uuid } }, "Daily Task item")
        cards.forEach { card ->
            parseDate(card.date, "Daily Task date")
            card.maintenanceRunOn?.let { parseDate(it, "Daily Task maintenance date") }
            card.renewalRunOn?.let { parseDate(it, "Daily Task renewal date") }
            validateFlatItems(card.items.map { it.position to it.indent }, "Daily Task")
        }
    }

    private fun validateClicker(backup: BackupFile) {
        val logs = backup.payload.clickerData ?: return
        requireUniqueIdentities(logs.map { it.uuid }, "Clicker Data grouping")
        requireUniqueIdentities(logs.flatMap { log -> log.cards.map { it.uuid } }, "Clicker card")
        logs.forEach { log ->
            validateClickerFields(log.fieldsJson)
            log.cards.forEach { requireJsonObject(it.valuesJson, "Clicker card values") }
        }
    }

    private fun validatePresets(backup: BackupFile) {
        val presets = backup.payload.savedColorPresets ?: return
        requireUniqueIdentities(presets.map { it.uuid }, "Saved color preset")
        presets.forEach {
            decode(it.colorsJson, ListSerializer(String.serializer()), "Saved color preset colors")
        }
    }

    private fun validatePreferences(preferences: BackupPortablePreferences) {
        require(HomeView.entries.any { it.key == preferences.lastHomeView }) {
            "Portable preference lastHomeView is invalid."
        }
        require(NavStyle.entries.any { it.key == preferences.navStyle }) {
            "Portable preference navStyle is invalid."
        }
        require(CompleteIcon.entries.any { it.key == preferences.listCompleteIcon }) {
            "Portable preference listCompleteIcon is invalid."
        }
        require(CelebrationIcon.entries.any { it.key == preferences.dailyListCelebrationIcon }) {
            "Portable preference dailyListCelebrationIcon is invalid."
        }
        require(preferences.dailyListAutoTrashKeep in 1..999) {
            "Portable preference dailyListAutoTrashKeep is invalid."
        }
        require(
            preferences.dailyListRetention.isBlank() ||
                preferences.dailyListRetention.toIntOrNull()?.let { it in 1..999 } == true
        ) { "Portable preference dailyListRetention is invalid." }
        preferences.automaticBackupCadence?.let { cadence ->
            require(AutoBackupCadence.entries.any { it.key == cadence }) {
                "Portable preference automaticBackupCadence is invalid."
            }
        }
        preferences.automaticBackupCustomDays?.let { days ->
            require(days in AutoBackupPolicy.CUSTOM_DAYS_RANGE) {
                "Portable preference automaticBackupCustomDays is invalid."
            }
        }
        preferences.automaticBackupRetention?.let { retention ->
            require(retention in AutoBackupPolicy.RETENTION_CHOICES) {
                "Portable preference automaticBackupRetention is invalid."
            }
        }
    }

    private fun validateFlatItems(items: List<Pair<Int, Int>>, label: String) {
        requireUniquePositions(items.map { it.first }, "$label item")
        items.forEachIndexed { index, (position, indent) ->
            require(position >= 0) { "$label item position is invalid." }
            require(indent in 0..1) { "$label item indentation is invalid." }
            require(index > 0 || indent == 0) { "$label contains an orphaned sub-item." }
            if (indent == 1) {
                require(items.take(index).lastOrNull { it.second == 0 } != null) {
                    "$label contains an orphaned sub-item."
                }
            }
        }
    }

    private fun requireUniquePositions(positions: List<Int>, label: String) {
        require(positions.size == positions.distinct().size) { "$label positions must be unique." }
    }

    private fun requireUniqueIdentities(values: List<String>, label: String, allowBlank: Boolean = false) {
        if (!allowBlank) require(values.none { it.isBlank() }) { "$label UUID is missing." }
        val established = values.filter { it.isNotBlank() }
        require(established.size == established.distinct().size) { "$label UUIDs must be unique." }
    }

    private fun parseDate(value: String, label: String) {
        require(runCatching { LocalDate.parse(value) }.isSuccess) { "$label is invalid." }
    }

    private fun requireJsonObject(value: String, label: String) {
        val parsed = runCatching { json.parseToJsonElement(value) }.getOrNull()
        require(parsed is JsonObject) { "$label is invalid JSON." }
    }

    /**
     * Idea fields are stored with the stable lowercase FieldType token, while
     * kotlinx enum serialization expects the enum name. Normalize only that
     * stored token before decoding the full object so all other validation stays
     * strict.
     */
    private fun validateIdeaFields(value: String) {
        val parsed = runCatching { json.parseToJsonElement(value) }.getOrNull()
        require(parsed is JsonArray) { "Idea Log fields are invalid JSON." }
        val normalized = JsonArray(parsed.map { element ->
            require(element is JsonObject) { "Idea Log fields are invalid JSON." }
            val token = (element["type"] as? JsonPrimitive)?.contentOrNull
            val fieldType = token?.let(FieldType::fromToken)
            require(fieldType != null) { "Idea Log fields contain an unsupported field type." }
            JsonObject(element + ("type" to JsonPrimitive(fieldType.name)))
        })
        runCatching { json.decodeFromJsonElement<List<IdeaFieldDef>>(normalized) }
            .getOrElse { throw IllegalArgumentException("Idea Log fields are invalid JSON.", it) }
    }

    /**
     * Clicker fields use stable lowercase tokens for both the field type and
     * increment direction. Normalize those established on-disk tokens before
     * decoding the complete field objects.
     */
    private fun validateClickerFields(value: String) {
        val parsed = runCatching { json.parseToJsonElement(value) }.getOrNull()
        require(parsed is JsonArray) { "Clicker fields are invalid JSON." }
        val normalized = JsonArray(parsed.map { element ->
            require(element is JsonObject) { "Clicker fields are invalid JSON." }

            val typeToken = (element["type"] as? JsonPrimitive)?.contentOrNull
            val fieldType = typeToken?.let(ClickerFieldType::fromToken)
            require(fieldType != null) { "Clicker fields contain an unsupported field type." }

            var normalizedElement = element + ("type" to JsonPrimitive(fieldType.name))
            val directionToken = (element["incrementDirection"] as? JsonPrimitive)?.contentOrNull
            if (directionToken != null) {
                val direction = ClickerIncrementDirection.entries.firstOrNull {
                    it.token == directionToken.trim().lowercase()
                }
                require(direction != null) {
                    "Clicker fields contain an unsupported increment direction."
                }
                normalizedElement = normalizedElement +
                    ("incrementDirection" to JsonPrimitive(direction.name))
            }
            JsonObject(normalizedElement)
        })
        runCatching { json.decodeFromJsonElement<List<ClickerField>>(normalized) }
            .getOrElse { throw IllegalArgumentException("Clicker fields are invalid JSON.", it) }
    }

    /**
     * v1/v2 stored the documented lowercase field tokens (for example
     * `"scale"`), while current kotlinx serialization writes enum names. Both
     * are established backup representations, so normalize only the type token
     * before decoding the complete schema.
     */
    private fun validateFormSchema(value: String) {
        val parsed = runCatching { json.parseToJsonElement(value) }.getOrNull()
        require(parsed is JsonArray) { "Form schema is invalid JSON." }
        val normalized = JsonArray(parsed.map { element ->
            require(element is JsonObject) { "Form schema is invalid JSON." }
            val token = (element["type"] as? JsonPrimitive)?.contentOrNull
            val fieldType = token?.let(FieldType::fromToken)
            require(fieldType != null) { "Form schema contains an unsupported field type." }
            JsonObject(element + ("type" to JsonPrimitive(fieldType.name)))
        })
        runCatching { json.decodeFromJsonElement<List<FieldDef>>(normalized) }
            .getOrElse { throw IllegalArgumentException("Form schema is invalid JSON.", it) }
    }

    private inline fun <reified T> decode(value: String, label: String): T =
        runCatching { json.decodeFromString<T>(value) }
            .getOrElse { throw IllegalArgumentException("$label is invalid JSON.", it) }

    private fun <T> decode(
        value: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        label: String,
    ): T = runCatching { json.decodeFromString(serializer, value) }
        .getOrElse { throw IllegalArgumentException("$label is invalid JSON.", it) }
}
