package com.datadragon.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Shapes
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
    /** Existing treatments differ by role; do not normalize without owner approval. */
    val validation: Shape,
    val validationBorder: Dp,
    val calendarCell: Shape,
    val colorPreview: Shape,
    val legendSwatch: Shape,
    val modeSelection: Shape,
)

val DefaultAppShapes = AppShapes(
    control = RoundedCornerShape(10.dp),
    controlBorder = 1.dp,
    validation = RoundedCornerShape(8.dp),
    validationBorder = 2.dp,
    calendarCell = RoundedCornerShape(6.dp),
    colorPreview = RoundedCornerShape(6.dp),
    legendSwatch = RoundedCornerShape(3.dp),
    modeSelection = CircleShape,
)

/** Supplied by `DataDragonTheme`; read through `AppTheme.shapes`. */
val LocalAppShapes = staticCompositionLocalOf { DefaultAppShapes }

/** Retains the existing Material defaults for cards, fields, and picker dialogs. */
val AppMaterialShapes = Shapes()
