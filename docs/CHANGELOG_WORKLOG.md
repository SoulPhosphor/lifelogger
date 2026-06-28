# Changelog / Worklog

Append a new dated entry after each meaningful session. Do not overwrite earlier entries.

---

## 2026-06-28 — Phase 8: CSV export + fix download-chooser order

**Summary**

Added per-log CSV export and corrected the download chooser to the approved
order.

- The download chooser order is now **.md, .json, .txt** (then **.csv**),
  matching what the user approved — previously it followed UI_SPEC's
  `.txt/.md/.json` ordering. Updated UI_SPEC §4 to match. All three readable/data
  formats were already present; this only reorders them and adds CSV.
- `CsvBuilder` produces one CSV per log (FORMATTING_SPEC §3): header row, then
  one row per entry. RFC-4180 quoting (cells with comma / quote / newline are
  quoted, internal quotes doubled); `multiple` joins with `; `; `scale` exports
  the bare number; date/time export the stored ISO-8601 string. Columns are
  `entry_id, created_at, {fields…}, notes`.

**Decision to confirm**

- A trailing **`notes`** column was added so notes aren't lost in CSV. §3's column
  list doesn't mention notes; say the word and I'll drop the column.

**Scope notes**

- PDF (Phase 9) is intentionally **not** built; the user asked to ignore it.

---

## 2026-06-28 — Phase 6: JSON backup and restore

**Summary**

All app data can now be backed up to a single `.json` file and restored from
one, and a single log can be exported as `.json` for re-import.

- `BackupFile` / `BackupCodec` define the on-disk shape (FORMATTING_SPEC §4): a
  format/version header plus every log (template fields + original Form Markdown)
  and its entries. `schemaJson`/`valuesJson` are kept verbatim and timestamps
  stay ISO-8601, so a backup → restore round-trip is lossless.
- `BackupRepository` builds the full backup and restores in one transaction
  (`withTransaction`): it clears both tables and re-inserts, preserving ids so
  entry → log links survive.
- **Backup** (Home `↓` → Backup screen "Back up now") writes the JSON to a
  user-chosen location via the system create-document sheet.
- **Restore** (bottom of Settings) opens a file via the system picker, then shows
  a confirmation warning that it **replaces all current data** before applying it.
- **Single-log `.json`** is now the third option in the Log screen's download
  chooser (alongside `.txt`/`.md`); it shares one log's template + entries via the
  existing FileProvider share path. File naming centralized in `ExportNaming`.

**Scope notes**

- No schema/version change — only new read/delete DAO queries were added.
- CSV export is **Phase 8**, PDF **Phase 9**.

---

## 2026-06-28 — Phase 5: Readable report export

**Summary**

The Log screen's `↓` now exports a readable report for that log as `.txt` or
`.md` through the Android share/save sheet.

- `ReportBuilder` produces the vertical, field-by-field report from
  FORMATTING_SPEC §2: `# {name} Report`, date range (first → last entry), total
  entries, then one block per entry (`## Entry — {date, time}`, each field as
  `**Label:** value`, multiline values as their own block, and a `**Notes:**`
  block). Plain text uses the same layout without the Markdown markers. Empty
  optional fields are omitted.
- Entries read chronologically (oldest first) in the report; the date range still
  runs first → last.
- File naming follows §5 (lowercase, spaces → `_`, punctuation stripped):
  `my_log_report.md` / `.txt`.
- `↓` on the Log screen opens a format chooser (`.txt` / `.md`); the chosen
  report is written to `cacheDir/exports` and shared via a `FileProvider` content
  URI (provider + `res/xml/file_paths.xml` added).

**Scope notes (respecting the phase plan)**

- Single-log `.json` export is shown as deferred in the chooser — it lands with
  **Phase 6** (JSON backup & restore). CSV is **Phase 8**, PDF **Phase 9**.
- Times in the report use 12-hour (AM/PM), matching the rest of the app.

---

## 2026-06-28 — Phase 4: Dynamic entry forms

**Summary**

Entries are now real: the New Entry screen is generated from each log's
`schemaJson`, entries save to the database, and the entry list shows them
newest-first.

- `LogEntry` entity (`id`, `templateId`, `createdAt`, `updatedAt`, `valuesJson`)
  plus `LogEntryDao` with the full CRUD surface (insert / update / delete /
  observe). Database bumped to **v4** with a real `MIGRATION_3_4` that creates the
  `log_entries` table and its `templateId` index (no template data lost).
- `createdAt` is stored as **ISO-8601 with offset** per FORMATTING_SPEC §1
  (machine-readable storage; 12h/24h is applied only at display time). The DAO
  orders entries `createdAt DESC, id DESC` for reverse-chronological listing.
- `EntryValues` owns the shape of `valuesJson` (a JSON object keyed by field
  label) and all read-time formatting: `multiple` → JSON array, `scale`/`number`
  → raw text, `date`/`time`/`datetime` → machine patterns; rendered back as
  `4 / 5`, `Jun 27, 2:14 PM`, etc.
