package com.datadragon.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

/**
 * Daily List's converters. A date is stored as ISO `yyyy-MM-dd` text — the
 * exact column shape MIGRATION_15_16 creates — so the stored schema and the
 * entity stay in step.
 */
class DailyListConverters {

    @TypeConverter
    fun localDateToIso(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun isoToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}

/**
 * The app's local Room database. Local only — no network, no sync.
 */
@Database(
    entities = [
        LogTemplate::class, LogEntry::class, EntryNote::class, Checklist::class, ChecklistItem::class,
        IdeaLog::class, IdeaEntry::class, Calendar::class, ColorPreset::class,
        DailyList::class, DailyListItem::class,
        ClickerLog::class, ClickerCard::class,
    ],
    version = 19,
    exportSchema = false,
)
@TypeConverters(DailyListConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun logTemplateDao(): LogTemplateDao

    abstract fun logEntryDao(): LogEntryDao

    abstract fun entryNoteDao(): EntryNoteDao

    abstract fun checklistDao(): ChecklistDao

    abstract fun ideaLogDao(): IdeaLogDao

    abstract fun ideaEntryDao(): IdeaEntryDao

    abstract fun calendarDao(): CalendarDao

    abstract fun colorPresetDao(): ColorPresetDao

    abstract fun dailyListDao(): DailyListDao

    abstract fun clickerDao(): ClickerDao

    companion object {
        const val SCHEMA_VERSION = 19

        @Volatile
        private var instance: AppDatabase? = null

        private val UUID_SQL_EXPRESSION =
            "lower(hex(randomblob(4))) || '-' || " +
                "lower(hex(randomblob(2))) || '-4' || " +
                "substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "substr('89ab', abs(random()) % 4 + 1, 1) || " +
                "substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "lower(hex(randomblob(6)))"

        private val UUID_TABLES = listOf(
            "log_templates",
            "checklists",
            "idea_logs",
            "daily_lists",
            "daily_list_items",
            "clicker_logs",
            "clicker_cards",
            "color_presets",
        )

        private fun installUuidImmutabilityTriggers(db: SupportSQLiteDatabase) {
            UUID_TABLES.forEach { table ->
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `prevent_${table}_uuid_update` " +
                        "BEFORE UPDATE OF `uuid` ON `$table` " +
                        "FOR EACH ROW WHEN OLD.`uuid` <> NEW.`uuid` " +
                        "BEGIN SELECT RAISE(ABORT, 'UUID is immutable'); END"
                )
            }
        }

        /** Custom SQLite triggers are not part of Room's generated table schema. */
        internal val UUID_IDENTITY_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                installUuidImmutabilityTriggers(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                installUuidImmutabilityTriggers(db)
            }
        }

