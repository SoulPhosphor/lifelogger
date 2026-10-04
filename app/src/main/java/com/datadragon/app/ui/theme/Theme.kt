package com.datadragon.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = DragonGreen,
    secondary = DragonAmber,
    error = DeleteRed,
)

private val DarkColors = darkColorScheme(
    primary = DragonGreen,
    secondary = DragonAmber,
    error = DeleteRed,
)

/**
 * The app's theme. Every color and text size a screen draws comes from here —
 * the Material color scheme, the Material type scale ([AppTypography]), and the
 * app's own named styles ([AppTextStyles]). Adding a theme later means adding a
 * branch in this function, not editing screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataDragonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(
        LocalAppTextStyles provides DefaultAppTextStyles,
        LocalAppShapes provides DefaultAppShapes,
        LocalAppSpacing provides DefaultAppSpacing,
        LocalAppSizes provides DefaultAppSizes,
        LocalAppColors provides appColorsFor(colorScheme),
        LocalAppOpacity provides DefaultAppOpacity,
        LocalPopupButtonStyles provides popupButtonStylesFor(appColorsFor(colorScheme), colorScheme.outline),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppMaterialShapes,
        ) {
            // No touch flash anywhere: pressing, hovering and dragging draw nothing.
            // Only a hardware-keyboard focus highlight remains, so keyboard users
            // can see where they are; touch never focuses these controls.
            CompositionLocalProvider(
                LocalRippleConfiguration provides RippleConfiguration(
                    rippleAlpha = RippleAlpha(
                        draggedAlpha = 0f,
                        focusedAlpha = DefaultAppOpacity.keyboardFocus,
                        hoveredAlpha = 0f,
                        pressedAlpha = 0f,
                    ),
                ),
                content = content,
            )
        }
    }
}

/**
 * Access to the app's own theme values, alongside `MaterialTheme` — read a named
 * style with `AppTheme.textStyles.sectionHeader`.
 */
object AppTheme {
    val popupButtons: PopupButtonStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalPopupButtonStyles.current

    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val opacity: AppOpacity
        @Composable
        @ReadOnlyComposable
        get() = LocalAppOpacity.current

    val textStyles: AppTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTextStyles.current

    val shapes: AppShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalAppShapes.current

    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSpacing.current

    val sizes: AppSizes
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSizes.current
}
