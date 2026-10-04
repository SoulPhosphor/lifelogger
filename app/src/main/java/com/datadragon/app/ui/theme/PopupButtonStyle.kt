package com.datadragon.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/** Two roles only. Cancel/dismiss is Primary; it does not introduce a third style. */
enum class PopupButtonRole { PRIMARY, DESTRUCTIVE }

/** Complete outlined popup treatment. Change a role here, rather than its callers. */
@Immutable
data class PopupButtonStyle(
    val contentColor: Color,
    val outlineColor: Color,
    val shape: Shape,
    val outlineWidth: Dp,
    val textStyle: TextStyle,
    val horizontalInset: Dp,
    val verticalInset: Dp,
)

@Immutable
data class PopupButtonStyles(val primary: PopupButtonStyle, val destructive: PopupButtonStyle) {
    fun forRole(role: PopupButtonRole) = when (role) {
        PopupButtonRole.PRIMARY -> primary
        PopupButtonRole.DESTRUCTIVE -> destructive
    }
}

/** Identical today; each role has its own complete value for future independent styling. */
fun popupButtonStylesFor(colors: AppColors, outline: Color): PopupButtonStyles {
    val primary = PopupButtonStyle(
        contentColor = colors.popupPrimary,
        outlineColor = outline,
        shape = DefaultAppShapes.control,
        outlineWidth = DefaultAppShapes.controlBorder,
        // Material's text-button label (14sp, Medium), as these buttons had before.
        textStyle = AppTypography.labelLarge,
        horizontalInset = DefaultAppSpacing.rowInset,
        verticalInset = DefaultAppSpacing.related,
    )
    return PopupButtonStyles(primary, primary.copy(contentColor = colors.popupDestructive))
}

val LocalPopupButtonStyles = staticCompositionLocalOf {
    val scheme = androidx.compose.material3.lightColorScheme()
    popupButtonStylesFor(appColorsFor(scheme), scheme.outline)
}
