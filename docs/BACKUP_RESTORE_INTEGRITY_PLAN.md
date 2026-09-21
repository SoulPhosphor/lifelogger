# Backup and Restore Integrity Plan

## Purpose

Make every Data Dragon backup path protect all irreplaceable user data, restore it safely, and remain difficult to break when new modes are added.

This plan covers:

- manual full backup;
- automatic full backup;
- Replace and Merge restore;
- the pre-import Undo snapshot;
- individual-item backup and restore where the app exposes it;
- portable user preferences;
- stable identity and future data modes.

The implementation should make the smallest coherent change set that provides complete protection. It must not create separate backup formats for manual and automatic backup.

## Current audit

The Room database is named data_dragon.db and is currently version 18. It contains thirteen user-data tables.

| Domain | Tables | Current full backup status | Required action |
|---|---|---:|---|
| Forms | log_templates, log_entries, entry_notes | Included | Preserve every field and stable identity |
| Form calendars | calendars | Included | Preserve configuration and future owner identity |
| Lists | checklists, checklist_items | Partially included | Add the checklist draft flag, which is currently lost |
| Idea Logs | idea_logs, idea_entries | Missing | Add complete snapshot, Replace, Merge, counts, and tests |
| Saved calendar colors | color_presets | Missing | Add stable identity and complete backup support |
| Daily Tasks | daily_lists, daily_list_items | Missing | Add complete snapshot and conflict-safe date merging |
| Clicker Data | clicker_logs, clicker_cards | Missing | Add complete snapshot, including ordering and timestamps |
| User preferences | data_dragon_settings SharedPreferences | Missing | Add portable preferences only |
| Undo snapshot | pre_import_snapshot.json | Uses the incomplete payload | Move to the complete payload and atomic writes |

The existing BackupFile version 2 contains Forms, form entries, follow-up notes, form calendars, Lists, and List items. It omits Idea Logs, Daily Tasks, Clicker Data, saved color presets, and preferences.

Additional integrity findings:

- BackupRepository.buildFull reads related tables through separate DAO calls without one enclosing read transaction. A concurrent edit can produce a snapshot whose parent and child reads represent different moments.
- Checklist.draft is not serialized.
- Backup decoding does not reject the wrong format identifier or a future unsupported version before restore.
- New categories represented only by default empty lists would make an old file indistinguishable from a deliberately empty current category.
- The manual destination is treated as successful after an output stream closes; the saved file is not reopened and verified.
- UndoSnapshotStore writes directly to its final file and saves the snapshot after the database restore. A crash or file-write failure can leave the restore completed without a dependable undo file.
- Restore catches file and database failures together, so an undo-save failure can be reported as if the database restore failed after the database has already changed.
- UUID preservation works in the audited rename paths, but UUID immutability and uniqueness are not enforced structurally.
- PR #12 implements useful automatic-backup ideas but is based on an older app state, backs up only the old BackupFile contents, uses a fixed daily interval, and must not be merged as-is.

## Approved product behavior

### Backup coverage

A full backup protects all irreplaceable user data and portable user preferences.

It includes:

- every row and user-controlled field in all current Room user-data tables;
- crash-recovery List drafts and their items;
- all configuration stored inside JSON columns;
- calendar configurations and saved color presets;
- Daily Task maintenance, renewal, completion, favorite, source, and ordering state;
- Clicker grouping and card UUIDs, fields, values, timestamps, and display settings;
- portable user preferences, including navigation preferences, mode visibility, list behavior, Daily Task preferences, automatic-backup cadence, and automatic-backup retention.

It excludes:

- the selected automatic-backup folder URI;
- the Android persisted folder permission;
- the automatic-backup enabled flag;
- the last successful automatic-backup time and destination;
- automatic-backup errors, dirty revisions, WorkManager identifiers, and local attempt history;
- cache files, generated previews, rendered heat maps, reports, and other reproducible output;
- the Room database file, WAL file, and shared-memory file as raw files.

### Raw database decision

Do not place raw SQLite files in the portable backup.

