# Dependency Maintenance

How Data Dragon's libraries and build tools are kept current without risking
installed data. Written for the owner and for any future AI session. Follow it
as written; do not rebuild the strategy from scratch.

## Where versions live

| What | File |
|---|---|
| Every library and Gradle plugin version | `gradle/libs.versions.toml` |
| Gradle itself (the Wrapper) | `gradle/wrapper/gradle-wrapper.properties` |
| Android SDK levels (`compileSdk`, `targetSdk`, `minSdk`) and app version | `app/build.gradle.kts` |
| GitHub Actions versions and the Java version CI uses | `.github/workflows/build.yml` |
| What Dependabot watches | `.github/dependabot.yml` |

Rules:

- Every version is pinned to an exact, tested, stable release. Never use `+`,
  `latest.release`, ranges, alpha, beta or release-candidate versions.
- Compose libraries take their versions from the Compose BOM (`composeBom`);
  do not give them individual versions.
- Add a new library to the version catalog first, then reference it from
  `app/build.gradle.kts`.

## Current baseline (October 2026)

| Component | Version | Notes |
|---|---|---|
| Gradle Wrapper | 9.7.1 | Checksum pinned in `gradle-wrapper.properties`. |
| Android Gradle Plugin (AGP) | 9.3.3 | Built-in Kotlin. See "Coordinated updates". |
| Kotlin (compiler, Compose compiler, serialization plugin) | 2.4.21 | Pinned through `kotlin-gradle-plugin` on the build classpath. |
| KSP | 2.3.12 | Runs Room's code generation. |
| Compose BOM | 2026.09.00 | Compose 1.12.1, Material 3 1.4.0. |
| Room | 2.8.5 | Schema exported to `app/schemas`. |
| kotlinx-serialization | 1.11.0 | Writes and reads backups. |
| colorpicker-compose (Skydoves) | 1.3.0 | Calendar color picker. |
| Reorderable | 3.1.0 | Drag-to-reorder lists. |
| AndroidX Core / Activity / Lifecycle / Navigation / WorkManager | 1.19.1 / 1.13.0 / 2.11.0 / 2.10.2 / 2.12.0 | |
| Robolectric / JUnit / coroutines-test / androidx.test core | 4.17 / 4.13.2 / 1.11.0 / 1.7.0 | Unit tests run on the JVM. |
| compileSdk / targetSdk / minSdk | 37 / 35 / 26 | See "Android SDK levels". |
| Java | 17 bytecode, built on JDK 21 in CI | |

## How updates are detected

Dependabot (`.github/dependabot.yml`) checks **once a month** and opens pull
requests (PRs). It watches:

- Gradle: every library and plugin in the version catalog, and the Gradle
  Wrapper.
- GitHub Actions used by the workflows.

Routine minor and patch updates are grouped so related libraries arrive in one
PR:

| Group | Contains | Why grouped |
|---|---|---|
| `kotlin-toolchain` | Kotlin, Compose compiler, serialization plugin, KSP | Must always match each other. |
| `android-build-toolchain` | AGP and the Gradle Wrapper | AGP sets the minimum Gradle version. |
| `persistence-and-backup-format` | Room, SQLite, kotlinx-serialization | **Data-safety PR.** Decides how saved data and backups are written and read. |
| `compose` | Compose BOM and Compose libraries | Visual changes come from here. |
| `androidx` | Other AndroidX libraries | Routine. |
| `other-libraries` | Everything else | Routine. |
| `github-actions` | Workflow actions | Routine. |

A **major** version (for example AGP 10, Kotlin 3, Room 3) is never grouped:
it always arrives as its own PR so it can be reviewed on its own.

At most 5 Gradle PRs and 3 GitHub Actions PRs are open at once. Dependabot
waits until some are merged or closed before opening more.

**Nothing merges automatically.** Dependabot PRs build in CI but never publish
an APK. A release is published only when a PR is merged into `main`, and only
the owner merges.

## Security updates

Security updates are separate from the monthly schedule. When GitHub reports a
vulnerable dependency, Dependabot opens a PR right away (if Dependabot security
updates are turned on in the repository settings; see "Repository settings").

Handle a security PR first, with the same checks as any other PR:

1. Read the advisory linked in the PR. Decide whether it actually affects Data
   Dragon (for example, a flaw in a network feature does not affect an app
   with no network access).
2. If it does, review and merge it promptly once CI is green.
3. If the fix needs a major upgrade, treat it as a major update (below), but
   start it immediately instead of waiting for a monthly batch.

## Coordinated updates

These components have to agree with each other. Never update one alone and
hope it works.

- **Kotlin ↔ AGP.** Each Kotlin release lists the newest AGP it supports in
  the [Kotlin Gradle plugin compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html).
  Kotlin 2.4.21 supports AGP up to 9.3, which is why AGP 9.4 is not used yet.
  Take a newer AGP minor version only when the Kotlin in use supports it.
