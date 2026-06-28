# Changelog Worklog

## 2026-06-28 — Phase 1 navigation skeleton

### Summary of changes
- Created the single-module Android project with Kotlin, Jetpack Compose, Material 3, Navigation Compose, and version-catalog dependency management.
- Added the Phase 1 navigation skeleton: Home, Create Log, Entry List, New Entry, Settings, and Backup screens.
- Wired the locked navigation behavior: Home top-right `+` opens Create Log, log rows open Entry List, row `+` opens the full-screen New Entry screen, top-left `⚙` opens Settings, and Home `↓` opens Backup.
- Added a GitHub Actions workflow that builds the debug APK and uploads it as an artifact on push and pull request.

### Known issues
- Data is placeholder-only. No database, parser, dynamic forms, exports, backups, restore, deletion, or persistence are implemented yet.
- Delete and download controls are visible as placeholders only and do not perform actions in Phase 1.
- Save and Preview buttons are placeholders only until later phases.

### Recommended next step
- Build Phase 2: add Room persistence for log templates and load real templates on the Home screen.

## 2026-06-28 — Phase 1 PR handoff cleanup

### Summary of changes
- Removed Gradle wrapper files so the pull request contains only text/source files and no binary wrapper JAR.
- Updated GitHub Actions to build with the Gradle command provided by the CI setup step.
- Added a `.gitignore` for Gradle, Android, IDE, and OS-generated files.

### Known issues
- Local compilation is still limited by this environment's blocked access to Android/Google Maven artifacts.
- Phase 1 remains placeholder-only; persistence, parsing, and real entry saving start in later phases.

### Recommended next step
- Re-run the pull request creation with the binary wrapper removed, then let GitHub Actions compile the debug APK.

## 2026-06-28 — CI version compatibility fix

### Summary of changes
- Changed Android and Gradle version pins to conservative stable versions that are available in public CI: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Navigation Compose 2.8.5, Gradle 8.9, and Android SDK 35.
- Updated app compile and target SDK values to 35 to match the SDK package installed by GitHub Actions.

### Known issues
- Local compilation remains blocked by this environment's inability to download Android Gradle Plugin artifacts from Google's Maven repository.
- Phase 1 remains a placeholder navigation skeleton only.

### Recommended next step
- Re-run GitHub Actions for this branch; the workflow now uses stable Android/Gradle versions instead of speculative future versions.
