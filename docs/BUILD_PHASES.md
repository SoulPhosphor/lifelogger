# Build Phases

Build this app in small, testable phases.

Do not attempt to build every feature at once.

Each phase should produce a working app state before moving to the next phase.

## Phase 1: Navigation Skeleton

Goal: Prove the app structure and navigation.

Build:

* Android project setup (Kotlin, Jetpack Compose, Material 3)
* Home screen with top-right plus button
* Placeholder list of log cards
* Right-side plus button on each log card
* Navigation routes: Home, Create/Edit Log System, Entry List, New Entry

Required behavior:

* Top-right plus opens Create/Edit Log System screen.
* Tapping a log card opens Entry List screen.
* Tapping the right-side plus on a log card opens New Entry screen.
* New Entry screen is full-screen, not a dialog.

No database required yet. Placeholder data is acceptable.

## Phase 2: Local Database for Log Systems

Goal: Save custom log systems locally.

Build:

* Room database
* LogTemplate entity (id, name, description, createdAt, updatedAt, schemaJson)
* DAO for creating, reading, updating, deleting log templates
* Home screen loads log systems from database
* Create/Edit Log System screen saves a real log template

## Phase 3: Form Markdown Import

Goal: Let the user paste a structured template to define fields for a log system.

Build:

* Paste screen for Form Markdown
* Parser that converts Form Markdown into schemaJson
* Preview generated fields before saving
* Store original Form Markdown if desired

Supported field types:

* text
* single choice (with options)
* multiple choice (with options)
* scale (with min/max)
* date
* time
* datetime
* yes/no

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

* Export button on Entry List screen
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

* Backup all data as JSON (all templates, entries, schemas)
* Restore all data from JSON
* Backup/restore option accessible from Settings

JSON is for backup and restore, not normal sharing.

## Phase 7: Edit and Delete

Goal: Allow editing and deleting entries and logs.

Build:

* Edit existing entries
* Delete entries with confirmation
* Delete entire logs with warning that all entries will be removed

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
