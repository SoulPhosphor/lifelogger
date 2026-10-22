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
| `dropdown` | Pick one from a list | the list of choices; `choice_style: displayed` shows chips instead of a dropdown; `preselected_option: <option>` pre-selects that option on new entries |
| `multiple` | Pick several from a list | the list of choices; a `*` before an option pre-selects it on new entries |
| `scale` | Pick a number in a range | `from` and `to`; `make_dropdown: true` renders as a dropdown |
| `yesno` | Yes / No radios | `allow_unknown: true` to add an Unknown radio |
| `date` | Pick a date (month/day/year) | none |
| `time` | Pick a time (12-hour with AM/PM) | none |
| `datetime` | Pick a date and time | `default: now` to pre-fill with current time |
| `tags` | Type a tag and add it; each becomes a removable chip | none |
| `webpage` | A web address, with a button that opens it in the browser | none |
| `blood_pressure` | Two 3-digit boxes separated by `/` (systolic / diastolic) | none |
| `ratio` | Two number boxes separated by `/`, followed by an optional unit | `left_digits` and `right_digits` — max digits on each side (1 to 10, both required); `unit` — unit of measurement (optional) |
| `comparison` | Rows of an item picked from a list plus two numbers, with each row's difference and a total | the list of items; `digits` — max digits per number (optional); `whole_numbers: false` allows a decimal point; `item_label`, `first_label`, `second_label`, `total_label` rename the captions (defaults Item, Expected, Actual, Total Difference); `clicker_counting: true` counts the second column with a button, set by `button_label` (default Add), `starting_number` (default 0), `increment` (default 1) and `direction: subtract` |
| `list_item_quantities` | Rows of an item picked from a list plus its amount, with an optional total of the amounts | the list of items; `digits` — max digits per amount (optional); `whole_numbers: false` allows a decimal point; `item_label`, `first_label` rename the column captions (defaults Item, Amount); `add_quantities: true` shows the total line, captioned by `total_label` (default Total Quantity); `show_average: true` shows Average of Numbers; `add_checkbox: true` adds a check box to each row, and `show_check_total: true` then shows Total Amount of Check Marks |

Any field may also add `required` (the user must fill it before saving). Fields are optional by default. A `comparison` or `list_item_quantities` field is never required; `required` is ignored on it.

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

A `dropdown` field may add `choice_style: displayed` (chips, one pick at a time;
the default is `dropdown`) and `preselected_option:` naming one of its options
(matched ignoring capitalization), which is selected when a new entry opens. A
name that isn't one of the options is reported in the preview and nothing is
pre-selected. In a `dropdown` list a leading `*` is part of the option's name.

```
## Category
type: dropdown
options:
- Option A
- Option B
choice_style: displayed
preselected_option: Option B
```

The older `top_answer_preselected: true` (pre-select the first option) is still
accepted when pasting older Markdown; generated Markdown no longer writes it.

In a `multiple` list, a `*` before an option pre-selects it when a new entry
opens; the `*` is not part of the option's name. Any number of options may be
marked:

```
## Tags
type: multiple
options:
- *Tag 1
- Tag 2
```

An option written twice in one list (ignoring capitalization) is skipped and
reported in the preview.

Every field and every option receives a permanent internal identity when the
form is saved; entries store answers against those identities, so later
renames and reordering never disconnect saved answers. Generated Markdown lists
active options only — retired options stay with the saved form but are not
written.

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

### ratio

```
## Blood Pressure Reading
type: ratio
left_digits: 3
right_digits: 3
unit: mmHg
```

Stored as the two numbers joined by `/` (e.g. `120/80`) and displayed with the
unit after it (`Blood Pressure Reading: 120/80 mmHg`). Each box takes up to its
digit count, fewer is fine. Both boxes must be filled, or (when optional) both
left blank.

### comparison

```
## Meals
type: comparison
options:
- Turkey Dinner
- Ham Dinner
digits: 3
whole_numbers: false
first_label: Expected
second_label: Actual
```

The entry form shows a table: an item drop-down, the two number boxes, the
difference (second minus first, shown as Δ) and a remove X on each row, then the
total of the differences under it. An item can be picked in only one row.
Numbers may be negative; with `whole_numbers: false` they may also carry one
decimal point. `digits` counts digits only, never the point or the minus sign.
Either number may be left blank, so a row can be filled in at different times;
a row with a number but no item blocks Save. A row's difference and the total
count only rows that have both numbers.

With `clicker_counting: true` the second column has no typed box: it shows its
value and a button that adds (or, with `direction: subtract`, subtracts)
`increment` each press. A row's value begins at `starting_number`, which counts
as entered as soon as the row has an item, with no press needed — starting 5 in
steps of 2 gives 5, 7, 9, 11. `digits` and `whole_numbers` then apply to the
first column only. Switching counting on or off never changes saved numbers.

Stored as a JSON array of rows, each `{"item": <option id>, "first": <number>,
"second": <number>}`, with a blank number left out. The item is the option's
permanent id, so renaming an option never disconnects saved rows.


### list_item_quantities

```
## Groceries
type: list_item_quantities
options:
- Soda
- Juice
add_quantities: true
```

Works like `comparison` with one number column: each row has an item
drop-down, its amount box (captioned Amount by default) and a remove X. There
is no second column and no difference. With `add_quantities: true` a total line
(Total Quantity by default) sums every row's amount; without it there is no
total line. Amounts are whole numbers unless `whole_numbers: false`.

`add_checkbox: true` puts a check box right after each row's amount box: a
press checks it, a second press clears it. Under the table, in this order:
Total Quantity (`add_quantities: true`), Average of Numbers (`show_average:
true`, the amounts' average to two decimal places), and Total Amount of Check
Marks (`add_checkbox: true` with `show_check_total: true`). Neither of the last
two captions can be renamed.

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

`auto_fill:` turns on Auto Fill: `auto_fill: timestamp` fills new entries from
the entry's timestamp, and `auto_fill: <field label>` fills them from another
date, time or datetime field with a matching part (a `date` field takes the
date, a `time` field the time; a `datetime` field takes what the source has and
the missing part from the timestamp). A source field that is itself labeled
`Timestamp` is written `auto_fill_field: Timestamp`, which always means a field.

```
## Start
type: datetime

## Start Time
type: time
auto_fill: Start
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