- **AGP ↔ Gradle.** Each AGP release has a minimum Gradle version
  ([AGP compatibility table](https://developer.android.com/build/releases/about-agp)).
  Kotlin also lists the newest Gradle it supports.
- **Kotlin ↔ KSP ↔ Room.** KSP runs Room's code generator. After updating any
  of the three, the Room schema check in CI must still pass.
- **Kotlin ↔ libraries.** A library built with a newer Kotlin than the
  project's compiler fails to compile ("incompatible metadata version"). Vico
  3.3.1 and colorpicker-compose 1.3.0 need Kotlin 2.4.
- **Compose ↔ compileSdk ↔ AGP.** Current Compose and Lifecycle require
  `compileSdk 37`, which requires AGP 9.2 or newer.
- **Compose BOM ↔ Vico ↔ Material 3.** Vico requires a minimum Compose and
  Material 3 version (see "Vico").

## Android SDK levels

- `compileSdk` is which Android APIs the code may use. Raising it does not
  change how the app behaves on a phone. Raise it when libraries require it.
- `targetSdk` tells Android which behavior rules to apply to the app. Raising
  it **can change app behavior** (permissions, background work, file access,
  edge-to-edge layout). It is raised only in a dedicated PR, after reading
  Google's behavior-change list for that Android version and with the owner's
  approval. Data Dragon is not on Google Play, so there is no store deadline.
- `minSdk` is the oldest Android version that can install the app. It stays at
  26 unless a required library forces a change, which needs an owner decision.

## Skydoves colorpicker-compose and Vico

### colorpicker-compose (Calendar color picker)

Used only in `app/src/main/java/com/datadragon/app/ui/components/ColorPickerDialog.kt`.
`ColorPickerDialogTest` covers the behavior that must survive upgrades: opening
on the saved color, Okay returning the same color unchanged, the hex field
driving the wheel, invalid hex being ignored, Cancel changing nothing, and
results always being opaque `#RRGGBB`.

Version 1.1.2 changed a darker saved color when Okay was pressed without
edits (`#3366CC` came back as `#407FFF`). 1.3.0 fixes this; the test now
guards it.

### Vico (future Visual Data Graphs)

Vico is the approved charting library. It is **not installed yet**: add it in
the PR that builds Visual Data Graphs, not before.

When that work begins, use:

```toml
[versions]
vico = "3.3.1"            # or the newest stable 3.x at that time

[libraries]
vico-compose = { group = "com.patrykandpatrick.vico", name = "compose", version.ref = "vico" }
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }
```

- `compose` holds the charts. Verified in the 3.3.1 sources: line and column
  (bar) charts for categories and time series (`cartesian`), and pie charts
  with an optional donut hole (`pie`, `PieSize.Inner`). `compose-m3` makes
  them use the app's Material 3 colors and typography. Do not add `views` or `compose-m2`.
- Requirements of Vico 3.3.1, verified from its published files: minSdk 23,
  Kotlin 2.4.10 or newer, Compose 1.12.0 or newer, Material 3 1.4.0 or newer,
  kotlinx-coroutines 1.11.0. The current baseline meets every one.
- Adding Vico raises kotlinx-coroutines in the app to 1.11.0. That is expected.
- Chart colors should come from the app's theme roles in `ui/theme/` so that
  palettes stay centrally themeable (see `docs/STYLE.md`).
- Vico and colorpicker-compose share no dependencies that conflict.
- Re-check Vico's requirements against the then-current baseline before adding
  it; run `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
  in CI and check nothing persistence-related moved.

## Reviewing a Dependabot PR

1. **Read what changed.** The PR title and description list each dependency
   and its old and new version.
2. **Check the group.**
   - `persistence-and-backup-format` → follow "Changes that can affect user
     data" below before approving.
   - `compose` → expect possible small visual differences; check the screens
     listed under "Before merging" on a device.
   - `kotlin-toolchain` / `android-build-toolchain` → confirm the combination
     is in the compatibility tables above.
   - A major version → treat as a major update.
3. **Read the release notes** linked in the PR for breaking or behavior
   changes.
4. **Wait for CI.** Every required check must pass (see below).
5. **Fix failures on the PR branch**, never on `main`.
6. **The owner approves and merges.**

## Checks that must pass before merging

CI (`.github/workflows/build.yml`) runs on every push and every PR:

- Gradle Wrapper validation (the Wrapper is a genuine Gradle release).
- Theme token check (`scripts/check_theme_tokens.py`).
- Pinned dependency check (`verifyPinnedDependencies`): fails if a library
  silently changes the version of Room, kotlinx-serialization or the Kotlin
  standard library.
- All unit tests, which include:
  - every database migration from old versions to the current one;
  - backup and restore, including legacy backup files;
  - `BackupFormatStabilityTest`, which decodes a frozen backup written by the
    original library versions and requires it to re-encode byte-for-byte;
  - the color picker behavior tests.
- Room schema check: the schema generated by the build must equal the
  committed `app/schemas` files.
- Release APK build.

A green CI run is required but is not proof that an installed app updates
safely; see "Verifying an update on a device".

## Changes that can affect user data

A change can affect saved data when it touches any of:

- Room (`androidx.room`), SQLite, KSP, or anything in `app/schemas`;
- kotlinx-serialization, or any `@Serializable` class in `app/src/main/java/com/datadragon/app/data/`;
- `AppDatabase.kt` (version, entities, migrations);
- backup code (`Backup*.kt`) or its fixtures in `app/src/test/resources/fixtures`;
- `applicationId`, `versionCode`, or the signing configuration in `app/build.gradle.kts`.

For such a change:

- The Room schema check and `BackupFormatStabilityTest` must pass **without
  editing their committed files**. If an upgrade makes either fail, stop: the
  upgrade changes how data is stored. Do not regenerate the fixture or schema
  to make the test pass. Investigate, and get an owner decision.
- A real schema change needs a database version bump, an explicit `Migration`
  and migration tests. Never use destructive migration.
- UUIDs must never change. Follow the "Stable identity and UUIDs" rules in
  `CLAUDE.md` and `docs/BACKUP_SYSTEM_EXTENSION_GUIDE.md`.

## Verifying an update on a device

CI cannot install the app over an existing copy. Before merging a major
upgrade or a data-safety PR:

1. On the phone with the current Data Dragon, make a manual backup and copy the
   file somewhere safe.
2. Download the APK from the PR's CI run (artifact `data-dragon-release-apk`).
3. Install it **over** the existing app. Do not uninstall first. Android must
   show it as an update.
4. Open the app and confirm Forms, entries and follow-up notes, Lists, Lucky
   Lists, Idea Logs, Daily Tasks, Clicker Data and Statistics, Calendars and
   saved color presets, and Settings are all present.
5. Open a Calendar color, press Okay without changes, and confirm the color is
   unchanged.
6. Restore the backup from step 1 using Merge into a test copy if available,
   or simply confirm the restore screen accepts and validates the file.

If the phone says "App not installed", stop: the APK is signed differently or
has a lower `versionCode`. Do not uninstall to work around it, because that
deletes the data.

## Why an APK must never be downgraded or re-signed

Android only installs an update over an existing app when both of these hold:

- it is signed with the **same key** (`app/datadragon-debug.keystore`), and
- its `versionCode` is **not lower** than the installed one.

Otherwise Android refuses, and the only way to install is to uninstall, which
deletes all local data. So: never change the keystore or signing settings,
never lower `versionCode`, and never change `applicationId`.

## Major framework upgrades

For Kotlin, Compose, Room, AGP, Gradle, KSP or another foundational component:

1. Read the official migration guide and release notes.
2. List every related compatibility requirement (see "Coordinated updates").
3. Use a dedicated branch and PR for the upgrade.
4. Update related components together when they must match.
5. Run all checks, then verify on a device.
6. Record behavior or compatibility changes in the PR description.
7. Get the owner's approval before merging.

## When an upgrade breaks the build or a test

- `main` keeps the current working versions. All investigation happens on the
  PR branch.
- Read the CI log, find the root cause, and fix it on the branch.
- Never weaken, skip or delete a test to make an update pass.
- Never force libraries into a combination their documentation does not
  support.
- If it cannot be fixed now, close the PR (Dependabot will not reopen that
  exact version) or add an `ignore` entry in `dependabot.yml` with a comment
  naming the blocker, and record the blocker in this document.

## Reverting a problem update

- **Not merged yet:** close the PR and delete its branch. `main` was never
  changed, so nothing else is needed.
- **Already merged:** open a PR that reverts the merge commit (GitHub's
  "Revert" button on the merged PR does this). Before merging the revert,
  check that it does not undo a database version bump or migration: once an
  installed app has upgraded its database, an older build cannot open it, so a
  data-related change is fixed forward instead of reverted.

## Repository settings (owner, in GitHub)

These are set in the GitHub website, not in code:

1. **Settings → Advanced Security** (or **Code security**): turn on
   *Dependabot alerts* and *Dependabot security updates*.
2. **Settings → Rules → Rulesets → New branch ruleset** for `main`:
   - Enforcement: Active; target the default branch.
   - Require a pull request before merging.
   - Require status checks to pass, and add the check named `build`.
   - Block force pushes and deletions.
3. Do not enable auto-merge for Dependabot PRs.
