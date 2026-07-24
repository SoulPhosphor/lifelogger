package com.datadragon.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * The app's Material 3 type scale — the single place the base text sizes live.
 *
 * Screens never hard-code a font size. They ask for a *named* style, either a
 * Material slot (`MaterialTheme.typography.bodyLarge`) or one of the app's own
 * semantic styles in [AppTextStyles]. Changing a theme then means changing this
 * file, not hunting through screens.
 */
val AppTypography = Typography()

/**
 * The app's own named styles, for roles Material's 15 slots don't cover.
 *
 * These exist so a future theme can restyle a *role* ("section header") in one
 * place instead of every screen that happens to draw one.
 */
@Immutable
data class AppTextStyles(
    /** A heading over a group of settings — deliberately smaller than a screen title. */
    val sectionHeader: TextStyle,
    /** A heading for one block inside a section — a step below [sectionHeader]. */
    val subsectionHeader: TextStyle,
    /** The main line of a settings row (the thing being switched or chosen). */
    val settingTitle: TextStyle,
    /** Explanatory text under a heading, row, or button. */
    val settingDescription: TextStyle,
    /** The main line of a tappable option inside a dialog. */
    val dialogOptionTitle: TextStyle,
    /** The one-line explanation under a dialog option. */
    val dialogOptionSubtitle: TextStyle,
    /**
     * Text inside a framed control — a button caption or a drop-down's current
     * value. Buttons and drop-downs share it so they read as the same control.
     */
    val controlLabel: TextStyle,
)

/**
 * The default (only, for now) set of app styles.
 *
 * A screen title in the top bar is `titleLarge` (22sp), so [AppTextStyles.sectionHeader]
 * is set a step below it at 18sp: clearly a heading, clearly not the screen title.
 */
val DefaultAppTextStyles = AppTextStyles(
    sectionHeader = AppTypography.titleMedium.copy(fontSize = 18.sp),
    subsectionHeader = AppTypography.titleMedium,
    settingTitle = AppTypography.bodyLarge,
    settingDescription = AppTypography.bodySmall,
    dialogOptionTitle = AppTypography.bodyLarge,
    dialogOptionSubtitle = AppTypography.bodySmall,
    controlLabel = AppTypography.bodyLarge,
)

/** Supplied by `DataDragonTheme`; read through `AppTheme.textStyles`. */
val LocalAppTextStyles = staticCompositionLocalOf { DefaultAppTextStyles }
