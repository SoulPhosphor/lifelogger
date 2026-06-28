# Data Dragon

A local-first Android app for creating custom logs and recording entries with as little friction as possible.

The user can create any log type with custom fields, then add entries quickly through simple full-screen forms. The app is designed for low cognitive friction — open, tap, fill, done.

---

## Read these docs in this order

A coding agent (Claude Code, Codex, etc.) should read the repo docs in this order before writing any code:

1. **README.md** (this file) — what the app is, how to behave, what to build first.
2. **docs/UI_SPEC.md** — the locked, screen-by-screen interface. The single source of truth for layout and navigation.
3. **docs/FORMATTING_SPEC.md** — how dates/times, reports, and CSV must be formatted.
4. **docs/FORM_MARKDOWN_SPEC.md** — the format users paste to define a log template.
5. **docs/BUILD_PHASES.md** — detailed phase-by-phase roadmap.

If any two documents conflict, **this README wins**, and the agent should note the conflict in the worklog rather than guessing.

---

## The one UI rule that must not be broken

This is the spine of the app. Do not redesign it.

- **Top-left `⚙`** → Settings (Restore-from-backup lives at the bottom of Settings).
- **`↓` (next to the cog)** → Backup screen (mass backup of every log).
- **Top-right `+`** on the home screen → create a **new log system / template**. This corner is reserved for this action only.
- **Right-side `+` on a log row** → add a **new entry** to that specific log (opens a **full-screen** form, never a floating dialog).
- **Tapping the log row itself** → open the **entry list** for that log.

---

## How the agent should work

- **Build one phase at a time.** Finish a phase, make sure the app compiles and runs, commit, then stop. Do not build multiple phases in one pass.
- **Respect the phase order below.** It is dependency-correct. Do not jump ahead.
- **Update the worklog every session.** After meaningful changes, *append* a new dated entry to `docs/CHANGELOG_WORKLOG.md`. Do not overwrite the file. Do not skip it.
- **Do not overbuild.** No cloud sync, no accounts, no ads, no analytics, no network calls. All data stays on the device unless the user exports it.
- **Ask before changing a locked decision.** If a design decision in this README seems wrong, raise it in the worklog and ask — do not silently change it.
- **Do not add personal information to any documentation.** This is a generic app. Docs describe features, not the user's life.

---

## Canonical phase order (supersedes any conflicting order in BUILD_PHASES.md)

| Phase | Goal | Done when |
|-------|------|-----------|
| 1 | Navigation skeleton | Home, Create-Log, Entry-List, New-Entry screens exist and route correctly with placeholder data. New-Entry is full-screen. |
| 2 | Room DB + log templates | `LogTemplate` persists locally; home screen loads real templates from the DB. |
| 3 | Form Markdown parser | Pasting Form Markdown produces a valid `schemaJson`; a preview screen lists the parsed fields; bad input shows a friendly error, never a crash. |
| 4 | Dynamic entry forms + entries | `LogEntry` persists; the New-Entry screen renders controls from `schemaJson`; entries save with an auto `createdAt`; the entry list shows them newest-first. **This is the first genuinely usable version.** |
| 5 | Readable report export | One log exports to a readable Markdown/plain-text report, via the Android share sheet. |
| 6 | JSON backup & restore | All templates + entries + schemas export to one JSON file and restore from it. |
| 7 | Edit & delete (with confirmation) | Entries and whole logs can be edited/deleted; deleting a log warns it removes all its entries. |
| 8 | CSV export | Each log exports to its own CSV (RFC-4180-safe). |
| 9 | PDF export | The readable report renders to a one-column PDF. |
| 10 | Visual / manual field builder | Optional UI to build templates without writing Form Markdown. Comfort feature only; never blocks earlier phases. |

---

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Navigation:** Navigation Compose
- **Database:** Room (local only)
- **JSON:** kotlinx.serialization (for `schemaJson`, `valuesJson`, and backups)
- **JDK:** 17
- **minSdk:** 26 (Android 8.0 — gives `java.time` for clean ISO-8601 handling)
- **targetSdk:** latest stable at build time
- **Build:** single-module Gradle project with a version catalog (`libs.versions.toml`). Pin all library versions there; use the latest stable releases available at build time.
- **CI:** GitHub Actions workflow that builds a **debug APK** and uploads it as a downloadable artifact on every push.

---

## Paste-ready kickoff prompt for the coding agent

> You are building an Android app called Data Dragon from the docs in this repository. Before writing any code, read `README.md`, then `docs/UI_SPEC.md`, `docs/FORMATTING_SPEC.md`, `docs/FORM_MARKDOWN_SPEC.md`, and `docs/BUILD_PHASES.md`.
>
> Build **only Phase 1** from the canonical phase order in the README. Do not start later phases. When Phase 1 compiles and runs, append a dated entry to `docs/CHANGELOG_WORKLOG.md` describing what you added, any known issues, and the recommended next step, then stop and tell me it's ready to review.
>
> Hard rules: keep all data on the device (no cloud, accounts, ads, analytics, or network calls); the top-right `+` creates a new log system and the per-row `+` adds an entry; the new-entry screen is full-screen, not a dialog. If any decision seems wrong, note it in the worklog and ask before changing it.
>
> **Do not add personal information, example use cases, or real-world scenarios to any documentation files.** Keep all docs generic and technical.

After Phase 1 is reviewed, send the same prompt but say "Build only Phase 2," and so on.
