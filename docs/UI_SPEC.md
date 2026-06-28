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
- **Tap an entry row** → open it to view/edit.

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
│  │ 14 entries · last entry today          │   │
│  └────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────┐   │
│  │ Another Log                        [+] │   │
│  │ 3 entries · last entry Jun 20          │   │
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
│  │ Field 1 value · Field 2 value          │   │
│  │ Notes preview text here…               │   │
│  └────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────┐   │
│  │ Jun 26, 11:20 PM                   🗑  │   │
│  │ Field 1 value · Field 2 value          │   │
│  └────────────────────────────────────────┘   │
└──────────────────────────────────────────────┘
```

- Entries listed newest first.
- Each entry row shows the date/time, a short summary of field values, and a notes preview if there are notes.
- Tapping an entry row opens it for viewing/editing. Tapping `🗑` confirms then deletes.
- Empty state: `No entries yet. Tap + to add one.`

---

## 4. Download format chooser

Opened by the `↓` on the Log screen.

```
┌──────────────────────────────┐
│  Download "My Log"            │
│  Choose a format:             │
│                               │
│   [ .txt ]   [ .md ]   [ .json ] │
│                               │
│            [ Cancel ]         │
└──────────────────────────────┘
```

- `.txt` and `.md` → a readable report.
- `.json` → this log's data (template + entries) for backup/re-import.

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
│ Description (optional)                           │
│ [____________________________________]          │
│                                                 │
│ Define fields            [ Field types ▸ ]      │
│ ┌──────────────────────────────────────┐       │
│ │ Paste Form Markdown here…            │       │
│ │                                      │       │
│ └──────────────────────────────────────┘       │
│ [ Preview form ]                                 │
└──────────────────────────────────────────────┘
```

- **Log name** (required), **Description** (optional).
- **Define fields:** a text box where the user pastes Form Markdown (see docs/FORM_MARKDOWN_SPEC.md). The **Preview form** button shows what the form will look like before saving.
- **Field types reference** (`[ Field types ▸ ]`): an expandable panel the user can open to see what field types are available and how to write them. Contents:

```
text         — a single line of text
multiline    — multi-line text box. Set "lines" for visible height
date         — month/day/year picker
time         — 12-hour time with AM/PM
dropdown     — pick one item from a list
scale        — pick a number in a range. Set "from" and "to"
yesno        — Yes / No / Unknown / Not Applicable
number       — type a number
multiple     — pick several items from a list (tappable chips)

Any field can add "required" to prevent saving without it.
```

- A visual field builder (add fields by tapping instead of pasting) may be added in a later phase as an alternative. It would not replace the paste box.

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
- Title: `New Entry` (or `Edit Entry` when editing).
- Date/time auto-fills with the current time, shown at the top.
- Controls are generated from the log's field definitions.
- Big, easy-to-tap controls. Typing is only needed for text and number fields.
- **Save** writes the entry and returns to the previous screen.

### Editing an existing entry

Same layout, pre-filled with the entry's current values. Save updates the entry.

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
│ Time format                                     │
│   ( ) 12-hour (2:14 PM)     ← default          │
│   ( ) 24-hour (14:14)                           │
│                                                 │
│ ─────────────────────────────────────────       │
│ Restore from backup                              │
│ [ Choose backup file… ]                          │
│ Loads data from a .json backup file.             │
└──────────────────────────────────────────────┘
```

- **Time format** toggle: 12-hour (default) / 24-hour.
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
| `number` | A box where the user types a number. |
| `multiple` | Tappable chips (multiple can be selected). Selected items display on one line separated by commas. |

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
