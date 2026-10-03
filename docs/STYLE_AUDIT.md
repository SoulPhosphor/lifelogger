# Appearance and accessibility audit

Reviewed October 3, 2026, against main commit `37b3fa1`.

## Result and scope

Reviewed all Compose UI source files, theme files, Android appearance resources,
and PDF rendering styles. Centralized scattered styling with the same existing
values. The original theme refactor preserved screen wording, navigation, data, defaults
and visible layouts. The approved follow-up makes pop-up action buttons outlined,
with identical Primary/Destructive roles, and adds accessibility metadata/actions.
Screen button appearances remain unchanged. App colors retain dynamic light/dark behavior.

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
| Buttons | Ordinary actions use outlined `AppButton`; top-bar actions, help links, use stock `TextButton`. These screen treatments retain their existing frame/shape and default text treatment. Pop-up actions now use the approved outlined Primary/Destructive styles. | `AppControls.kt`, `AppDialog.kt`; `CreateLogScreen.kt`, `NewEntryScreen.kt`, `NewIdeaScreen.kt`, `ChecklistScreen.kt`, `CalendarConfigScreen.kt` |
| Filters / mode indicators | Statistics uses filled Material `FilterChip` with the shared control corner (so it is already not a pill). Home uses a circular selected-mode indicator, which differs from the guide's no-fully-rounded rule. | `ClickerStatisticsScreen.kt`, `HomeScreen.kt` (`ViewToggle`) |
| Corners | Framed controls are 10dp; validation frames are 8dp; calendar cells/color previews are 6dp; legend swatches are 3dp. Stock cards/fields/dialogs retain Material shapes. Each custom role now has its own token. | `Shape.kt`, `EntryFieldControls.kt`, `NewIdeaScreen.kt`, calendar screens |
| Dropdowns | Shared `AppDropdown` has an outlined frame sized to its widest label. Calendar dropdowns use full-width read-only Material fields. Daily Task preference choices are text/arrow rows without the shared frame and can change width with the selected caption. | `AppControls.kt`, `CalendarConfigScreen.kt`, `DailyListPreferencesScreen.kt` |
| Field labels | Entry-facing fields generally use external labels. Older form/idea editors and the color picker still use Material floating labels; some editors include placeholder examples. | `CreateLogScreen.kt`, `EditFormScreen.kt`, `IdeaLogEditor.kt`, `ColorPickerDialog.kt` |
| Dialog layout | `AppDialog` centers titles/actions. Several specialized dialogs use stock `AlertDialog`; picker/export patterns already have documented exceptions. The save-color-preset dialog also uses the stock pattern. | `AppDialog.kt`, `ExportFormatDialog.kt`, `ColorPickerDialog.kt`, `CalendarConfigScreen.kt` |
| Widths / spacing | Numeric widths vary (72/88/96/120dp). Editor sub-items use 32dp indent, read-only Daily Task children 20dp. Screen gaps vary (4/8/12/16dp). These now have named tokens and retain their current values. | `Sizes.kt`, `Spacing.kt`, numeric editors, `ListEditor.kt`, `DailyListMainScreen.kt` |

## Accessibility findings

The owner approved an accessibility pass and outlined pop-up actions. Source fixes
below preserve screen appearance. A source fix is not a device accessibility
certification; verification still needs a real Android device.

| Finding | Status / implementation | Remaining checks or decision |
| --- | --- | --- |
| Invisible dropdown captions | Fixed: measurement text clears semantics; width measurement is unchanged. | Automated test checks only the chosen caption exists until the menu opens. |
| External field labels | Fixed: shared labeled controls associate their existing label with editable fields; explicit labels cover Notes, Follow-Up Note, calendar fields, retention days and statistics days. Floating labels retain their Material association. | Test covers empty field naming and editing. Check TalkBack announcement order on-device. |
| Calendar descriptions | Fixed: full date plus existing result text on each day, named summary/log actions, named color/range swatches. | Check reading order and results with real stored calendars and TalkBack. |
| Arbitrary calendar colors / brand contrast | Open: no visible screen color changes were made. User-picked colors can have unsuitable contrast with inherited day-number text. Brand scheme foreground/container pairs need palette review. | Choosing automatic contrasting text or adjusting palettes changes screen appearance and needs an owner decision. |
| Custom action roles / target size | Fixed button roles on AppButton/AppDropdown; pop-up buttons have 48dp minimum layout targets. | Screen control sizes remain unchanged. Actual expanded hit bounds, adjacent overlap and small copy/open/color controls need device verification before approving screen layout changes. |
| Selected state / duplicate controls | Fixed Home mode selection and whole-row switch/checkbox/radio semantics in settings, preferences, form/idea editors and clicker settings/statistics. Child indicators delegate actions; their former interactive layout sizes are retained. | Test checks one named switch action and one state update. Verify focus order with TalkBack/Switch Access. |
| Drag-only reorder | Fixed: Move Up/Move Down actions and position on list and field handles; callbacks reuse existing reorder paths and existing identities. Edit Form already had visible up/down controls; these remain. | Test covers first-item actions, identity preservation and rejected invalid moves. Verify pointer drag and accessible moves on-device. |
| Headings / validation | Fixed Settings section/subsection and calendar month heading semantics; required/invalid entry frames and invalid web-address fields expose errors. | Test covers specific field errors. Verify error discovery/announcement when submission fails. |
| Large fonts / constrained dialogs | Fixed AppDialog scrolling and action wrapping; export/color-picker body scrolling; outlined pop-up captions can wrap. | Check 200% fonts, narrow/landscape/keyboard-open dialogs. Fixed numeric widths, Home titles and seven-column calendar layout remain unchanged pending device findings/owner decisions. |
| Untagged PDF exports | Open: current Android canvas exporter has no tagged structure/reading-order implementation. | Accessible tagged PDF generation needs a separate exporter change; scope decision remains pending. |

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
The source fixes above have targeted automated coverage. Contrast, screen hit
bounds/large-font layout and tagged PDF export remain open. Device checks are
not available in this execution environment and have not been represented as passed.
