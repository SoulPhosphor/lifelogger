# Extending the Backup System for Future Modes

## Purpose

Use this guide whenever Data Dragon adds a mode, table, saved configuration, independently mergeable object, or portable preference.

A feature is not complete until its irreplaceable state participates in full backup, Restore, Undo, automatic-backup change tracking, validation, and tests.

Do not create a second full-backup system for a new mode.

## Non-negotiable identity rules

A display name is not identity.

The following values may change without changing identity:

- grouping title;
- field label;
- Daily Task date;
- ordering position;
- archive, favorite, completion, or display state;
- local Room row ID.

For every object with a UUID:

1. Generate the UUID only when creating a genuinely new independent object.
2. Preserve it through rename, edit, reorder, move, export, backup, Merge, Replace, and undo.
3. A restore-existing path requires the incoming UUID and may not fall back to a random default.
4. A duplicate-as-new action generates a new UUID because it creates a second independent object.
5. Local Room IDs may be regenerated and are never serialized as Merge identity.
6. Add a unique database index for every UUID used to match during Merge.
7. Add an immutability trigger that rejects changes to an established UUID.
8. A migration may assign a UUID only to a legacy blank identity or repair a genuine duplicate before the unique index is installed.
9. Never rewrite a valid unique UUID merely because the object was renamed or its local row ID changed.
10. Test identity before and after every rename/edit path and a complete backup round trip.

Avoid a random default on a serialized property when old stored JSON lacks the property. Decoding that object repeatedly could generate a new identity on every read. Perform one explicit migration, persist the assigned identity, and then export it.

## Decide what must be protected

Include state when losing it would require the user to recreate, remember, or re-enter something.

Usually included:

- user-created groupings and records;
- drafts used as crash recovery;
- configuration that changes meaning or display;
- archive, favorite, completion, and ordering state;
- relationships between records;
- stable IDs;
- portable preferences;
- source information needed to prevent duplicates or destructive maintenance.

Usually excluded:

- rendered charts or heat-map pixels;
- caches and search indexes;
- temporary previews;
- generated reports;
- local WorkManager state;
- folder URIs and Android permission grants;
- last-backup timestamps and errors;
- other state that can be deterministically rebuilt from included data.

When uncertain whether state is user data or a cache, stop and obtain an owner ruling before implementation.

## One registry entry per category

The backup system should have an exhaustive BackupDataRegistry. Every backup category registers:

- category token;
- tables it protects;
- snapshot function;
- current payload serializer;
- validator;
- Replace handler;
- Merge handler;
- conflict comparison and the current/backup details shown during review;
- restored-count reporter;
- identity and conflict rule;
- change-revision coverage;
- supported individual-item behavior.

A conceptual adapter shape is:

    interface BackupCategoryAdapter<Payload> {
        val category: BackupCategory
        val protectedTables: Set<String>
        suspend fun snapshot(database: AppDatabase): Payload
        fun validate(payload: Payload): ValidationResult
        suspend fun replace(database: AppDatabase, payload: Payload): RestoreCounts
        suspend fun merge(database: AppDatabase, payload: Payload): RestoreCounts
    }

The exact Kotlin shape may differ, but category knowledge must not be scattered across the Settings screen, automatic runner, Undo store, and several independent codecs.

## Required steps when adding a mode

### 1. Inventory its persistence

List every new:

- Room entity and table;
- JSON column and its codec;
- preference key;
- file stored in app-private or external storage;
- relationship to an existing mode;
- derived cache or generated output.

Do not assume the new mode has only its obvious primary table.

### 2. Define stable identity

For each grouping and any child that Merge compares independently:

- add a permanent UUID at creation;
- add a unique index;
- add an immutability trigger;
- make edit methods update named mutable columns rather than replacing identity;
- define how legacy rows receive IDs;
- define whether cloning creates a new UUID;
- define parent-child remapping during restore.

If an existing object is identified by mutable text, migrate it to a stable ID before using it for Merge.

### 3. Add the backup payload

Add the mode to:

- BackupCategory;
- the included-categories manifest;
- the current payload;
- the deterministic serializer;
- per-category counts;
- validation;
- legacy conversion when applicable.

Bump the backup format version when the on-disk meaning changes. Do not rely on a missing property defaulting to an empty list; absent and deliberately empty have different Replace behavior.

### 4. Build a consistent snapshot

Add one-shot DAO queries that read all required rows in deterministic order.

The shared full snapshot builder must call the new category inside the same Room read transaction as every other category. Never let a new mode call a separate automatic-backup writer.

### 5. Define Replace

State exactly what is deleted and inserted.

Replace must:

- run inside the shared restore transaction;
- touch the category only when the file declares it present and the user selected it;
- delete children before parents when required;
- regenerate local row IDs;
- preserve UUIDs;
- rebuild relationships against the new local IDs;
- retain no stale rows from the replaced category.

### 6. Define Merge

Obtain an owner ruling for every real conflict before coding.

