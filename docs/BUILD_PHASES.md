# Build Phases

Build this app in small, testable phases.

Do not attempt to build every feature at once.

Each phase should produce a working app state before moving to the next phase.

## Phase 1: Navigation Skeleton

Goal: Prove the app structure and navigation.

Build:

* Android project setup (Kotlin, Jetpack Compose, Material 3)
* Home screen with top-right plus button
* Placeholder list of log rows
* Right-side plus button on each log row
* Navigation routes: Home, Create Log, Entry List, New Entry

Required behavior:

* Top-right plus opens Create Log screen.
* Tapping a log row opens Entry List screen.
* Tapping the right-side plus on a log row opens New Entry screen.
* New Entry screen is full-screen, not a dialog.

No database required yet. Placeholder data is acceptable.

## Phase 2: Local Database for Log Systems

Goal: Save custom log systems locally.

Build:

* Room database
* LogTemplate entity (id, name, createdAt, schemaJson)
* DAO for creating, reading, deleting log templates
* Home screen loads log systems from database
* Create Log screen saves a real log template

Logs cannot be edited after creation. They can only be deleted.

## Phase 3: Form Markdown Import

Goal: Let the user paste a structured template to define fields for a log system.

Build:

* Paste screen for Form Markdown
* Parser that converts Form Markdown into schemaJson
* Preview generated fields before saving
* Store original Form Markdown with the template

Supported field types (must match FORM_MARKDOWN_SPEC):

* text
* multiline (with lines)
* number (with digits)
* dropdown (with options)
* multiple (with options)
* scale (with from/to)
* yesno
* date
* time
* datetime

## Phase 4: Dynamic Entry Forms

Goal: Generate entry forms from each log system's schema.

Build:

* LogEntry entity (id, templateId, createdAt, updatedAt, valuesJson)
* DAO for creating, reading, updating, deleting entries
* New Entry screen generated from schemaJson
* Save entries as valuesJson
* Auto-created timestamp
* Entry List screen shows saved entries in reverse chronological order

## Phase 5: Readable Report Export

Goal: Export a human-readable report for one log.

Build:

* Download icon (`↓`) on the Log screen opens format chooser
* Markdown and plain text export for one log
* Android share/save intent

Report should include:

* Log name
* Date range
* Total entries
* Entries listed vertically, field by field

CSV and PDF are not required in this phase.

## Phase 6: JSON Backup and Restore

Goal: Protect all app data.

Build:

* Backup (`↓` on Home screen) saves all data as JSON (all templates, entries, schemas)
* Restore (in Settings, at the bottom) loads data from a JSON file
* Restoring replaces all current data with the backup contents, with a confirmation warning

JSON is for backup and restore, not normal sharing.

## Phase 7: Delete Entries and Logs

Goal: Allow deleting entries and logs.

Build:

* Delete entries with confirmation
* Delete entire logs with confirmation warning that all entries will be removed

Entries cannot be edited after creation — only added or deleted. Logs cannot be
edited after creation either. (Tapping an entry row may open a read-only view, but
there is no edit.)

## Phase 8: CSV Export

Goal: Provide spreadsheet-compatible export.

Build:

* Export one log as CSV
* Multiple-choice values joined with semicolons
* Notes quoted safely (RFC-4180)

Each log exports separately because different logs have different fields.

## Phase 9: PDF Export

Goal: Provide polished readable reports as PDF.

Build:

* Convert readable report to PDF
* One-column layout
* Keep text readable, avoid cramped table layouts

PDF export should be based on the readable report, not the CSV.

## Phase 10: Visual Field Builder

Goal: Optional UI to build templates without writing Form Markdown.

This is a comfort feature. It should never block earlier phases.

## Worklog Requirement

After every meaningful coding session, update `docs/CHANGELOG_WORKLOG.md`.

Each entry should include:

* Date
* Summary of changes
* Known issues
* Next steps