All current modes share one Room database; there is not a separate database per mode. Copying only data_dragon.db can miss committed data still present in its WAL file. Copying the database, WAL, and shared-memory files is tied to SQLite and Room schema details and is not a safe portable restore format across app versions.

The portable, versioned JSON snapshot is the authoritative backup. It must include every persistent user-data table and be built inside one Room read transaction.

### Automatic backup

The user can choose:

- Daily;
- Weekly;
- a custom number of days.

Automatic backup is off by default. It cannot be enabled until a folder has been selected and verified.

Use Android WorkManager for durable background execution and a foreground overdue check as a fallback. Both call the same coordinator and the same full-backup writer.

Automatic backup is change-aware:

- A small database revision increases whenever protected Room data changes.
- A portable-settings revision increases whenever a backed-up preference changes.
- The snapshot records the revisions it contains.
- A backup is written only when the selected interval is due and at least one protected revision is newer than the last successful backup.
- If the app is unused and nothing changes, no backup file is created and no rotation occurs.
- The coordinator schedules unique one-time work when a used session or data change needs future protection. It does not keep producing unchanged periodic backups.
- The foreground fallback runs an overdue dirty backup immediately and repairs missed or delayed scheduling.
- If data changes during a write, only the captured revision is marked protected. The newer revision remains dirty for the next backup.

The exact custom-day validation range, retention range, and initial defaults must be confirmed before the user-interface phase. Retention is a portable preference. Rotation deletes only files created by automatic backup and never counts or deletes manual backups or unrelated files.

The Settings screen always shows a human-readable description of the currently selected automatic-backup folder. Do not show only a raw content URI. It also shows the last successful automatic backup on this device and a clear current-device error when the location becomes unavailable.

### Device transfer

Folder access is device-specific. A restored backup may restore the cadence and retention preference, but it never restores or invents access to the old phone's folder.

On a fresh device:

- automatic backup remains off;
- no folder is selected;
- the user chooses a folder through Android's folder picker;
- local automatic-backup history begins after the first verified success on that device.

On the same device, restoring app data does not overwrite the currently valid local folder permission or local backup history.

### Replace and Merge

Restore remains the overall action. Replace and Merge are the application modes.

Replace:

- changes only the categories selected by the user;
- makes each selected, present category match the backup;
- clears a category only when the file explicitly declares that category as included;
- leaves categories absent from an older file untouched;
- applies portable preferences when Preferences is selected;
- regenerates local Room row IDs as needed while preserving UUIDs.

Merge:

- adds unmatched groupings;
- uses permanent UUIDs to identify the same grouping;
- preserves the existing incoming-wins whole-group behavior for Forms and ordinary Lists unless the owner explicitly changes it;
- applies the same explicitly documented whole-group rule to Idea Logs and Clicker Data;
- keeps current portable preferences rather than trying to combine scalar settings;
- never treats a display name, title, date, position, or local Room ID as identity.

Daily Tasks use the following approved Merge rule when both databases contain a card for the same date:

1. Keep the current card, its UUID, title, favorite state, completion history, maintenance state, renewal state, and current ordering.
2. Match tasks by their immutable item UUID.
3. Skip an incoming task whose UUID and contents are identical.
4. If the same task UUID has different contents, keep the current version and report a conflict.
5. Add incoming tasks with new UUIDs.
6. Keep incoming task sequences together. If a matching top-level task already exists, attach new incoming sub-items to that current sequence. Do not create orphaned indented rows.
7. Two separately created tasks with identical visible wording but different UUIDs are distinct and both survive.
8. Report added, skipped, and conflicted counts.

### Individual-item files

Audit every existing individual-item export and restore entry point.

- Continue using the same versioned envelope as a full backup.
- Mark the included category explicitly.
- Extend individual restore to every data type for which the UI claims support.
- A single-item file must contain exactly one restorable top-level item.
- Do not describe the feature as supporting any data type while the decoder recognizes only Forms and Lists.

## Stable identity contract

### Audit result

The current audited rename and edit paths preserve the parent UUID for Forms, Lists, Idea Logs, Daily Task cards, and Clicker groupings:

