package com.datadragon.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Multiple Selection's look (docs/UI_ELEMENTS.md, "Multiple Selection"): the selection bar under a Main
 * screen's top bar, and the dashed "coupon" outline around a selected card.
 */
@Immutable
data class AppSelectionStyle(
    /** The selection bar: its corner, fill, and the gap around it. */
    val barShape: Shape,
    val barColor: Color,
    val barContentColor: Color,
    val barSubtitleColor: Color,
    val barOuterInset: Dp,
    val barInnerInset: Dp,
    /** "3 Selected": the bar's live count. */
    val countText: TextStyle,
    /** "Tap a card to toggle selection". */
    val subtitleText: TextStyle,
    /** The dashed outline drawn just outside a selected card. */
    val outlineColor: Color,
    val outlineWidth: Dp,
    val outlineDash: Dp,
    val outlineGap: Dp,
    /** How far outside the card the outline runs, and its corner radius. */
    val outlineOffset: Dp,
    val outlineCorner: Dp,
)

fun selectionStyleFor(colorScheme: ColorScheme, typography: Typography): AppSelectionStyle = AppSelectionStyle(
    barShape = RoundedCornerShape(16.dp),
    barColor = colorScheme.surfaceContainerHigh,
    barContentColor = colorScheme.onSurface,
    barSubtitleColor = colorScheme.onSurfaceVariant,
    barOuterInset = 12.dp,
    barInnerInset = 16.dp,
    countText = typography.titleLarge.copy(fontWeight = FontWeight.Bold),
    subtitleText = typography.bodyMedium,
    outlineColor = colorScheme.onSurface,
    outlineWidth = 2.dp,
    outlineDash = 10.dp,
    outlineGap = 6.dp,
    outlineOffset = 5.dp,
    outlineCorner = 18.dp,
)

/** Supplied by `DataDragonTheme`; read through `AppTheme.selection`. */
val LocalAppSelectionStyle = staticCompositionLocalOf<AppSelectionStyle?> { null }