At minimum, define:

- the stable matching identity;
- incoming-only behavior;
- current-only behavior;
- identical-match behavior;
- differing-match behavior;
- ordering behavior;
- parent-child behavior;
- conflict reporting.

Do not silently use names as identity. Do not silently select newest, longest, current, or incoming data without an approved rule.

Every independently matched object must participate in the shared conflict policy:

- a matching UUID with identical contents is skipped without prompting;
- a matching UUID with different contents is a conflict;
- **Keep Current Data** preserves the current object;
- **Use Backup Data** applies the incoming object according to the category's approved replacement boundary;
- **Ask Me** reports every conflict during preflight and changes nothing until the user resolves all conflicts and continues.

Daily Task items are the approved exception: they are never conflicts. A task already on the receiving card keeps its current state, and a task whose UUID belongs to a card on another date stays there while the incoming copy is added with a new permanent UUID.

Conflict detection happens before the undo snapshot is replaced and before the restore transaction begins. Do not interrupt a running transaction with prompts. Daily Task card collisions are matched by date for user-facing organization: the current card owns an occupied date and retains its UUID and metadata, while the selected policy determines whether the incoming card's tasks are merged into it. When the incoming card's date is free but its UUID belongs to a card on another date, the incoming card is created on its own date with a new permanent UUID, and the current card is left unchanged.

### 7. Register change tracking

Add each protected table to the database revision triggers and BackupDataRegistry.

Add portable preference writes to the preference revision tracker.

Update the invariant test so it proves:

- every user-data table is registered or explicitly allowlisted as operational;
- every registered table has INSERT, UPDATE, and DELETE revision triggers;
- the new mode makes automatic backup dirty.

### 8. Add Restore and Undo presentation

Add the category to:

- restore category selection;
- confirmation descriptions;
- restored counts and summaries;
- conflict reports;
- Undo selected-category handling;
- individual-item restore when supported.

Exact wording and layout require owner approval before UI implementation.

The shared Restore screen conflict-policy wording is approved as:

**If there is a conflict, what would you like to have happen?**

- **Keep Current Data**
- **Use Backup Data**
- **Ask Me**

Show it only for Merge. Default to **Ask Me**, remember the last selection locally, and exclude that workflow preference from portable backup.

### 9. Test the complete path

Required tests for a new mode:

- populated snapshot includes every field;
- empty-but-present category is represented;
- Replace onto populated data;
- Merge with unmatched, identical, and conflicting identities;
- UUID stability through rename/edit and round trip;
- local ID remapping;
- orphan rejection;
- corrupted payload rejection;
- undo after Replace and Merge;
- automatic backup becomes dirty after every table mutation;
- no automatic file is written when the category has not changed;
- legacy file behavior;
- clean-install restore.

## Database coverage guard

Add an instrumentation test that reads SQLite's user tables and compares them with BackupDataRegistry.

Allowlist only tables that are intentionally operational or generated by Room, such as Room metadata and backup revision state.

The test must fail when a developer adds a user-data table without registering it. Its failure message should name the unprotected table and point to this guide.

This is the main protection against a future mode quietly existing outside full backup.

## Backup versioning rules

- Current writers emit only the current version.
- Readers explicitly convert each supported older version.
- A newer unsupported version is rejected before any restore action.
- Category presence comes from the manifest, not from default values.
- Unknown fields are tolerated only when the declared version is supported.
- A format change includes fixtures and migration tests.
- Preserve semantic values exactly; never normalize user text during backup conversion.
- Store timestamps in their existing machine-readable representation.

## File integrity rules

A backup is successful only after:

1. snapshot validation;
2. deterministic encoding;
3. checksum creation;
4. complete destination write;
5. stream close;
6. destination reopen;
7. decode, checksum, format, version, category, and count validation.

Do not rotate old automatic backups or update success history before all seven steps pass.

## Preferences

Maintain one explicit portable-preference allowlist.

When a new preference is added, decide whether it is:

- portable user intent, which belongs in backup; or
- device and execution state, which stays local.

Never serialize SharedPreferences wholesale. That would accidentally include folder URIs, permissions, worker state, timestamps, and future operational keys.

Replace may apply selected portable preferences. Merge keeps current preferences unless the owner approves a different explicit behavior.

## Calendars and heat maps across modes

A rendered heat map is derived output and is not backed up. Back up:

- its user-selected configuration;
- stable source-field identity;
- calculation and condition rules;
- color configuration or referenced preset identity;
- the underlying mode data from which it is calculated.

When calendars expand beyond Forms, do not attach them through a local Form row ID or a mutable label. Introduce a stable owner relationship such as owner category plus owner UUID, migrate existing Form calendars explicitly, and preserve calendar identity if calendars will be merged independently.