- Forms and Lists use targeted SQL updates that do not touch UUID.
- Idea Log updates use targeted SQL updates.
- Daily Task title/date/status updates do not touch UUID, and loaded task rows carry their existing item UUID.
- Clicker grouping edits load the existing entity and copy it, preserving UUID.

The existing restore code also preserves known Form and List UUIDs. A legacy version-1 object without a UUID receives one when it first becomes a current object.

### Required hardening

UUID safety must become an enforced invariant rather than a convention.

1. A UUID is created exactly once for a genuinely new user object.
2. Rename, edit, move, reorder, archive, favorite, lock, date change, export, backup, Merge, Replace, and undo preserve the UUID byte-for-byte.
3. Duplicating an object as a new independent object receives a new UUID. Restoring or transferring the same object preserves its UUID.
4. Local Room row IDs are not portable identities. Restore may regenerate them and must remap parent-child relationships.
5. Add unique indexes for every UUID used as a Merge identity. Before adding an index, a migration repairs only blank or truly duplicated legacy values. It must not rewrite valid unique UUIDs.
6. Add database triggers that reject UPDATE attempts which change an established UUID. Run any one-time repair before installing the trigger.
7. Add a stable UUID to color_presets because presets are global, independently mergeable user objects.
8. Preserve existing UUIDs in the backup payload. Do not use a normal new-object constructor during restore.
9. Replace ambiguous direct construction with explicit creation paths such as createNew and restoreExisting. The restore path requires an incoming UUID; the new-object path is the only path allowed to generate one.
10. Do not place a random UUID default in a serialized type if decoding an older stored object would generate a different UUID every time it is read.
11. Add rename/edit identity tests for every UUID-bearing model and semantic round-trip tests that ignore local row IDs but compare every UUID.

Only add child-record UUIDs where the child is independently matched or referenced during Merge. Daily Task items and Clicker cards already have UUIDs. Ordinary Form entries, follow-up notes, ordinary List items, Idea entries, and form calendars are currently restored inside their parent grouping and do not require a disruptive UUID migration solely to copy them losslessly. If future behavior merges those children individually, add stable UUIDs before implementing that merge.

Form fields are a separate legacy identity issue: FieldDef currently uses its label as the stored value key, whereas Idea and Clicker fields already have stable IDs. Do not silently generate Form field IDs while decoding old schema JSON. Before field-level Form merging or generalized cross-mode calendar ownership is implemented, design and test an explicit migration for Form schema JSON, entry values, sorting references, and calendar configuration references.

## Backup format revision

Introduce a new backup format version while retaining readers for supported legacy versions.

The new envelope should contain:

- format identifier;
- format version;
- unique backup ID;
- creation timestamp;
- source app version and Room schema version for diagnostics;
- explicit included-categories manifest;
- payload containing all included categories;
- portable preferences when included;
- deterministic integrity checksum over the canonical payload;
- per-category counts useful for validation and the restore summary.

Use an explicit payload object rather than relying on missing fields decoding to empty lists.

Compatibility rules:

- Reject a file whose format identifier is not Data Dragon.
- Reject a future version the installed app cannot safely interpret.
- Convert supported older versions into the current in-memory model before validation.
- Version 1 and version 2 files declare only the legacy categories they truly carried.
- Unknown future fields may be tolerated only inside a supported version; a future version is not silently downgraded through ignoreUnknownKeys.
- An absent category is different from a present empty category.
- Preserve legacy UUID behavior: assign a UUID once when importing an object that genuinely had none, then persist and export it thereafter.

## Central backup architecture

Create one backup service used by every backup path.

Recommended responsibilities:

- BackupDataRegistry: exhaustive category and protected-table registry.
- BackupSnapshotBuilder: reads one consistent snapshot inside a Room transaction.
- BackupCodec: version conversion, deterministic encoding, decoding, and checksum.
- BackupValidator: format, version, checksum, UUID, relationship, JSON-field, and count validation.
- BackupRestoreService: category-aware Replace, Merge, and undo.
- BackupFileWriter: verified manual and automatic writes.
- AutoBackupCoordinator: folder state, dirty revisions, due calculation, WorkManager, rotation, and reporting.

