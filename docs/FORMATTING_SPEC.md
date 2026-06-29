# Formatting Specification

This document is the single source of truth for how the app stores, displays, and exports data. The coding agent must follow this exactly and must not improvise formatting.

> **Internal storage stays machine-readable.** The database keeps clean, machine-readable data (ISO-8601 timestamps, clean JSON). Formatting (12-hour AM/PM display, report layout, CSV escaping) is applied only at display or export time.

---

## 1. Dates and times

### Internal storage

- Store all timestamps as **ISO-8601 strings with a timezone offset**, e.g. `2026-06-27T14:14:00-05:00`.
- `createdAt` is set automatically when an entry is saved. Entries are never edited, so there is no `updatedAt`.

### Display

- Times are always displayed in **12-hour (AM/PM)** format. There is no user setting.
- Formatting affects display only, never the stored value.

| Time | Date | Combined |
|------|------|----------|
| `2:14 PM` | `June 27, 2026` | `June 27, 2026, 2:14 PM` |

---

## 2. Readable report (primary export)

This is the format for sharing with other people. It reads cleanly top-to-bottom. **It is not a spreadsheet.** Each entry is laid out vertically, field by field.

### Structure

```
# {Log Name} Report
Date range: {first entry date} – {last entry date}
Total entries: {n}

---

## {entry date and time}

**{Field label}:** {value}
**{Field label}:** {value}

**Notes:**
{notes text on its own lines}

---

## {next entry date and time}
...
```

The entry heading is just the date and time — there is no `Entry` prefix.

### How each field type renders in the report

| Field type | Rendered as |
|------------|-------------|
| `text` | The text value. |
| `multiline` | Shown as a block on its own lines. |
| `date` / `time` | Formatted as 12-hour (AM/PM). |
| `dropdown` | The selected value. |
| `multiple` | Selected values joined with `, ` (comma-space). |
| `scale` | `{value} / {max}` (e.g. `4 / 5`). |
| `number` | The number as typed. |
| `yesno` | `Yes`, `No`, `Unknown`, or `Not Applicable`. |

- Empty optional fields are omitted from the report.
- Always show date range and total entries at the top.

### Plain-text vs Markdown

- `.md` export uses `#`, `##`, and `**bold**` formatting.
- `.txt` export drops the markdown markers but keeps the same vertical layout and spacing.

---

## 3. CSV export (compatibility format)

CSV is for users who want spreadsheet-compatible data. It is not the primary reading format.

### Rules

- **One CSV file per log** (different logs have different fields).
- Encoding: UTF-8, with a header row.
- Follow **RFC 4180**: wrap cells containing commas, double-quotes, or newlines in double quotes. Escape internal double-quotes by doubling them.
- `multiple` values join with `; ` (semicolon-space) so they don't collide with the CSV comma.
- `scale` exports as the raw number only (e.g. `4`), not `4 / 5`.
- Date/time columns export as ISO-8601 strings so spreadsheets can sort them.
- Empty optional fields export as empty cells.

### Column order

```
entry_id, created_at, {field 1}, {field 2}, ..., {field n}
```

---

## 4. JSON exports

### Full backup (Home → Backup screen)

One file containing all log templates (with their field definitions and original Form Markdown if used) and all entries. This is what Restore in Settings reads.

### Single-log export (Log screen → download → .json)

One file containing one template and that log's entries. Same structure as one log inside a full backup, so it can be re-imported.

Timestamps stay in ISO-8601 exactly as stored. JSON files are for backup and data portability, not for human reading.

---

## 5. Export file naming

- Lowercase the log name.
- Replace spaces with underscores.
- Strip punctuation other than underscores.
- Example: `My Log` → `my_log_report.md`, `my_log.csv`

Files are saved through the Android system "Save to…" document picker
(`ACTION_CREATE_DOCUMENT`): the user chooses the destination folder (Drive,
Downloads, etc.) and confirms the file name, and the bytes are written there. A
short toast confirms the save. This is a real save-to-location flow, not the
"share" sheet.
