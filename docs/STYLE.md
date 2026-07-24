# Style

This document locks the app's visible **style** — how controls, text, and
values look and read. Like the UI Specification, every rule here is a
**decision, not a suggestion**. It complements the "Text house style" in
`CLAUDE.md`. It is a living document: add a rule here the moment one is decided,
rather than letting the same choice get re-made differently on each screen.

## Text

- **Labels and button captions use Title Case.** Capitalize every major word;
  minor words (a, an, the, and, or, of, to, for, in, on, at, by) stay lowercase
  unless they are the first word. Applies to every on-screen label, button, and
  disabled-state caption.
- **Body text, descriptions, and confirmation questions are normal sentences**,
  not Title Case.

## Type and color come from the theme — never from a screen

Every text size and every color is a **named value defined in
`app/src/main/java/com/datadragon/app/ui/theme/`**. A screen asks for a name; it
never states a number.

- **Never write a font size (`18.sp`) or a color literal (`Color(0xFFC62828)`)
  in a screen file.** If a screen needs a size or a color that doesn't exist yet,
  add a name for it in the theme and use that name.
- **Ask for a Material slot** — `MaterialTheme.typography.bodyLarge`,
  `MaterialTheme.colorScheme.error` — or one of the app's own named styles,
  `AppTheme.textStyles.<name>` (defined in `Type.kt`).
- **The app's own named styles are for roles Material doesn't name.** Today:
  `sectionHeader`, `settingTitle`, `settingDescription`, `dialogOptionTitle`,
  `dialogOptionSubtitle`. Add a role here rather than one-off `.copy(...)` calls
  at the point of use.
- **Never `.copy(fontSize = …)` a Material style at a call site.** That is a
  hard-coded size wearing a disguise, and a theme can't reach it.

Because of this, adding a theme later is a change to `Theme.kt` and `Type.kt`
only — no screen has to be touched.

### Sizes that are already decided

- **A screen title** (the text in the top bar) is Material's `titleLarge`.
- **A section heading inside a screen** is `AppTheme.textStyles.sectionHeader` —
  one step below the screen title, so it reads as a heading but never competes
  with the title.

## Controls

- **A dropdown always sits on the same line as its label** — the label on the
  left, the current value (with its chooser) on the right. Never stack the
  dropdown above or below its label.
- **No pill / oval buttons for lists of choices.** A set of options is never a
  row of outlined pills. Options are full-width rows (see below).
- **A screen longer than the display scrolls.** Any screen whose content can
  exceed the screen height gets a vertical scroll, so the last section is always
  reachable.

## Choosing from a list of options

Used by the Export List dialog; the pattern for any "pick one of these" list.

- **A Material 3 `Surface`, one option per full-width row** — not a card, not a
  button, not a pill.
- **Soft, not blocky:** `RoundedCornerShape(12.dp)`, filled with
  `MaterialTheme.colorScheme.surfaceContainerLow` so it blends into the dialog.
  No borders, no outlines, no dividers between rows.
- **It responds to touch.** `Surface(onClick = …)` keeps the default Material
  ripple, highlight, and focus behavior.
- **Two lines per option:** a title (`dialogOptionTitle`) and a one-line subtitle
  saying what the option is (`dialogOptionSubtitle`, in `onSurfaceVariant`).
- **The subtitles are the explanation.** No paragraph of disclaimer text under
  the list.
- **Light spacing between rows** (4.dp) and light padding inside them
  (16.dp × 10.dp) — never hard dividers or thick outlines.

## Dialogs

- **The question is the dialog's title**, phrased as a normal sentence:
  "Delete list?".
- **Button order is part of the wording and is not rearranged.** Where the
  wording says "Okay Cancel", Okay is on the left and Cancel on the right. (In
  Compose, Material draws the dismiss slot before the confirm slot, so the
  left-hand button goes in `dismissButton` regardless of what it does.)
- **Destructive confirmations use `MaterialTheme.colorScheme.error`** for the
  destructive button's text — never a red literal.
- **A control the user may have already seen is never hidden when it becomes
  unavailable.** Keep it visible and disable (gray out) it instead, so it does
  not silently disappear and leave the user confused. A disabled button says why
  in Title Case in place of its normal caption (e.g. "Nothing to Restore").

## Dates and times

- **Human-readable timestamps read `Mon D, YYYY at H:MM AM/PM`** — for example,
  `Jul 24, 2026 at 2:05 PM`. The word "at" separates the date and the time; there
  is no comma before the time. (Stored/machine timestamps stay ISO-8601 per the
  Formatting Specification; this rule is only for text shown to the user.)
