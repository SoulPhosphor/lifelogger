# Appearance and accessibility audit

Reviewed October 3, 2026, against main commit `37b3fa1`.

## Result and scope

Reviewed all Compose UI source files, theme files, Android appearance resources,
and PDF rendering styles. Centralized scattered styling with the same existing
values. No screen wording, navigation, data, defaults, or visible layouts were
intentionally changed. App colors retain dynamic light/dark behavior.

This is a source-code audit. Android device screenshots, TalkBack behavior,
keyboard/switch navigation, actual touch bounds, and large-font rendering still
require device verification. Compilation and automated test results are recorded
in the pull request. Source value checks do not establish pixel-perfect rendering.

## Styling differences for owner decisions

These differences were preserved, not normalized. The rules in STYLE.md remain
binding; this table documents older implementation differences rather than
approving new exceptions.

| Area | Existing difference | Relevant files / components |
| --- | --- | --- |
| Buttons | Ordinary actions use outlined `AppButton`; top-bar actions, help links, and some dialog actions use stock `TextButton`. The latter has a different frame/shape and default text treatment. | `AppControls.kt`, `AppDialog.kt`; `CreateLogScreen.kt`, `NewEntryScreen.kt`, `NewIdeaScreen.kt`, `ChecklistScreen.kt`, `CalendarConfigScreen.kt` |
| Filters / mode indicators | Statistics uses filled Material `FilterChip` with the shared control corner (so it is already not a pill). Home uses a circular selected-mode indicator, which differs from the guide's no-fully-rounded rule. | `ClickerStatisticsScreen.kt`, `HomeScreen.kt` (`ViewToggle`) |
| Corners | Framed controls are 10dp; validation frames are 8dp; calendar cells/color previews are 6dp; legend swatches are 3dp. Stock cards/fields/dialogs retain Material shapes. Each custom role now has its own token. | `Shape.kt`, `EntryFieldControls.kt`, `NewIdeaScreen.kt`, calendar screens |
| Dropdowns | Shared `AppDropdown` has an outlined frame sized to its widest label. Calendar dropdowns use full-width read-only Material fields. Daily Task preference choices are text/arrow rows without the shared frame and can change width with the selected caption. | `AppControls.kt`, `CalendarConfigScreen.kt`, `DailyListPreferencesScreen.kt` |
| Field labels | Entry-facing fields generally use external labels. Older form/idea editors and the color picker still use Material floating labels; some editors include placeholder examples. | `CreateLogScreen.kt`, `EditFormScreen.kt`, `IdeaLogEditor.kt`, `ColorPickerDialog.kt` |
| Dialog layout | `AppDialog` centers titles/actions. Several specialized dialogs use stock `AlertDialog`; picker/export patterns already have documented exceptions. The save-color-preset dialog also uses the stock pattern. | `AppDialog.kt`, `ExportFormatDialog.kt`, `ColorPickerDialog.kt`, `CalendarConfigScreen.kt` |
| Widths / spacing | Numeric widths vary (72/88/96/120dp). Editor sub-items use 32dp indent, read-only Daily Task children 20dp. Screen gaps vary (4/8/12/16dp). These now have named tokens and retain their current values. | `Sizes.kt`, `Spacing.kt`, numeric editors, `ListEditor.kt`, `DailyListMainScreen.kt` |

## Accessibility findings

No accessibility behavior or appearance was changed in this preservation task.
The recommendations below are for a separately approved accessibility pass.

| Priority | Finding and source evidence | Proposed follow-up |
| --- | --- | --- |
| High | `AppDropdown` lays out every possible caption as alpha-zero `Text` to stabilize width. Those invisible captions do not clear their semantics, so they can appear in the accessibility tree alongside the chosen caption. | Hide measuring-only text from accessibility; retain the exact width calculation. Verify TalkBack reads only the selected value. |
| High | Many outlined fields put a separate `Text` label above the field without a semantic association. Empty fields can expose an edit control without its visible label. This includes entry controls and calendar range minimum/maximum fields. | Attach the visible label to field semantics without moving it into the outline. Verify each blank field is announced by name. |
| High | Calendar `DayCell` exposes the day number and click/long-click actions, but no full-date/result description or named long-click action. Heat-map/yes-no result meaning is primarily color until a popover is opened. `ColorRangeRow` uses a clickable color swatch without an accessible name/value. | Announce full date, result and available actions. Name color swatches with their color/range. Keep visual design unchanged. |
| High | Calendar day numbers use inherited text color on arbitrary user-picked backgrounds. The non-dynamic brand schemes override only primary/secondary/error, leaving Material's paired foreground/container defaults. Neither path validates foreground/background contrast. | Measure text contrast in light/dark/dynamic schemes and user palettes; decide how to handle unsuitable user colors and paired brand roles. |
| Medium | Custom `AppButton`/`AppDropdown` use `clickable` without an explicit button role or minimum layout target. Copy/open icons use 32dp sized `IconButton`s; the calendar color swatch is 32dp. Foundation/Material may expand actual touch bounds, but adjacent bounds can overlap. | Verify real hit bounds and spacing on-device, then provide at least 48dp targets where needed without enlarging glyphs. |
| Medium | `ViewToggle` names its icon and supplies `Role.Button`, but does not expose selected state. Several settings toggles/radio options make both parent rows and child controls clickable, without unified row toggle/select semantics or radio-group semantics. | Expose selection/state and one labeled logical target per option. Verify reading order and duplicate focus stops. |
| Medium | List reorder handles expose a "Reorder" label and pointer drag behavior, but no move-up/move-down accessibility actions. Comparable field editors also use drag handles. | Add accessible reorder actions and position announcements; preserve pointer drag behavior. |
| Medium | Section headers are styled text without heading semantics. Inline validation frames/required markers do not consistently expose error text to accessibility; color alone can signal the validation state. | Mark headings and expose validation errors/state for assistive technology. |
| Medium | Fixed-width numeric inputs, nonwrapping dropdowns, one-line Home titles, seven-column calendar grids, and centered dialog button rows need large-font/narrow-screen checks. `AppDialog` has no internal scroll container when title/body/actions exceed available height. | Test 200% font size, small screen widths, landscape and keyboard-open dialogs; approve adaptations for any clipping or unreachable controls. |
| Medium | PDFs use Android canvas text without document tagging/reading-order structure. They wrap and paginate visually but are not demonstrated to be accessible tagged PDFs. | Assess exported PDFs with a screen reader; approve accessible PDF generation if required. |

Decorative arrows and icons beside already-labeled controls appropriately have
null content descriptions; those were not treated as missing labels. Most named
icon actions already have descriptive text. Standard Material typography uses
scalable `sp`, and most long screens already scroll.

## Verification boundaries

The source guard scans every non-theme UI file for raw application sizes,
colors, shapes, explicit font families/weights and disabled opacity literals.
The value-preservation review compares replaced dimensions with their original
values. Material defaults, user data colors, XML vector geometry, and zero-size
measurement state remain deliberate exceptions, not unresolved theme literals.

A successful CI build verifies compilation and the existing automated suite.
The accessibility findings above remain open until device checks and any owner
approved changes are completed.
