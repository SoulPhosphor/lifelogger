# UI Elements Cheat Sheet

A plain-language map of the app's user-interface pieces: what each part is
called, and — for anyone who thinks in CSS — the Jetpack Compose word for the
same idea. When the owner says "make a dialog box" or "change the button
color", this file says which piece they mean and where it lives.

This is a **map, not the law.** The binding visual rules live in
[`STYLE.md`](STYLE.md); the screen-by-screen behavior lives in
[`UI_SPEC.md`](UI_SPEC.md). When they disagree with this file, they win — fix
this file to match.

---

## CSS → Jetpack Compose translation

| You think (CSS) | The app calls it (Compose) | Where it lives |
| --- | --- | --- |
| A reusable class / component | a **composable function** (`@Composable fun …`) | `ui/components/`, `ui/screens/` |
| A `<div>` / flex container | `Column` (stacks down), `Row` (across), `Box` (layered) | inline in each screen |
| `color:` / CSS custom property | a **color token** in the theme's color scheme | `ui/theme/Color.kt`, `Theme.kt` |
| `font-size` / `font` | a **named text style** | `ui/theme/Type.kt` (`AppTheme.textStyles.*`) |
| `padding` / `margin` / `gap` | a **named spacing value** or `Modifier.padding(…)` | `ui/theme/Spacing.kt` (`AppTheme.spacing.*`) |
| `border-radius` | a **shape** | `ui/theme/Shape.kt` (`AppTheme.shapes.*`) |
| A `<button>` on a page | `AppButton` | `ui/components/AppControls.kt` |
| A `<select>` drop-down | `AppDropdownRow` | `ui/components/AppControls.kt` |
| A modal / dialog | an `AlertDialog` (see the Dialog anatomy below) | one per screen today |

**The one rule that makes theming possible:** a screen never writes a raw size
or color (no `18.sp`, no `Color(0xFF…)`). It asks the theme for a *name*. That
is what lets the whole app be recolored by editing the theme files instead of
every screen. (This is `STYLE.md` rule #4.)

---

## Where the "theme variables" are

To recolor or restyle the app later, these are the files to edit — nothing on a
screen needs touching:

- **Colors** — `ui/theme/Color.kt` (the raw swatches) and `ui/theme/Theme.kt`
  (which swatch fills each role: primary, surface, error, and so on).
- **Text sizes / weights** — `ui/theme/Type.kt`.
- **Spacing** — `ui/theme/Spacing.kt`.
- **Corner shapes** — `ui/theme/Shape.kt`.

---

## Dialog anatomy

A **dialog box** (a small window that floats over the screen and asks one
thing) has these parts:

| Part | What it is | Alignment |
| --- | --- | --- |
| **Title** | the question, as a sentence — "Delete list?" | **centered** |
| **Body / subtext** | the one line explaining what happens | **left**, not centered |
| **Buttons** | the choices, as a centered row at the bottom | the **row is centered**; within it, Cancel on the **left**, the action on the **right** |

Button roles — each is its own component so they can be themed separately
later, even though **right now they all look the same (one shared color, no
red):**

- **Action / primary** — the button that does the thing ("Okay", "Unlock").
- **Destructive** — the button that deletes or discards ("Delete Log",
  "Discard"). Its own component, so a future theme can make it stand out; today
  it is the same color as the others.
- **Cancel / dismiss** — backs out. Always present unless the dialog is a
  one-button notice.

**Not every floating box is one of these simple dialogs.** These keep their own
existing designs and are *not* changed by the simple-dialog rules above:

- **Pickers** — date, time, and color pickers.
- **Export dialogs** — see `STYLE.md` §7; the owner likes these as they are.
- **Option-list dialogs** — the "pick one of these" list pattern, `STYLE.md` §8.

---

## Vocabulary

**This is the shared vocabulary. Use these words the way they are defined here,
in every session.** They have caused confusion across sessions, so they are
pinned down here once and for all. When the owner uses one of these words, this
is what it means — do not substitute your own term, and do not guess which one
is meant.

### The two structural words

- **Grouping** — one whole form / list / Idea Log / Clicker Data Log, **and
  everything inside it**. It is the container. On the Home screen it shows as a
  single tappable row (a title, a summary line, and for some modes a "+"), and
  tapping it opens that grouping. On the Forms Home, "Odor Log" is a grouping.
  The owner also says **"the grouping"**, **"grouping form"**, and **"grouping
  of logs"** for this same thing.
  - Code: `LogTemplate` (Forms), `Checklist` (Lists), `IdeaLog` (Ideas),
    `ClickerLog` (Clicker). The Home rows that draw a grouping are `LogRow`,
    `ChecklistRow`, `IdeaLogRow`, `ClickerLogRow` in `HomeScreen.kt`.
- **Card** — one entry / record **inside** a grouping, seen only after the
  grouping is opened. One submitted odor entry is a card; one clicker card is a
  card. A grouping holds a stack of cards.
  - Code: `LogEntry` (Forms), `IdeaEntry` (Ideas), `ClickerCard` (Clicker).

A **grouping** contains **cards**. Never use "card" for a grouping or "grouping"
for a card.

### The two screens

- **Home screen** — the screen that lists **all the groupings** for the current
  mode, one row per grouping. The top bar's mode icons switch which mode's
  groupings are listed.
- **Main screen** — the screen for **one grouping**, opened from its Home row,
  showing that grouping's **cards**. The owner also calls this "the form main
  screen" / "the main screen for the log itself".

### The parts of a grouping's Home row

- **Grouping title** — the grouping's name, the top line of its Home row (e.g.
  "Odor Log").
