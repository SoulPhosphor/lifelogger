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
