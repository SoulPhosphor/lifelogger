package com.datadragon.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Named icon sizes, so a screen never hard-codes a `24.dp` on an icon and the
 * sizing rule lives in one place.
 */
@Immutable
data class AppSizes(
    /**
     * The application settings cog shown at the top of the main screen and as
     * the per-data-type settings control at the top-left of a detail screen's
     * title. Deliberately a step larger than a plain top-bar icon: the cog glyph
     * carries more internal padding, so at the plain icon size it reads smaller
     * than the icons beside it.
     */
    val settingsCog: Dp,
)

val DefaultAppSizes = AppSizes(
    settingsCog = 30.dp,
)

/** Supplied by `DataDragonTheme`; read through `AppTheme.sizes`. */
val LocalAppSizes = staticCompositionLocalOf { DefaultAppSizes }
