# UI Specification

This document locks the user interface. Every screen below is a **decision, not a suggestion**. The coding agent must build exactly what is described here. Do not redesign layouts, move buttons, change what a button does, or "improve" the navigation. If something here seems wrong, note it in the worklog and ask first.

Conventions used below:

- `[X]` = a tappable button/icon.
- A box `┌─┐` is a screen or a row in a list.
- Icons: `⚙` settings, `↓` download/backup, `🗑` delete, `+` add, `<` back.

All primary screens are **full-screen**. The only floating dialogs allowed are **destructive-action confirmations** (deleting a log or an entry) and the **format chooser** for downloads.

---

## 1. Navigation flow

**Home screen**

- **Top-left `⚙`** → Settings (restore-from-backup lives at the bottom of Settings).
- **`↓` (next to the cog)** → Backup screen (backs up every log at once).
- **Top-right `+`** → create a **new log**. This corner is reserved for this action only.
- **Tap a log row's name** → open that log's **entry list**.
- **`+` on the right side of a log row** → add a **new entry** to that log.

**Log screen (one log's entries)**

- **Top-left `<`** → back to Home.
- **`↓` (next to back)** → download this log (choose format).
- **`🗑` (next to download, still on the left)** → delete this whole log (always confirms first).
- **Top-center** → the log's name.
- **Top-right `+`** → add a new entry to this log.
- **`🗑` on the right side of an entry row** → delete that entry (always confirms first).
- **Tap an entry row** → open it as a read-only view (entries cannot be edited).

> **Design principle: high-use buttons and destructive buttons stay far apart.** The everyday `+` is always far right; delete actions live on the left and always require confirmation.

---

## 2. Screen — Home

### With logs

```
┌──────────────────────────────────────────────┐
│  ⚙   ↓          Data Dragon                   +   │
├──────────────────────────────────────────────┤
│  ┌────────────────────────────────────────┐   │
│  │ My Log                             [+] │   │
│  │ 14 Entries · Last Entry Today          │   │
│  └────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────┐   │
│  │ Another Log                        [+] │   │
│  │ 3 Entries · Last Entry June 20         │   │
│  └────────────────────────────────────────┘   │
└──────────────────────────────────────────────┘
```

- Each row shows the log name and an add-entry `[+]` on the same line.
- Below that, a line showing entry count and when the last entry was added.
- Tapping the row opens the log. Tapping `[+]` adds a new entry.
- Logs stay in the order they were created (no automatic resorting).
- Long log names truncate with an ellipsis so the `[+]` always stays visible.

### Empty state (no logs yet)

```
┌──────────────────────────────────────────────┐
│  ⚙   ↓          Data Dragon                   +   │
├──────────────────────────────────────────────┤
│                                                │
│              No logs yet.                       │
│      Tap  +  (top right) to create             │
│            your first one.                      │
│                                                │
└──────────────────────────────────────────────┘
```

---

## 3. Screen — Log (one log's entries)

```
┌──────────────────────────────────────────────┐
│  < ↓ 🗑          My Log                   +   │
├──────────────────────────────────────────────┤
│  ┌────────────────────────────────────────┐   │
│  │ Jun 27, 2:14 PM                    🗑  │   │
│  │ Sleep: 5                               │   │
│  │ Activities: exercise, sleep, art       │   │
│  │ Notes: longer text wraps onto the      │   │
│  │   next line as needed                  │   │
│  └────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────┐   │
│  │ Jun 26, 11:20 PM                   🗑  │   │
│  │ Sleep: 7                               │   │
│  └────────────────────────────────────────┘   │
└──────────────────────────────────────────────┘
```

- Entries are listed newest first. A form may instead use one selected Date or
  Date & Time field as its default sort timestamp, and may set its default
  direction to Oldest to Newest. An entry with no value in the selected field is
  never interleaved — those entries collect at the bottom of the list in either
  direction.
- The sorting controls sit under the form title and are always present, whether
  or not the log has entries: `[ Timestamp ▾ ] [ Sort: Newest ▾ ] [ ✕ Clear ]`.
  The first dropdown carries the label of the field the list is currently ordered
  by, and its menu lists every field available to order by. When the automatic
  timestamp is the only ordering available, it is a plain `Timestamp` label
  instead of a dropdown. Clear returns both to the form's configured defaults, and
  both reset when the log is reopened.
- The controls are the first item of the scrollable entry list, centred across the
  screen, and scroll away with the entries rather than staying pinned. The list
  opens at the top, and returns to the top whenever the field or the direction
  changes.
- Only fields that carry a date can take part in ordering. A Time-only field
  never can, because two times with no date cannot be placed against each other.
- Automatic creation timestamps are always recorded. Each form controls whether
  that timestamp is visible on its entry cards. When it is hidden the top line is
  not left blank: the entry's first filled-in field moves up into it and wraps
  beside the `⋮` rather than running under it.
- When visible, the top line of each card holds the entry's date/time with the delete `🗑`
  across from it on the right. The `🗑` uses the default icon color (it is **not**
  tinted red).
- Below the top line, every field that has a value is shown on its own line,
  inline as `label: value` (the label muted, the value normal). One field per
  line, going down the card. Long values wrap onto further lines; nothing is
  truncated. Notes, when present, appear last as `Notes: …`.
- Tapping `🗑` confirms then deletes.
- **Locked vs unlocked.** A locked log shows a 🔒 next to its name (and on the
  Home list). Tapping the 🔒 offers a one-way **unlock** (confirm dialog warns it
  can't be re-locked). While a log is **unlocked**, each entry shows an edit
  pencil that reopens the entry form pre-filled; saving keeps the original
  date/time and stamps an edit time. Locked entries can't be edited — only added,
  deleted, or (if enabled) appended to.
- **Follow-up notes.** When the log allows them, each entry has an "Add follow-up
  note" action. A note is a separate, time-stamped line shown under the entry as
  `↳ {time}: {text}`. Notes are append-only — they never change the original
  entry and can't themselves be edited.
- Empty state: `No entries yet. Tap + to add one.`

---

## 4. Download format chooser

Opened by the `↓` on the Log screen.

```
┌──────────────────────────────────┐
│  Download "My Log"                │
│  Choose a format:                 │
│                                   │
│  [ .md ] [ .json ] [ .txt ] [ .csv ] [ .pdf ] │
│                                   │
│              [ Cancel ]           │
└──────────────────────────────────┘
```

- `.md`, `.txt`, and `.pdf` → a readable report.
- `.json` → this log's data (template + entries) for backup/re-import.
- `.csv` → spreadsheet-compatible rows for this log.

---

## 5. Screen — Create Log

Once a log is created, it cannot be edited. It can only be deleted.

```
┌──────────────────────────────────────────────┐
│ <    New Log                           Save    │
├──────────────────────────────────────────────┤
│ Log name                                        │
│ [____________________________________]          │
│                                                 │
│ [   Build   |   Paste   ]   ← style toggle      │
│                                                 │
│  (Build)                                        │
│  ┌──────────────────────────────────────┐      │
│  │ Field 1                          [🗑] │      │
│  │ Label: [ Mood                     ]   │      │
│  │ Type:  [ Scale (number range)   ▾ ]   │      │
│  │ From [1]   To [5]                     │      │
│  │ [ ] Required                          │      │
│  └──────────────────────────────────────┘      │
│  [ + Add field ]                                │
└──────────────────────────────────────────────┘
```

- **Log name** (required). Logs have no description field.
- **Two behavior checkboxes**, chosen once at creation:
  - **Locked log** (default on): entries are create-once and can't be edited.
    One-way — it can be unlocked later from the log screen, but never re-locked.
  - **Allow follow-up notes** (default off): entries can have time-stamped notes
    appended later without changing the original.
- **Style toggle (`[ Build | Paste ]`)** picks how fields are defined. **Build is
  the default.** Switching tabs converts between the two: Paste → Build parses the
  Markdown into editable cards; Build → Paste writes the equivalent Markdown.
- **Build:** a list of field cards. Each has a Label, a Type dropdown, the inputs
  that type needs (options list, scale from/to, multiline lines, number digits,
  datetime "default to now"), and a Required checkbox. `+ Add field` appends one;
  the 🗑 removes one. Invalid cards show an inline hint and block Save.
- **Paste:** a text box for Form Markdown (see docs/FORM_MARKDOWN_SPEC.md) with a
  **"How to write it"** help panel showing the syntax and a worked example.
- **Save does not require a preview.** In Paste mode the text is parsed on Save;
  `Preview form` is an optional helper that shows the parsed fields and any
  problems. Save is enabled once there is a name (Build also requires every field
  card to be valid).
- **Field types reference**: shown inside the Paste "How to write it" panel.
  Contents:

```
text         — a single line of text
multiline    — multi-line text box. Set "lines" for visible height
date         — month/day/year picker
time         — 12-hour time with AM/PM
dropdown     — pick one item from a list
scale        — pick a number in a range. Set "from" and "to"
yesno        — Yes / No / Unknown / Not Applicable
number       — type a number. Set "digits" for max digits allowed
multiple     — pick several items from a list (tappable chips)

Any field can add "required" to prevent saving without it.
```

- The visual field builder (Build tab) is the default way to define fields; the
  paste box remains available under the Paste tab as an alternative.

---

## 6. Screen — New Entry (full-screen)

```
┌──────────────────────────────────────────────┐
│ <    New Entry                          Save    │
├──────────────────────────────────────────────┤
│ Date / time:  Jun 27, 2026, 2:14 PM (auto)     │
│                                                │
│ Rating                                          │
│ [1] [2] [3] [4] [5]                             │
│                                                │
│ Category                                        │
│ [ Dropdown selection     ▼ ]                    │
│                                                │
│ Notes                                           │
│ ┌──────────────────────────────────────┐       │
│ │                                      │       │
│ │                                      │       │
│ └──────────────────────────────────────┘       │
└──────────────────────────────────────────────┘
```

- **Full-screen. Never a dialog or bottom sheet.**
- Title: `New Entry`.
- Date/time auto-fills with the current time, shown at the top.
- Controls are generated from the log's field definitions.
- Big, easy-to-tap controls. Typing is only needed for text and number fields.
- **Save** writes the entry and returns to the previous screen.
- Save remains tappable when required fields are incomplete. Tapping it marks
  each incomplete required field, scrolls to the first one, and focuses that
  control when it accepts direct text input. Repeated Save attempts move to the
  next incomplete required field after the prior one is completed.

### Entries cannot be edited

Once saved, an entry cannot be edited — only added or deleted. There is no
`Edit Entry` screen.

---

## 7. Delete confirmations (always required)

### Delete a whole log

```
┌──────────────────────────────────────────────┐
│  Delete "My Log"?                              │
│                                                │
│  This permanently deletes this log             │
│  and all of its entries.                        │
│  This can't be undone.                          │
│                                                │
│        [ Cancel ]   [ Delete log ]              │
└──────────────────────────────────────────────┘
```

### Delete one entry

```
┌──────────────────────────────────────────────┐
│  Delete this entry?                             │
│  Jun 27, 2:14 PM                                │
│  This can't be undone.                          │
│                                                │
│        [ Cancel ]   [ Delete entry ]            │
└──────────────────────────────────────────────┘
```

- The delete button is visually distinct (red). Cancel is the safe default.

---

## 8. Screen — Settings

```
┌──────────────────────────────────────────────┐
│ <    Settings                                   │
├──────────────────────────────────────────────┤
│ Restore from backup                              │
│ [ Choose backup file… ]                          │
│ Loads data from a .json backup file.             │
└──────────────────────────────────────────────┘
```

- Times are always shown in **12-hour (AM/PM)** format. There is no time-format setting.
- **Restore from backup** sits at the bottom, away from everyday controls. Restoring **replaces all current data** with the backup contents. A confirmation dialog warns the user before proceeding.

---

## 9. Screen — Backup

Opened by the `↓` on Home. Backs up all logs and entries at once.

```
┌──────────────────────────────────────────────┐
│ <    Backup all data                            │
├──────────────────────────────────────────────┤
│ This saves every log and every entry into a    │
│ single .json file you can store safely.        │
│                                                 │
│              [ Back up now ]                     │
└──────────────────────────────────────────────┘
```

---

## 10. Field type rendering

| Field type | Shows as |
|------------|----------|
| `text` | A single-line text input. |
| `multiline` | A multi-line text box. Height matches the `lines` value. |
| `date` | A date picker (month/day/year). |
| `time` | A time picker (12-hour with AM/PM). |
| `datetime` | A date and time picker combined. Can default to the current time. |
| `dropdown` | A dropdown list. User picks one. |
| `scale` | Tappable pills if 5 or fewer numbers; dropdown if 6 or more. |
| `yesno` | A dropdown with four options: Yes, No, Unknown, Not Applicable. |
| `number` | A box where the user types a number. Limited to the max digits specified. |
| `multiple` | Tappable chips (multiple can be selected). Selected items display on one line separated by commas. |
| `tags` | A text box with an **Add** button; each saved tag is a chip with an "X". Read-only, the chips have no "X". |
| `webpage` | A box for a web address. Read-only, it shows with a small open-in-browser button beside it. |

- Required fields are marked and block saving until filled.
- Controls should be large and easy to tap.

---

## 11. Don't do these

- Do **not** move the `+` away from the top-right.
- Do **not** put delete near the `+`.
- Do **not** make any delete instant — always confirm first.
- Do **not** make New Entry a dialog or bottom sheet — it is full-screen.
- Do **not** reorder log rows based on activity — order is fixed.
- Do **not** add dashboards, charts, or summary panels.
- Do **not** add the visual field builder before its phase.
- Do **not** add pre-built templates or example logs. The app starts empty.
- Do **not** put personal information or real-world scenarios in any documentation or placeholder content.

---

## 12. Ideas

Ideas is the third Home section, after Forms and Lists. It has its own Idea Log /
Idea Entry storage rather than a mode flag on a form, so it never inherits
Form-only concepts: **there is no locking and no follow-up notes in Ideas.**

An Idea field's values are stored under the field's permanent id, not its label,
so renaming or reordering a field never disconnects the values already saved
against it.

### Home

- The `Data Dragon` title is followed by the Forms, Lists and **Ideas** toggles,
  in that order. Ideas uses the Material `OnlinePrediction` icon and takes the
  same selected-state styling as the other two.
- The last-used section is remembered, so the app can reopen on Ideas.
- An Idea Log card shows its name, its entry summary line (the same wording forms
  use), and an add-idea `[+]` on the right. **Archived ideas are not counted** —
  the summary describes what the log actively holds.
- The top-right `+` creates a new Idea Log.

### Screen — Create / Edit Idea Log

Visual builder only; there is no Paste tab for Ideas. Settings, in order:

1. Idea Log Name
2. Automatic Timestamping — default off
3. Allow Archiving — default off
4. Show Entire Idea Card — default off
5. Number of Lines Shown in Preview Mode (#) — default 20, 1–999, 3 digits max
6. Use Default Fields — default off
7. The fields builder

A new Idea Log always begins with one Text (Multiline) field, switch or no
switch. **Use Default Fields** fills in whichever of Title, Text and Tags is
missing — the field already there is the default Text field, so it is never
duplicated. Switching it back off removes nothing.

Field types offered: Title, Text (Multiline), Tags, Categories, Webpages, Date
Only, Time Only, Date/Time. Every field has an editable Label, Required (default
off) and Include in Preview Mode (default on). Fields drag into order by their
`DragIndicator` handle — no Up/Down buttons.

A Text (Multiline) field in Ideas gets four extra settings, all default off:
Word Count, Character Count, Allow Copying, and Allow Truncation in Entire Idea
Card Mode (which reveals `Only Show [n] Lines`). **These four are Ideas-only.**

Turning Allow Archiving off while archived ideas exist is refused: nothing is
saved, the switch stays on, and the screen says
**"Archived Ideas Exist" — "Archived ideas must be unarchived or deleted before
Allow Archiving can be turned off."** Archived ideas are never silently
unarchived and never made unreachable.

### Screen — Idea Log

Top bar, trailing controls, in a fixed relationship:

```
[Marked Filter] [Folder Copy] [Search] [+]       (active view, archiving on)
[Marked Filter] [Home Storage] [Search] [+]      (archive view)
[Marked Filter] [Search] [+]                     (archiving off)
```

`HomeStorage` is bundled as `res/drawable/ic_home_storage.xml`, traced from the
Google Material Symbols source: `material-icons-extended` is generated from the
older Material Icons set, which has no `home_storage`.

The marked-only star appears only when something in the current result set is
marked, and always goes **before** the archive toggle. Nothing is ever inserted
between the archive toggle, Search and `+`. With archiving off there is no
archive view at all.

`+` always creates an **active** idea, and using it from the archive view returns
to the active list so the new idea is visible.

Search opens an area directly under the top app bar: the heading `Search`, a text
field with a trailing Search button, then `Whole Word` and `Match Case` (and
`Search All Locations` in the active view of an archiving log) — all default off.
A search runs only on the Search button or the keyboard's Search key, never per
keystroke. Off/off means case-insensitive substring matching; the query is always
escaped, so it can never be read as a regular expression.

Sorting is the same system forms use — Allow Order Filtering, Use as Default Sort
Timestamp, and a default direction — with the same rule about which fields are
eligible: Date Only and Date/Time are, Time Only is not.

### Idea cards and the Idea Detail view

Cards use the form entry-card language: the automatic timestamp (when the log
shows it) on the top line with the star and `⋮` across from it; with the
timestamp off, the first shown field takes that space instead of leaving it
blank.

- **Preview Mode** (Show Entire Idea Card off) draws only fields with Include in
  Preview Mode on, and limits a multiline field to the log's Preview Mode line
  count.
- **Entire Idea Card Mode** draws every populated field at full length, unless a
  multiline field opted into its own truncation limit.
- Either way the card stays tappable, and tapping its body opens the **Idea
  Detail** view — never the editor.

A card's `⋮` menu holds Edit, Mark/Unmark, Archive or Unarchive (only when the
log allows archiving), and Delete. **Delete is always there, archived or not:
archiving never replaces deletion.**

The Idea Detail view is the complete read-only rendering: every populated field,
multiline text in full, tags, webpage buttons, any counts and copy buttons, and
the automatic timestamp. It **ignores** Include in Preview Mode, the Preview Mode
line count, and any Entire Idea Card truncation limit — those govern cards, not
this screen. The same actions are available from its top bar.

Truncation is always display-only. Stored text is never shortened, and Copy takes
the whole stored text even when the visible text was cut short.
