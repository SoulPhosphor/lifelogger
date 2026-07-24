package com.datadragon.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shapes every framed control shares.
 *
 * There is exactly one control shape in this app. A button and a drop-down box
 * are framed identically — same corner, same outline — so nothing on screen is
 * ever a pill.
 */
@Immutable
data class AppShapes(
    /** The corner of every framed control: buttons and drop-down boxes alike. */
    val control: Shape,
    /** The outline thickness of every framed control. */
    val controlBorder: Dp,
)

val DefaultAppShapes = AppShapes(
    control = RoundedCornerShape(10.dp),
    controlBorder = 1.dp,
)

/** Supplied by `DataDragonTheme`; read through `AppTheme.shapes`. */
val LocalAppShapes = staticCompositionLocalOf { DefaultAppShapes }