Each category adapter owns:

- its category token;
- protected tables;
- snapshot query and payload conversion;
- validation;
- Replace behavior;
- Merge behavior;
- restored counts;
- identity rules.

Automatic backup and manual backup must call the same full snapshot builder and codec. Undo uses the same complete payload. No mode may maintain a private full-backup implementation.

## Consistent snapshots and integrity

### Snapshot

- Build the entire payload inside one Room read transaction.
- Read parents and children in deterministic order.
- Capture the protected data revision in that same transaction.
- Validate that every child belongs to an included parent.
- Encode only after the transaction returns a complete in-memory snapshot.
- Compute the checksum over a canonical payload representation.

### Manual write

- Build and validate the snapshot before opening the user's destination.
- Write verified bytes from memory or an app-private temporary file.
- Close the output stream, reopen the destination, decode it, verify the checksum, format, version, and counts, and only then report success.
- Report a failed or unverifiable destination as failure.
- Never change automatic-backup history because a manual backup succeeded.

### Automatic write

Retain the useful safety principles from PR #12:

- use the same payload as manual backup;
- use a dedicated automatic-backup filename prefix;
- create a new file before removing an older same-slot file;
- delete a partial newly created file after write failure;
- record success only after reopening and validating the completed file;
- retry after failure;
- keep manual and unrelated files outside rotation;
- treat a changed destination as immediately needing its first backup;
- serialize concurrent foreground and worker attempts through one runner.

Improve folder setup:

- acquire the new persisted URI permission first;
- perform a real create, write, read, and delete probe;
- store the destination only after the probe succeeds;
- release the old permission only after the new destination is safely active;
- retain a display label for the selected folder;
- never ignore a permission-acquisition failure.

### Undo snapshot

- Build the complete pre-import snapshot.
- Write it to an app-private temporary file, close it, reopen and validate it, then atomically rename it into the undo slot before changing the database.
- If the undo snapshot cannot be secured, do not start the restore.
- Perform the database restore in one Room transaction.
- A failed database restore leaves the database unchanged and the valid pre-import snapshot available.
- Do not report an undo-file failure as though the database rolled back when it did not.
- Undo must support all included categories and the same selected-category boundary as Restore.

## Restore validation

Validate completely before any destructive database call.

Validation includes:

- correct format and supported version;
- checksum match;
- declared categories agree with payload;
- UUIDs are present and unique within the scopes that require uniqueness;
- no parent-child references are orphaned;
- Daily Task dates are valid and unique inside the incoming category;
- positions and indentation are within accepted bounds;
- stored JSON strings decode through their domain codec;
- counts agree with the manifest;
- preferences are restricted to the portable allowlist.

Restore then runs in one transaction. All incoming local row IDs are treated as untrusted transport details. Insert parents, collect the new local IDs, and insert children against those new IDs while retaining UUIDs.

## Change revision and scheduling

Use database-backed revision state so a committed change cannot be missed by a process crash.

- A small operational backup_state row stores the current protected-data revision.
- SQLite triggers increment the revision in the same transaction as INSERT, UPDATE, or DELETE on each protected table.
- This operational row is not included in the portable payload.
- A repository invariant test verifies that every protected table in BackupDataRegistry has revision triggers.
- Portable preference writes increment a separate persisted preferences revision.
- The last successfully protected data and preference revisions remain device-local.

On app foreground:

- if enabled, verify destination state;
- if dirty and overdue, run the shared backup immediately;
- otherwise enqueue or repair one unique delayed WorkManager request for the due time.

After a successful automatic backup:

- store the captured revisions and success time;
- if newer revisions already exist, keep the state dirty and schedule the next due work;
- if clean, do not enqueue another unchanged backup.

This leaves no repeating job that writes identical files while the app is unused.

## Phased implementation

### Phase 0: Regression fixtures and inventory

- Add legacy version-1 and version-2 fixture files.
- Add a populated version-18 test database containing every current table and edge state.
- Add a machine-checkable inventory of Room user-data tables, portable preferences, and excluded operational state.
- Add a failing coverage test for any user-data table missing from BackupDataRegistry.

