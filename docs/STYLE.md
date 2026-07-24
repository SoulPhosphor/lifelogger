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

## Controls

- **A dropdown always sits on the same line as its label** — the label on the
  left, the current value (with its chooser) on the right. Never stack the
  dropdown above or below its label.
- **A control the user may have already seen is never hidden when it becomes
  unavailable.** Keep it visible and disable (gray out) it instead, so it does
  not silently disappear and leave the user confused. A disabled button says why
  in Title Case in place of its normal caption (e.g. "Nothing to Restore").

## Dates and times

- **Human-readable timestamps read `Mon D, YYYY at H:MM AM/PM`** — for example,
  `Jul 24, 2026 at 2:05 PM`. The word "at" separates the date and the time; there
  is no comma before the time. (Stored/machine timestamps stay ISO-8601 per the
  Formatting Specification; this rule is only for text shown to the user.)