- New Entry screen generates a control per field type (FORM_MARKDOWN_SPEC /
  UI_SPEC §10): text, multiline (height from `lines`), number (digit-limited),
  dropdown, yesno (4-option dropdown), scale (≤5 pills / ≥6 dropdown), multiple
  (chips), and date / time / datetime pickers. `datetime` with `default: now`
  pre-fills. Required fields block Save. A free-text **Notes** box is stored under
  a reserved key so it can never collide with a user field.
- Log screen lists entries (timestamp, value summary, notes preview). Home rows
  now show real "N entries · last entry today/yesterday/MMM d".

**Scope notes (respecting the phase plan)**

- Tap-an-entry-to-edit and the per-entry `🗑` delete are **Phase 7** — entry rows
  are display-only for now. The DAO's `update`/`delete` exist ready for it.
- Export (`↓`), backup, and restore remain later-phase stubs.
- All times render 12-hour (AM/PM); see the time-format removal entry below.

---

## 2026-06-28 — Remove the 24-hour time option (user instruction)

Per the user's instruction, the app uses **12-hour (AM/PM) time only** — there is
no 24-hour option.

- Removed the time-format toggle from the Settings screen; Settings now holds
  only Restore-from-backup. (Display was already 12-hour everywhere, so no
  formatting logic changed — `EntryValues` and the time picker stay 12-hour.)
- Updated UI_SPEC §8 (no time-format setting) and FORMATTING_SPEC §1 (12-hour is
  the only display format) to match.

---

## 2026-06-28 — Remove the description (user instruction)

Per the user's explicit instruction, logs have **no description** anywhere. The
description was specified in the original docs (UI_SPEC §5, BUILD_PHASES entity,
FORM_MARKDOWN_SPEC `>` line); all of that has been removed:

- Dropped the Description box from the Create Log screen and the `description`
  column from `LogTemplate` (database v3; recreated cleanly on upgrade).
- The parser no longer reads `>` lines as a description — they are now ignored
  and listed under skipped lines.
- Updated UI_SPEC, BUILD_PHASES, and FORM_MARKDOWN_SPEC to match (this resolves
  and supersedes the earlier name/description conflict — there is no description
  to reconcile anymore).

This also settles the open question from the Phase 3 entry below.

---

## 2026-06-28 — Phase 3: Form Markdown import

**Summary**

Pasting Form Markdown now produces a real schema and a preview before saving.

- `FieldType` (the ten types from FORM_MARKDOWN_SPEC §2) and a `@Serializable`
  `FieldDef` model; added kotlinx.serialization (plugin + json) per the README
  tech stack.
- `FormMarkdownParser` converts Form Markdown into a list of `FieldDef`
  (serialized to `schemaJson`). It follows the spec's parser rules: first `#` is
  the name, `>` is the description, `##` starts a field, `options:` + `-` items,
  bare `required`, scale `from`/`to`, `lines`, `digits`, `default: now`.
  Unrecognized lines are collected as "skipped" and duplicate / invalid fields as
  "issues" — it never throws on bad input.
- Create Log screen: "Preview form" parses the text and shows each parsed field,
  plus any skipped lines and problems. Save is enabled only after a preview and
  stores the parsed `schemaJson` **and** the original Form Markdown text.
- `LogTemplate` gained a `formMarkdown` column; database bumped to v2 with a
  proper `MIGRATION_1_2` (no data loss).

**Conflict noted (per README "if two documents conflict… note it and ask")**

- UI_SPEC §5 gives the Create Log screen separate **Log name** and
  **Description** fields, while FORM_MARKDOWN_SPEC §1 says the pasted Markdown's
  `#`/`>` lines are the name/description. Both can set the same thing.
- **Resolution chosen (pending user confirmation):** the dedicated boxes are
  authoritative; if a box is empty and the paste has a `#`/`>` line, the box is
  auto-filled from it. This keeps both documents working. Easy to switch to
  "paste is the single source" or "ignore `#`/`>`" if the user prefers.

**Scope notes (respecting the phase plan)**

- Entry forms are **not** generated from `schemaJson` yet — rendering controls
  from the schema is **Phase 4**.
- Real log deletion is still **Phase 7** (DAO method exists, UI shows the
  confirmation dialog only).

**Known issues**

- Same later-phase stubs remain (download, restore, entry save, real delete).
- Full compile/APK happens on GitHub Actions (sandbox blocks Android deps).

**Next steps**

- Confirm CI is green.
- Then **Phase 4**: `LogEntry` entity + DAO, generate the New Entry form from
  `schemaJson`, save entries with an auto `createdAt`, and show them newest-first
  on the Log screen. (This is the first genuinely usable version — worth a
  hands-on test.)

---

## 2026-06-28 — Phase 2: Local database for log systems

**Summary**