### Phase 1: Identity hardening

- Add and test UUID uniqueness for all current Merge identities.
- Add color preset UUIDs.
- Repair only blank or duplicate legacy UUIDs, then add unique indexes.
- Add UUID immutability triggers.
- Introduce explicit new-object and restore-existing factories.
- Add rename, edit, reorder, move-date, export, restore, and round-trip UUID tests.
- Do not perform the separate Form field-label migration in this phase.

### Phase 2: Complete versioned snapshot

- Add the new envelope, category manifest, payload, checksum, counts, and compatibility conversion.
- Add Idea Logs, Daily Tasks, Clicker Data, saved color presets, checklist draft state, and portable preferences.
- Build the full payload inside one Room read transaction.
- Add deterministic encode/decode and corruption tests.

### Phase 3: Complete restore and undo

- Implement category-aware Replace and Merge.
- Implement the approved Daily Task combination rule.
- Preserve current preferences during Merge.
- Make pre-import snapshots complete and atomic.
- Expand restore summaries and failure reports.
- Prove rollback through injected failures.

### Phase 4: Verified manual backup

- Route Back Up Now through the shared service.
- Reopen and validate the written destination before showing success.
- Update descriptions so the screen truthfully states what is protected.

### Phase 5: Change-aware automatic backup

- Reuse the sound concepts from PR #12 without merging its outdated branch.
- Add folder selection, persisted permission, readable location, validation probe, enabled toggle, cadence, retention, status, and error state.
- Add the database revision mechanism and unique WorkManager scheduling.
- Add the foreground fallback and single-run serialization.
- Add rotation and failure-recovery tests.

### Phase 6: Individual-item parity and category UI

- Audit every existing export.
- Extend the single-item envelope and restore decoder to supported data modes.
- Extend the current restore category selection beyond Everything, Form, and List.
- Obtain owner approval for exact user-visible wording and layout before UI code.

### Phase 7: Release gate

- Run all unit, migration, instrumentation, corruption, scheduling, and restore tests.
- Install over a populated older build and verify no UUID changes.
- Back up, uninstall or clear app data in a controlled test, reinstall, restore, and compare every user-data field and UUID.
- Test same-device and cross-device folder behavior.
- Verify GitHub Actions build and test jobs pass.

## Required test matrix

At minimum, tests must cover:

- every Room user-data table present in a full snapshot;
- every portable preference present and every device-specific key absent;
- checklist draft preservation;
- renamed Forms, Lists, Idea Logs, Daily Task cards, Clicker groupings, fields with stable IDs, and color presets keeping their UUIDs;
- valid UUIDs unchanged by the identity-hardening migration;
- blank and duplicate legacy UUID repair;
- local row IDs changing while UUIDs and relationships remain stable;
- version-1 and version-2 compatibility;
- future version rejection;
- wrong-format rejection;
- truncated, edited, and checksum-invalid files;
- atomic snapshot consistency during concurrent edits;
- Replace with present empty categories;
- Replace with categories absent from an old file;
- Merge for each grouping type;
- same-date Daily Task combination, sub-item attachment, current-wins conflicts, and duplicate visible wording with distinct UUIDs;
- preference behavior in Replace and Merge;
- undo across every category;
- write failure, permission loss, folder replacement, clock change, process restart, and concurrent worker/foreground attempts;
- no new file when protected revisions have not changed;
- a change made during backup remaining dirty afterward;
- rotation touching only automatic-backup files.

## Acceptance criteria

The work is complete only when:

1. A full backup contains all current user-created Room data and portable preferences.
2. Manual backup, automatic backup, Undo, and full Restore share one current payload and validator.
3. A verified current backup restores onto a clean install without semantic data loss.
4. Renaming or editing never changes an established UUID.
5. Local database IDs may change without affecting Merge identity.
6. Old backups cannot silently erase categories they never contained.
7. Automatic backup creates no unchanged backup files while the app remains unused.
8. The selected automatic-backup location is always visible when configured.
9. A success message means the saved file was reopened and validated.
10. Adding an unregistered user-data table makes automated tests fail with a clear backup-coverage error.
