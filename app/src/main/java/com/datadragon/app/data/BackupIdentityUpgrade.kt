package com.datadragon.app.data

/**
 * Applies [FieldIdentityMigration] to Forms and Idea Logs read from a backup
 * written before field and option identities existed, just before they are
 * inserted. Data that already carries identities is returned unchanged, so a
 * current backup restores byte-for-byte with every UUID kept.
 */
object BackupIdentityUpgrade {

    fun form(log: BackupLog, newId: () -> String = StableUuid::createNew): BackupLog {
        if (!FieldIdentityMigration.formNeedsMigration(log.schemaJson)) return log
        val migrated = FieldIdentityMigration.migrateForm(
            FieldIdentityMigration.FormData(
                schemaJson = log.schemaJson,
                entryValues = log.entries.map { it.valuesJson },
                calendarConfigs = log.calendars.map { it.configJson },
                sortTimestampField = log.sortTimestampFieldId,
            ),
            newId,
        )
        return log.copy(
            schemaJson = migrated.schemaJson,
            sortTimestampFieldId = migrated.sortTimestampField,
            entries = log.entries.mapIndexed { i, entry -> entry.copy(valuesJson = migrated.entryValues[i]) },
            calendars = log.calendars.mapIndexed { i, calendar ->
                calendar.copy(configJson = migrated.calendarConfigs[i])
            },
        )
    }

    fun ideaLog(log: BackupIdeaLog, newId: () -> String = StableUuid::createNew): BackupIdeaLog {
        if (!FieldIdentityMigration.ideaNeedsMigration(log.fieldsJson)) return log
        val migrated = FieldIdentityMigration.migrateIdeaLog(
            FieldIdentityMigration.IdeaData(log.fieldsJson, log.entries.map { it.valuesJson }),
            newId,
        )
        return log.copy(
            fieldsJson = migrated.fieldsJson,
            entries = log.entries.mapIndexed { i, entry -> entry.copy(valuesJson = migrated.entryValues[i]) },
        )
    }
}
