package com.datadragon.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

/**
 * Access to the app's own theme values, alongside `MaterialTheme` — read a named
 * style with `AppTheme.textStyles.sectionHeader`.
 */
object AppTheme {
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
}
