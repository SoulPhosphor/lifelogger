package com.datadragon.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Extra semantic colors not supplied by Material. All defaults preserve the old look. */
@Immutable
data class AppColors(
    val invalidContentColor: Color = Color.Gray,
    val popupPrimary: Color,
    val popupDestructive: Color,
)

fun appColorsFor(scheme: ColorScheme) = AppColors(
    popupPrimary = scheme.onSurface,
    popupDestructive = scheme.onSurface,
)

val LocalAppColors = staticCompositionLocalOf { appColorsFor(androidx.compose.material3.lightColorScheme()) }

/** Material's existing disabled treatment; kept separate for content and outline. */
@Immutable
data class AppOpacity(
    val disabledContent: Float = 0.38f,
    val disabledBorder: Float = 0.12f,
    /** Material's focus state layer; shown only for hardware-keyboard focus, never on touch. */
    val keyboardFocus: Float = 0.1f,
)
val DefaultAppOpacity = AppOpacity()
val LocalAppOpacity = staticCompositionLocalOf { DefaultAppOpacity }
