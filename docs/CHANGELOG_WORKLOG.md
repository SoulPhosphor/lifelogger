# Changelog / Worklog

Append a new dated entry after each meaningful session. Do not overwrite earlier entries.

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
