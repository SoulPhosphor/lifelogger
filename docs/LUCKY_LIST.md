# Lucky List

Owner-approved bare-bones mode. Starts unchecked in Choose Your Data Modes.
When enabled, Lucky List appears first in both the icons and mode dropdown.
Settings label: **Lucky List**. Description: **Randomly select a lucky item or option.**

## Editing and selection

Home shows name-only Lists-style rows. The top-right plus creates a draft with
focus in the first item. Titles, autosave, crash-recovery drafts, editing,
deleting, and drag/accessibility reordering reuse ordinary Lists behavior.
Lucky items have no completion or indentation state. Add-item sits immediately
after the last row. Select Lucky Item is centered at the bottom; without any
nonblank items it is disabled and reads No Items to Select.

The gear before the title opens Lucky List Settings. Its only toggle is
**When spinning again exclude previously selected items.**, off by default and
stored independently on each list.

Selection is uniformly random among nonblank eligible rows. Equal-text rows
are separate options. With exclusion on, each row is selected at most once in
one winner-dialog session. Okay, outside dismissal, and dialog Back close that
session; reopening starts fresh. Session history is never persistent data.

Winner title: **We have a winner!**. Buttons: **Spin Again**, **Okay**.
Once the session is exhausted, show **All items have been selected.** /
**Start fresh so all options are available again?** with **Cancel**, **Start Fresh**.
Cancel returns to the prior winner; Start Fresh resets and selects again.

Saved-list ellipsis menu follows Lists: Export, then Delete List. Formats are
TXT, Markdown, PDF and lossless JSON; human-readable exports have no checkboxes.

## Persistence and backup

Room version 22 adds lucky_lists and lucky_list_items without deleting any
existing data. Lists have unique immutable UUIDs. Children are restored as a
whole grouping, have no independent merge identity, and use remapped local IDs.
Per-list exclusion, titles, item text/order, creation time, and draft state are
included in the lucky_lists category. Mode visibility is a portable preference.

Backup format 5 writes the new category and counts explicitly. Versions 1–4
remain readable; absent Lucky List data cannot erase current lists. Null new
fields are omitted when validating old checksums. Replace, whole-group Merge,
conflict policies, selected-category Undo, individual restore, verified manual
backup, automatic snapshot capture and database revision triggers cover it.

The existing Phase 5 automatic-backup work and its two fixes are included here.
Its operational backup_state table is added after the main branch's v20
statistics migration, avoiding the competing version-20 schema definitions.
Device-local folder access, enabled flag, worker state and history are excluded
from portable backup. Daily/weekly/custom cadence and retention remain portable.

## Icon provenance

Cyclone is bundled in res/drawable/ic_cyclone.xml, converted from Google's
Material Symbols outlined 24px asset. Its path is unchanged, with the original
negative-Y viewport translated into an Android vector viewport. Compose applies
the active theme tint; there are no runtime requests or hotlinks.
Source: https://github.com/google/material-design-icons/blob/master/symbols/web/cyclone/materialsymbolsoutlined/cyclone_24px.svg
License: Apache-2.0 (https://github.com/google/material-design-icons/blob/master/LICENSE)
