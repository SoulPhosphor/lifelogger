package com.datadragon.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Named dimensions, so a screen never hard-codes a `24.dp` on an icon and the
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
    /** Glyph sizes and existing compact icon hit areas; no accessibility resize in this audit. */
    val icon: Dp,
    val smallIcon: Dp,
    val inlineIcon: Dp,
    val legendSwatch: Dp,
    val compactIconTarget: Dp,
    val modeIndicator: Dp,
    val modeTarget: Dp,
    val loadingIndicator: Dp,
    val loadingStroke: Dp,
    /** Distinct existing numeric field widths, retained independently. */
    val shortNumberWidth: Dp,
    val statisticsNumberWidth: Dp,
    val numberWidth: Dp,
    val clickerNumberWidth: Dp,
    /** Minimums only: text fields remain free to grow with their contents. */
    val editorMinHeight: Dp,
    val notesMinHeight: Dp,
    val followUpMinHeight: Dp,
    val largeEditorMinHeight: Dp,
    val textLineHeight: Dp,
    /** Color picker/calendar geometry. */
    val colorPreview: Dp,
    val colorWheelHeight: Dp,
    val brightnessHeight: Dp,
    val swatchColumnWidth: Dp,
    val dialogElevation: Dp,
)

val DefaultAppSizes = AppSizes(
    settingsCog = 30.dp,
    icon = 24.dp,
    smallIcon = 18.dp,
    inlineIcon = 16.dp,
    legendSwatch = 14.dp,
    compactIconTarget = 32.dp,
    modeIndicator = 36.dp,
    modeTarget = 48.dp,
    loadingIndicator = 20.dp,
    loadingStroke = 2.dp,
    shortNumberWidth = 72.dp,
    statisticsNumberWidth = 88.dp,
    numberWidth = 96.dp,
    clickerNumberWidth = 120.dp,
    editorMinHeight = 96.dp,
    notesMinHeight = 120.dp,
    followUpMinHeight = 144.dp,
    largeEditorMinHeight = 160.dp,
    textLineHeight = 24.dp,
    colorPreview = 32.dp,
    colorWheelHeight = 280.dp,
    brightnessHeight = 32.dp,
    swatchColumnWidth = 56.dp,
    dialogElevation = 6.dp,
)

/** Supplied by `DataDragonTheme`; read through `AppTheme.sizes`. */
val LocalAppSizes = staticCompositionLocalOf { DefaultAppSizes }
