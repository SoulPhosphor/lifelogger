# Style

This document locks the app's visible **style** — how controls, text, and
values look and read.

**Every rule here is a decision, not a suggestion.** If a rule here would be
broken by a new screen, the screen changes, not the rule. If a rule here is
wrong, the owner changes it here first, and then the code follows. Nothing gets
styled "just for this one screen".

It complements the "Text house style" in `CLAUDE.md`. It is a living document:
add a rule here the moment one is decided, rather than letting the same choice
get re-made differently on each screen.

---

## 1. The hard rules

These are the ones that keep getting broken. They are absolute.

1. **No pills. Ever.** Nothing on screen is a capsule, stadium, oval, or
   fully-rounded shape. Not buttons, not chips, not option rows, not filters.
2. **One button shape.** Every button in the app is `AppButton`. There is no
   second button style, and Material's `Button` / `OutlinedButton` /
   `FilledTonalButton` / `ElevatedButton` are never used.
3. **A button is framed exactly like a drop-down box** — same outline, same
   corner. A button and a drop-down sitting next to each other look like the
   same control.
4. **No hard-coded sizes or colors in a screen.** No `18.sp`, no
   `Color(0xFF…)`, no `RoundedCornerShape(…)` at a call site. Screens ask the
   theme for a name.
5. **A drop-down never resizes itself.** Its width is set by the widest label it
   could ever show, and stays there.
6. **A hint goes under its label, never under the control.**

---

## 2. Text

- **Labels and button captions use Title Case.** Capitalize every major word;
  minor words (a, an, the, and, or, of, to, for, in, on, at, by) stay lowercase
  unless they are the first word. Applies to every on-screen label, button, and
  disabled-state caption.
- **Body text, hints, descriptions, and confirmation questions are normal
  sentences**, not Title Case.

---

## 3. Type and color come from the theme — never from a screen

Every text size and every color is a **named value defined in
`app/src/main/java/com/datadragon/app/ui/theme/`**. A screen asks for a name; it
never states a number.

| File | What lives there | XML equivalent |
| --- | --- | --- |
| `Color.kt` | The raw brand colors | `colors.xml` |
| `Type.kt` | The type scale and the named text roles | `styles.xml` |
| `Shape.kt` | The control corner and outline thickness | shape drawables |
| `Theme.kt` | Ties them together and hands them to every screen | `themes.xml` |

- **Never write a font size (`18.sp`) or a color literal (`Color(0xFFC62828)`)
  in a screen file.** If a screen needs a size or a color that doesn't exist
  yet, add a name for it in the theme and use that name.
- **Ask for a Material slot** — `MaterialTheme.typography.bodyLarge`,
  `MaterialTheme.colorScheme.error` — or one of the app's own named styles,
  `AppTheme.textStyles.<name>`.
- **Never `.copy(fontSize = …)` a Material style at a call site.** That is a
  hard-coded size wearing a disguise, and a theme can't reach it.

Because of this, adding a theme later is a change to `Theme.kt`, `Type.kt`,
`Shape.kt`, and `Color.kt` only — no screen has to be touched.

### The named text roles (`AppTheme.textStyles`)

| Name | Used for |
| --- | --- |
| `sectionHeader` | A heading over a group of settings |
| `settingTitle` | The main line of a row — the thing being set or chosen |
| `settingDescription` | Hint text under a heading or a label |
| `controlLabel` | Text inside a framed control: a button caption or a drop-down's current value |
| `dialogOptionTitle` | The main line of a tappable option in a dialog |
| `dialogOptionSubtitle` | The one-line explanation under a dialog option |

### Sizes that are already decided

- **A screen title** (the text in the top bar) is Material's `titleLarge`.
- **A section heading inside a screen** is `sectionHeader` — one step below the
  screen title, so it reads as a heading but never competes with the title.

---

## 4. Buttons

- **`AppButton` is the only button.** It draws a thin outline in the theme's
  outline color, with the theme's control corner — the same frame a drop-down
  box uses.
- **Never a pill, never a filled capsule, never a raised/elevated button.**
- **A button never resizes based on state.** A caption that changes with state
  (e.g. "Restore" / "Nothing to Restore") still lives in a button whose frame
  stays put.
- **A control the user may have already seen is never hidden when it becomes
  unavailable.** Keep it visible and disable (gray out) it instead, so it does
  not silently disappear and leave the user confused. A disabled button says why
  in Title Case in place of its normal caption (e.g. "Nothing to Restore").

---

## 5. Drop-downs

- **A drop-down always sits on the same line as its label** — the label on the
  left, the current value (with its chooser) on the right. Never stack the
  drop-down above or below its label.
- **A drop-down's width never changes.** It is sized to the widest label it
  could ever show, plus a small, fixed slack, so choosing a different option
  never makes it grow, shrink, or shove its neighbors around. Nothing on the
  line is allowed to jump.
- **The collapsed box may show a shorter label than the menu**, when the full
  name is too long for the line. The menu always shows the full name.
- **The drop-down box and `AppButton` are framed identically.**

Both behaviors come free from `AppDropdown` / `AppDropdownRow` in
`ui/components/AppControls.kt`. Do not hand-roll a drop-down.

---

## 6. Hints and subtext

- **A hint belongs directly under the label it explains, inside that label's
  own column.** It never spans the full width under a whole row, and it never
  sits under the control.
- **Order is always: label → hint → control.** A hint never comes after the
  thing it describes.
- A hint is a normal sentence, in `settingDescription`, colored
  `onSurfaceVariant`.

---

## 7. Choosing from a list of options

Used by the Export List dialog; the pattern for any "pick one of these" list.

- **A Material 3 `Surface`, one option per full-width row** — not a card, not a
  button, not a pill.
- **Soft, not blocky:** the theme's control corner, filled with
  `MaterialTheme.colorScheme.surfaceContainerLow` so it blends into the dialog.
  No borders, no outlines, no dividers between rows.
- **It responds to touch.** `Surface(onClick = …)` keeps the default Material
  ripple, highlight, and focus behavior.
- **Two lines per option:** a title (`dialogOptionTitle`) and a one-line
  subtitle saying what the option is (`dialogOptionSubtitle`, in
  `onSurfaceVariant`).
- **The subtitles are the explanation.** No paragraph of disclaimer text under
  the list.
- **Light spacing between rows** (4.dp) and light padding inside them
  (16.dp × 10.dp) — never hard dividers or thick outlines.

---

## 8. Dialogs

- **The question is the dialog's title**, phrased as a normal sentence:
  "Delete list?".
- **Button order is part of the wording and is not rearranged.** Where the
  wording says "Okay Cancel", Okay is on the left and Cancel on the right. (In
  Compose, Material draws the dismiss slot before the confirm slot, so the
  left-hand button goes in `dismissButton` regardless of what it does.)
- **Destructive confirmations use `MaterialTheme.colorScheme.error`** for the
  destructive button's text — never a red literal.

---

## 9. Screens

- **A screen longer than the display scrolls.** Any screen whose content can
  exceed the screen height gets a vertical scroll, so the last section is always
  reachable.
- **Sections are separated by a divider**, with the section heading first.

---

## 10. Dates and times

- **Human-readable timestamps read `Mon D, YYYY at H:MM AM/PM`** — for example,
  `Jul 24, 2026 at 2:05 PM`. The word "at" separates the date and the time;
  there is no comma before the time. (Stored/machine timestamps stay ISO-8601
  per the Formatting Specification; this rule is only for text shown to the
  user.)