Form fields, Single / Multiple Choice options and Idea Categories have permanent ids (database v24, backup format 8), and entries store answers by those ids; calendars reference the source field, day-timestamp field and Count Matching option by id. Legacy label-keyed data is converted once by `FieldIdentityMigration` (database upgrade) and `BackupIdentityUpgrade` (restore of older backups). Do not generate missing IDs only in memory.

## Journal mode example

A future Journal mode should, before release:

1. give the Journal grouping a permanent UUID;
2. give entries UUIDs if individual entry merging is required;
3. add Journal tables and relationships to BackupDataRegistry;
4. add a journal payload and included-category token;
5. define Replace and Merge conflicts;
6. add its portable display preferences;
7. register change triggers;
8. add category, undo, corruption, UUID, and clean-install tests;
9. confirm automatic backup uses the unchanged shared full snapshot service.

## Individual-item exports

A human-readable export, CSV, Markdown file, PDF, or report is not a lossless backup unless it uses the current backup envelope and passes current validation.

If a mode gains individual-item backup:

- use the shared envelope;
- declare exactly one category;
- include exactly one top-level object;
- preserve its UUID;
- remap local IDs on restore;
- reject files containing zero or multiple top-level objects in the individual-item flow.

## Pull-request checklist

Before declaring a new mode protected, confirm all of the following:

- [ ] Every irreplaceable field is in the payload.
- [ ] Stable identities do not depend on names or local IDs.
- [ ] Rename and edit tests preserve UUIDs.
- [ ] The category is explicit in the manifest.
- [ ] The full snapshot reads it in the shared transaction.
- [ ] Replace is defined and tested.
- [ ] Merge conflicts have owner-approved rules.
- [ ] Matching UUIDs are compared before writes and participate in the shared conflict policy.
- [ ] Conflict review identifies the current and backup versions without using local Room IDs as identity.
- [ ] Undo includes it.
- [ ] Change revisions include every table and portable preference.
- [ ] Manual and automatic backup share the same writer.
- [ ] Old backups cannot erase it by omission.
- [ ] Corruption and unsupported versions are rejected.
- [ ] Clean-install restore reproduces its semantic state.
- [ ] The database coverage guard passes.
- [ ] GitHub Actions passes.

## Instruction for future AI

Before changing persistence or adding a mode:

1. Read docs/BACKUP_RESTORE_INTEGRITY_PLAN.md.
2. Read this guide.
3. Read the stable identity section of CLAUDE.md.
4. Inventory the new state.
5. Stop for an owner ruling on undefined Merge conflicts, defaults, wording, or destructive behavior.
6. Update the registry, format, restore, change tracking, tests, and documentation in the same feature work.

A mode is not finished when it can save locally. It is finished when its data can survive a complete device failure and restore without losing identity.

## Advanced Search settings (database 25, backup 9)

Forms carry `advancedSearchEnabled` in `log_templates`, added by the additive
24→25 migration with default 0. Existing table revision triggers cover updates.
`BackupLog` carries the setting through full/manual/automatic snapshots, individual
Form files, conflict comparison, Merge, Replace and Undo using existing parent
boundaries. No identities or stored answers change.

Daily Tasks stores `dailyListAdvancedSearchEnabled` in the explicit portable
preference allowlist and revision tracker. Settings Replace/Undo apply it; settings
Merge keeps current preferences. Both nullable payload fields omit their Off default
so older checksums remain valid. Missing settings mean Off. Version 9 is required
when either setting is declared. Temporary criteria and panel state are never backed
up. Older supported backup files remain readable.

## Never Allow Form Unlocking (database 27, backup 11)

Forms carry `neverAllowUnlocking` in `log_templates`, added by the additive
26→27 migration with default 0. Existing table revision triggers cover updates.
`BackupLog` carries it as a nullable field that is written only when on, so a
form without it encodes exactly as before. Version 11 is required when it is
declared. Missing means off. `templateOf` restores a form that has it as locked.
The existing `locked` field and its legacy default are unchanged.

Form fields may also carry `autoFillSource` (Auto Fill) inside `schemaJson`,
written only when set; a form declaring it also requires version 11.

## List Item Quantities fields (backup 12)

A List Item Quantities field (`LIST_ITEM_QUANTITIES`) lives in a form's
`schemaJson` like any field; its options are `ChoiceOption`s with permanent ids
and its answers use the Comparison row shape with only `item` and `first`. Its
own setting, `addQuantities`, is written only when on. No table or database
change. A form declaring such a field requires backup version 12, since an
older reader would reject the unknown field type; a decoded version-11 file
without one keeps version 11.

## List Item Quantities check boxes and average (backup 13)

The field may carry `addCheckbox`, `showCheckTotal` and `showAverage`, each
written only when on, and a saved row may carry `"checked": true`. A form
declaring any of those settings, or holding any saved check mark, requires
backup version 13, since an older reader would drop them. A decoded
version-12 file without them keeps version 12. No database change.

A Comparison field (forms and Clicker Data Logs) may carry `showAverage`
(Show Average of Differences), written only when on; it also requires
version 13.