- **Summary line** — the second line under the title: the card count and the
  most recent activity, e.g. `110 Entries · Last Entry Yesterday` on Forms and
  Ideas today. Count wording is "No Entries Yet" / "1 Entry" / "N Entries". The
  Clicker grouping's summary line — `N Entries · Last Saved Today`, with "Last
  Saved" instead of "Last Entry" — is being added; it does not ship yet.

### The two "+" buttons are different things

This is the single biggest source of confusion — they are never the same button.

- **New (top-bar "+")** — the "+" at the top-right of the **Home screen's** top
  bar. It creates a **new grouping** in the current mode (a new form, list, Idea
  Log, or Clicker Data Log). It is present in every mode that creates groupings,
  Forms and Clicker alike.
- **Add (grouping-row "+")** — the "+" on the right edge of a single
  **grouping's Home row**. It adds a **card** to that grouping without opening
  it. Present on Forms ("Add entry") and Ideas ("Add idea") groupings today; not
  on Clicker or Lists groupings.
- **Add card (Main-screen "+")** — the "+" in the top bar of a **Main screen**
  (a grouping opened). It adds a **card** to the grouping you are looking at.
  **Every mode's Main screen has this**, Clicker included — opening a Clicker
  grouping shows a "+" at the top that adds a new clicker card.

Do **not** confuse the Main-screen "+" (adds a whole card) with a **tracker
button** (below), which only changes one number on a card already there.

### Tracker buttons (Clicker cards)

- **Tracker button** — a button on a Clicker card's face, one per Click Tracker /
  stepping Write-In field, that changes **that one tracker's number** on **that
  one card** (add or subtract its step). Its caption defaults to "Add" but the
  owner can rename it per field. In the "Problems" grouping, the "Add" buttons
  beside "Moved Rooms" and "Wore Hearing Protection" are tracker buttons — they
  are **not** the Add-card "+", and they do not create a card.

### Editing and other terms

- **Editing a card** — most cards are not edited directly on the card face. You
  open the **vertical ellipsis (three dots) in the card's upper-right corner**
  and choose to edit. (Clicker cards are the exception: most values are meant to
  be changed right on the card face for speed; a few field types can only be
  changed from that edit menu.)
- **Clicker Data Log** — one clicker grouping: a titled series of cards, each
  carrying the same set of trackers and fields.
