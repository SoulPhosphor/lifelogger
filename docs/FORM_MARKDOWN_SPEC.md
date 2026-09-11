# Form Markdown Specification

Form Markdown is the simple text format a user pastes to define a log's fields. The app parses it into the template's `schemaJson`. This is how logs are created until the optional visual builder ships (a later phase).

This file is the authoritative list of field types. The **Field-types reference panel** on the Create/Edit Log screen (UI_SPEC §5) must mirror it.

---

## 1. Basic structure

- The **first line** beginning with `#` is the **log name**.
- Each field starts with `##` followed by the **field label** the user will see.
- Under each field, a `type:` line sets what kind of field it is. Some field types take extra lines for their options.

```
# My Log

## Rating
type: scale
from: 1
to: 5

## Notes
type: multiline
lines: 4
```

---

## 2. Field types

| Type | What it is | Variables |
|------|-----------|-----------|
| `text` | A single line of text | none |
| `multiline` | Multi-line text box | `lines` — visible height |
| `number` | Type any number | `digits` — max number of digits allowed |
| `dropdown` | Pick one from a list | the list of choices |
| `multiple` | Pick several from a list | the list of choices |
| `scale` | Pick a number in a range | `from` and `to`; `make_dropdown: true` renders as a dropdown |
| `yesno` | Yes / No radios | `allow_unknown: true` to add an Unknown radio |
| `date` | Pick a date (month/day/year) | none |
| `time` | Pick a time (12-hour with AM/PM) | none |
| `datetime` | Pick a date and time | `default: now` to pre-fill with current time |
| `tags` | Type a tag and add it; each becomes a removable chip | none |
| `webpage` | A web address, with a button that opens it in the browser | none |
| `blood_pressure` | Two 3-digit boxes separated by `/` (systolic / diastolic) | none |

Any field may also add `required` (the user must fill it before saving). Fields are optional by default.

### Scale rendering

- Chips (tappable numbered pills that wrap to a new row when they don't fit) are the default.
- Add `make_dropdown: true` to render as a dropdown of numbers instead.
- Optional scale + chips: tapping the picked chip clears the choice. Required scale: the pick stays put.
- Optional scale + dropdown: opens blank; a "None" item at the top of the menu clears the choice. Required scale + dropdown: opens with "Choose…" and the user must pick.

---

## 3. How each type is written

### text / multiline

```
## Title
type: text

## Notes
type: multiline
lines: 4
```

### number

```
## Amount
type: number
digits: 3
```

### dropdown / multiple

```
## Category
type: dropdown
options:
- Option A
- Option B
- Option C

## Tags
type: multiple
options:
- Tag 1
- Tag 2
- Tag 3
```

### scale

```
## Rating
type: scale
from: 1
to: 5
```

Add `make_dropdown: true` for a dropdown of numbers instead of chips:

```
## Pain Score
type: scale
from: 0
to: 100
make_dropdown: true
```

### yesno

```
## Completed
type: yesno
```

Renders as two radio buttons — **Yes** and **No** — on the same line. Add
`allow_unknown: true` to include a third **Unknown** radio:

```
## Completed
type: yesno
allow_unknown: true
```

On a non-required yesno field, tapping the currently selected radio deselects
it. On a required yesno field one of the radios must be picked to save.

### blood_pressure

```
## BP
type: blood_pressure
```

Two 3-digit boxes separated by a literal `/` (systolic on the left, diastolic on
the right). Digits only. Stored as one string, e.g. `120/80`. When the field is
required, both sides must carry a 1–3 digit number. When it is optional, either
both sides are filled or both are completely blank — a half-filled value blocks
Save.

### tags

```
## Topics
type: tags
```

The person filling in the entry types a tag and presses **Add**. The tag is
trimmed, an empty one is ignored, and a tag the field already holds is refused
(compared without regard to case, so "android" can't join "Android"; the first
one keeps its spelling). Each saved tag is a chip with an "X" to remove it.
Read-only, the chips have no "X". A required `tags` field needs at least one tag.
Stored as a JSON array of strings, in the order they were added.

### webpage

```
## Source
type: webpage
```

The field's author defines only its label — never a URL. The person filling in
the entry types the address. Blank is fine when the field is optional; anything
non-blank must be a real HTTP/HTTPS webpage address with a normal domain-style
host (a dot and a valid final domain portion), so prose is never stored as one.

What the user typed is stored, trimmed and not otherwise rewritten. `https://` is
supplied only at the moment the address is opened, and only when no scheme was
typed. Displayed read-only, the address carries a small open-in-browser button;
only that button navigates.

### date / time / datetime

```
## When
type: datetime
default: now
```

---

## 4. Parser rules

- The first `#` line is the log name.
- Each `##` starts a new field; its label is the text after `##`.
- `options:` is followed by `-` list items.
- Lines the parser doesn't recognize are ignored, and the preview lists what was skipped — the parser never crashes on bad input.
- Labels must be unique within a log; duplicates are flagged in preview.

---

## 5. Preview requirement

Before a template is saved, the Create/Edit Log screen shows a **preview** of the generated form, plus any lines that were skipped. The user saves only after seeing the preview.

---

## 6. Storage

- The parsed result is stored as the template's `schemaJson`.
- The original pasted Form Markdown is also stored with the template, so it can be shown again for editing and included in `.json` exports.