        /** v2 added the original Form Markdown alongside the parsed schema. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN formMarkdown TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** v4 added the log_entries table (Phase 4: saved entries). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `log_entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`templateId` INTEGER NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, " +
                        "`updatedAt` TEXT, " +
                        "`valuesJson` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_log_entries_templateId` " +
                        "ON `log_entries` (`templateId`)"
                )
            }
        }

        /**
         * v5 added per-log `locked` / `allowAppendedNotes` flags and the
         * `entry_notes` table (append-only follow-up notes). Existing logs default
         * to locked, preserving the prior create-once behavior.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN locked INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN allowAppendedNotes INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `entry_notes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`entryId` INTEGER NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_entry_notes_entryId` " +
                        "ON `entry_notes` (`entryId`)"
                )
            }
        }

        /**
         * v6 added the `checklists` and `checklist_items` tables (the Lists
         * feature). Purely additive — existing logs, entries and notes are
         * untouched.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklists` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklist_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`checklistId` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`completed` INTEGER NOT NULL, " +
                        "`indent` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklist_items_checklistId` " +
                        "ON `checklist_items` (`checklistId`)"
                )
            }
        }

        /**
         * v7 added the per-entry `marked` flag (the manual star highlight).
         * Purely additive; existing entries default to unmarked (no star).
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE log_entries ADD COLUMN marked INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v8 added the per-list `draft` flag. A draft is a new list persisted only
         * as crash protection; it's hidden from Home until Save finalizes it.
         * Purely additive; existing lists default to non-draft (normal saved
         * lists), so they stay visible exactly as before.
         */
        // internal (not private) so the migration test can apply it directly.
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE checklists ADD COLUMN draft INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v9 gave every log and list a permanent, app-internal `uuid` — its
         * stable identity across installs, used so a backup can recognize "the
         * same log/list" regardless of any later rename. Purely additive: the
         * column is added, then each existing row is backfilled with a freshly
         * generated UUID (v4). The `randomblob` calls re-run per row, so every
         * existing row gets its own distinct id.
         */
        // internal (not private) so the migration test can apply it directly.
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN uuid TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("UPDATE log_templates SET uuid = $UUID_SQL_EXPRESSION")
                db.execSQL(
                    "ALTER TABLE checklists ADD COLUMN uuid TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("UPDATE checklists SET uuid = $UUID_SQL_EXPRESSION")
            }
        }

        /** v10 added per-form timestamp visibility and custom timestamp sorting. */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN automaticTimestamping " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN sortTimestampLabel TEXT"
                )
            }
        }

        /** v11 added the form's default sort direction. */
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN sortNewestFirst " +
                        "INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * v12 added the `idea_logs` and `idea_entries` tables (the Ideas
         * feature). Purely additive — forms, entries, notes and lists are
         * untouched, so existing data survives the upgrade.
         */
        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `idea_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`uuid` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`fieldsJson` TEXT NOT NULL, " +
                        "`automaticTimestamping` INTEGER NOT NULL, " +
                        "`allowArchiving` INTEGER NOT NULL, " +
                        "`showEntireIdeaCard` INTEGER NOT NULL, " +
                        "`previewLines` INTEGER NOT NULL, " +
                        "`sortTimestampFieldId` TEXT, " +
                        "`sortNewestFirst` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `idea_entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ideaLogId` INTEGER NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, " +
                        "`updatedAt` TEXT, " +
                        "`valuesJson` TEXT NOT NULL, " +
                        "`marked` INTEGER NOT NULL, " +
                        "`archived` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_idea_entries_ideaLogId` " +
                        "ON `idea_entries` (`ideaLogId`)"
                )
            }
        }

        /**
         * v13 added the per-form `integrateCalendar` flag (the Form Editor's
         * "Integrate Calendar" toggle). Purely additive — existing forms default
         * to 0 (no calendar), so their data and behavior are untouched.
         */
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN integrateCalendar " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v14 added the `calendars` table (the Calendar feature). Purely additive
         * — forms, entries, notes, lists and ideas are untouched, so existing data
         * survives the upgrade. A form's calendars are removed with the form.
         */
        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calendars` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`templateId` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`configJson` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_calendars_templateId` " +
                        "ON `calendars` (`templateId`)"
                )
            }
        }

        /**
         * v15 added the `color_presets` table (user-saved color presets from
         * "Save Colors as Preset"). Purely additive — global to the app, not tied
         * to any form, so existing data is untouched.
         */
        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `color_presets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`colorsJson` TEXT NOT NULL)"
                )
            }
        }

        /**
         * v16 added the `daily_lists` and `daily_list_items` tables (the Daily
         * List feature). Purely additive — forms, entries, notes, lists, ideas,
         * calendars and color presets are untouched, so existing data survives
         * the upgrade. The `date` UNIQUE index is the persistence-level rule
         * that two saved Daily Lists can never share a date; dates are stored
         * as ISO `yyyy-MM-dd` text so they sort lexicographically like
         * chronologically. Backup/restore deliberately does not touch these
         * tables (Daily List backup is a separate follow-up).
         */
        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_lists` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`uuid` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`favorited` INTEGER NOT NULL, " +
                        "`genuinelyCompleted` INTEGER NOT NULL, " +
                        "`completionBlockedByCleanup` INTEGER NOT NULL, " +
                        "`maintenanceRunOn` TEXT, " +
                        "`renewalRunOn` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_lists_date` " +
                        "ON `daily_lists` (`date`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_list_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dailyListId` INTEGER NOT NULL, " +
                        "`uuid` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`completed` INTEGER NOT NULL, " +
                        "`indent` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`sourceUuid` TEXT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_list_items_dailyListId` " +
                        "ON `daily_list_items` (`dailyListId`)"
                )
            }
        }

        /**
         * v17 added the `clicker_logs` and `clicker_cards` tables (the Clicker
         * Data feature). Purely additive — every other table is untouched, so
         * existing data survives the upgrade. A log's cards are keyed to it by
         * `clickerLogId`; tracker/field definitions live in the log's
         * `fieldsJson` and per-card values in each card's `valuesJson`.
         */
        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `clicker_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`uuid` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`lastAccessedAt` INTEGER NOT NULL, " +
                        "`fieldsJson` TEXT NOT NULL, " +
                        "`displayOnlyClickerDateTime` INTEGER NOT NULL, " +
                        "`autoDateStamp` INTEGER NOT NULL, " +
                        "`autoTimeStamp` INTEGER NOT NULL, " +
                        "`allowFollowUp` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `clicker_cards` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`clickerLogId` INTEGER NOT NULL, " +
                        "`uuid` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`displayDate` TEXT, " +
                        "`displayTime` TEXT, " +
                        "`valuesJson` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_clicker_cards_clickerLogId` " +
                        "ON `clicker_cards` (`clickerLogId`)"
                )
            }
        }

        /**
         * v18 added `lastModifiedAt` to `clicker_logs` — the "Last Saved" time
         * shown on a Clicker grouping's Home row. Purely additive. Existing rows
         * have no record of their true last card change, so they are seeded from
         * `lastAccessedAt` (the closest proxy); the value becomes exact the next
         * time a card in the grouping changes.
         */
        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE clicker_logs ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("UPDATE clicker_logs SET lastModifiedAt = lastAccessedAt")
            }
        }

        /**
         * v19 makes every persisted UUID a database-enforced identity. Existing
         * unique, non-blank values are left byte-for-byte unchanged. Blank
         * values receive an identity, and when legacy rows share an identity,
         * the lowest local row id keeps it while only later rows are repaired.
         * Unique indexes are installed after that repair, followed by triggers
         * that reject any later attempt to change an established UUID.
         */
        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE color_presets ADD COLUMN uuid TEXT NOT NULL DEFAULT ''"
                )

                UUID_TABLES.forEach { table ->
                    db.execSQL(
                        "UPDATE `$table` SET `uuid` = $UUID_SQL_EXPRESSION " +
                            "WHERE trim(`uuid`) = '' OR `id` NOT IN (" +
                            "SELECT MIN(`id`) FROM `$table` GROUP BY `uuid`)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uuid` " +
                            "ON `$table` (`uuid`)"
                    )
                }
                installUuidImmutabilityTriggers(db)
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "data_dragon.db",
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                        MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
                    )
                    .addCallback(UUID_IDENTITY_CALLBACK)
                    .build()
                    .also { instance = it }
            }
    }
}
