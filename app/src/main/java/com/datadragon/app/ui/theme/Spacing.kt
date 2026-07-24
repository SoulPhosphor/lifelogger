package com.datadragon.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's named vertical gaps. A screen never writes a bare `8.dp` between
 * rows — it asks for one of these, so the spacing rule lives in one place.
 */
@Immutable
data class AppSpacing(
    /**
     * The default gap between related items stacked in a section: a hint under
     * its label, and one row to the next. This is the gap between "Only applies
     * to future items." and the toggle under it in Settings — that pairing is
     * the reference for how tight this gap is.
     */
    val related: Dp,
    /**
     * Extra breathing room between two standalone action controls stacked
     * directly on top of each other — e.g. a "Choose File" button immediately
     * followed by an unrelated action button. Wider than [related] because
     * neither control belongs to the other.
     */
    val distinctControls: Dp,
)

val DefaultAppSpacing = AppSpacing(
    related = 8.dp,
    distinctControls = 24.dp,
)

/** Supplied by `DataDragonTheme`; read through `AppTheme.spacing`. */
val LocalAppSpacing = staticCompositionLocalOf { DefaultAppSpacing }
