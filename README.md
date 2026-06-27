# lifelogger
Android App for low friction logging of life events
# Project Goal: Custom Log Builder

## Purpose

This project is a local-first Android app for creating custom logs and recording entries with as little friction as possible.

The first use case is tracking apartment-related smell and sound incidents, but the app must not be hardcoded only for those categories. The app should allow the user to create any kind of log system, such as:

* Smell log
* Sound log
* Landlord contact log
* Maintenance issue log
* Sleep disruption log
* Health/symptom impact log
* General incident log

The app is intended for tired, stressed, or overloaded users who need to record structured information quickly without using spreadsheets directly.

## Core Design Principle

The app should prioritize low friction.

The user should be able to open the app, tap one button, fill out a simple full-screen form, and save an entry.

Avoid clutter, dashboards, complex setup, hidden workflows, or unnecessary typing.

## Required Home Screen Behavior

The home screen shows a list of log systems.

The top-right plus button creates a new log system/template.

Each log row/card has its own plus button on the right side. That button creates a new entry inside that specific log.

Tapping the log row/card itself opens the entry list for that log.

Required behavior:

* Top-right plus = create a new log system/template
* Log row/card tap = view entries for that log
* Log row/card right-side plus = add a new entry to that log

## Required Screen Structure

### Home Screen

Shows:

* App title
* Top-right plus button for creating a new log system
* List of existing log systems
* Each log row/card shows:

  * Log name
  * Optional description or entry count
  * Right-side plus button for adding an entry

### Create/Edit Log System Screen

Allows the user to create or edit a log system.

Fields should include:

* Log name
* Optional description
* Field definitions

Initial versions may use manual field creation. Later versions may support Form Markdown import.

### New Entry Screen

When the user taps the plus button on a log row/card, the app opens a full-screen new entry form for that log.

This must not be a floating dialog.

The form should show only the fields for that log system.

Every entry should automatically record a created timestamp.

The form should use large, easy controls where possible:

* Buttons
* Chips
* Single-choice selectors
* Multi-choice selectors
* Scale selectors
* Large notes box

### Entry List Screen

When the user taps the log row/card itself, the app opens a list of entries for that log.

The entry list should show entries in reverse chronological order.

Each entry card should show a short readable preview.

The screen should include:

* Log name
* Plus button for adding a new entry
* Export button for exporting that log
* Entry cards
* Edit/delete options

## Export Philosophy

CSV is not the primary human-readable format.

The main export should be a readable report where each entry is shown vertically, field by field.

Example:

Date: June 27, 2026
Time: 2:14 PM
Severity: 4
Location: Bedroom
Smell type: Smoke, chemical

Notes:
Smell strongest near bathroom fan area.

## Export Types

The app should eventually support:

### Readable Report

Primary sharing format.

Formats:

* Markdown
* Plain text
* Later PDF

This is for landlords, doctors, housing agencies, legal aid, advocates, or personal review.

### JSON Backup

Backup and restore format.

This should include:

* All log systems/templates
* All entries
* All field definitions
* App settings if needed

JSON is for protecting/restoring app data, not for normal human sharing.

### CSV Export

Compatibility format.

CSV should be available later for users who need spreadsheet-compatible structured data.

CSV is not the main reading format.

Each log should export to its own CSV because different logs may have different fields.

## Internal Data Storage

Do not store working app data as CSV.

Use a local Room database.

The app should store log templates and entries locally on the device.

Suggested data model:

### LogTemplate

* id
* name
* description
* createdAt
* updatedAt
* schemaJson
* optional formMarkdown

### LogEntry

* id
* templateId
* createdAt
* updatedAt
* valuesJson

The valuesJson field stores the user's answers for that entry.

This allows different log systems to have different fields without changing the database schema every time.

## Privacy Requirements

The app must be local-first.

Do not include:

* Ads
* Analytics
* Cloud sync
* Account system
* External tracking
* Automatic network transmission

The user's data should stay on the device unless they intentionally export it.

## Build Requirements

Use:

* Kotlin
* Jetpack Compose
* Material 3
* Room database
* GitHub Actions to build a debug APK artifact

The app should be simple, maintainable, and built in phases.

Avoid unnecessary architecture complexity.