Added the local Room database and made log templates persist. Home now loads
real templates from the database and Create Log saves real records.

- Dependencies: Room (runtime + ktx + compiler via KSP), KSP plugin, and the
  Compose `lifecycle-viewmodel-compose` / `lifecycle-runtime-compose` helpers.
  All versions pinned in `gradle/libs.versions.toml`.
- `LogTemplate` entity with exactly the Phase 2 fields: `id`, `name`,
  `description`, `createdAt`, `schemaJson`.
- `LogTemplateDao` with create / read / delete only — **no update**, because
  logs cannot be edited after creation.
- `AppDatabase` (Room, local only, singleton).
- ViewModels (`HomeViewModel`, `CreateLogViewModel`, `LogViewModel`) using
  `AndroidViewModel` + the default factory — no DI library added.
- Home screen observes templates from the DB and renders them in creation order;
  empty state shown when there are none.
- Create Log "Save" inserts a real template (name required) and returns Home,
  where it appears immediately.
- Log screen loads the real template so its title shows the saved log name.
- Removed the Phase 1 in-memory `Placeholder.kt`.

**Security fix folded in (from PR review)**

- Set `android:allowBackup="false"` (plus `fullBackupContent="false"` and an
  explicit `data_extraction_rules.xml` that excludes all data from cloud backup
  and device transfer). This enforces the README's local-first rule now that a
  real database exists, so app data cannot leave the device via a system path.

**Scope notes (respecting the phase plan)**

- The Form Markdown paste box is saved-around for now: new templates store an
  empty schema (`"[]"`). The parser that turns Form Markdown into `schemaJson`
  is **Phase 3**, and storing the original Form Markdown text is also Phase 3.
- Entries do not exist yet (Phase 4), so log rows show "No entries yet" and the
  Log screen shows the empty-entries state.
- The delete-log button still only shows its confirmation dialog; wiring real
  deletion is **Phase 7**. The DAO delete method exists per the Phase 2 spec but
  is intentionally not yet connected to the UI.

**Known issues**

- Same as above: later-phase buttons (download, preview, restore, real delete,
  entry save) remain intentional stubs.
- Full compile/APK still happens on GitHub Actions (the dev sandbox blocks
  Android dependency downloads).

**Next steps**

- Confirm CI is green and the debug APK builds with Room.
- Then **Phase 3**: Form Markdown paste screen + parser → `schemaJson`, a field
  preview, and storing the original Form Markdown with the template.

---

## 2026-06-28 — Phase 1: Navigation skeleton

**Summary**

Scaffolded the Android project and built Phase 1 (navigation skeleton) from the
canonical phase order in the README.

- Single-module Gradle project with a version catalog (`gradle/libs.versions.toml`).
  Kotlin 2.0.21, AGP 8.7.3, Gradle wrapper 8.11.1, Jetpack Compose (BOM
  2024.10.01) + Material 3, Navigation Compose. `minSdk` 26, `compileSdk`/
  `targetSdk` 35, JDK 17.
- `MainActivity` hosts a `NavHost` (`navigation/DataDragonNavHost.kt`) with routes
  defined centrally in `navigation/Destinations.kt`.
- Screens, matching docs/UI_SPEC.md layout and button placement:
  - **Home** — top-left `⚙` Settings and `↓` Backup, top-right `+` creates a new
    log (reserved corner). Log rows show name + entry-count line; tapping a row
    opens its entry list, the per-row `+` adds an entry. Empty state included.
  - **Log (entry list)** — left cluster `<` back / `↓` download / `🗑` delete-log,
    centered log name, top-right `+` add entry. Entry rows newest-first with a
    per-row `🗑`. Delete buttons open confirmation dialogs (visual only in
    Phase 1).
  - **Create Log** — name + description fields, expandable "Field types"
    reference panel, Form Markdown paste box, "Preview form" button.
  - **New Entry** — **full-screen** (never a dialog), auto date/time line at top,
    placeholder Notes box.
  - **Settings** — 12-hour (default) / 24-hour time toggle; "Restore from backup"
    pinned to the bottom.
  - **Backup** — "Back up now" stub.
- Placeholder, in-memory data only (`data/Placeholder.kt`); no database, network,
  accounts, analytics, or ads. No personal information in code or docs.
- GitHub Actions workflow (`.github/workflows/build.yml`) builds a debug APK and
  uploads it as an artifact on every push.

**Known issues**

- Buttons for later phases (download, save, restore, delete confirmation,
  preview) are present per the locked UI but are intentionally inert stubs.
- The build could not be compiled in the dev sandbox because Android
  dependencies (`dl.google.com`) are blocked by the network policy there. The
  Gradle wrapper itself was verified to bootstrap and run; the real
  debug-APK compile happens on GitHub Actions, which has full network access.

**Next steps**

- Confirm the CI workflow produces a green build and a downloadable debug APK.
- Then begin **Phase 2**: Room database + `LogTemplate`, and load real templates
  on the Home screen.
