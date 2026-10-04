package com.datadragon.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.datadragon.app.ui.theme.AppTheme
import com.datadragon.app.ui.theme.PopupButtonRole

/** Popup-only button. Screen buttons deliberately retain their existing treatment. */
@Composable
fun PopupButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: PopupButtonRole = PopupButtonRole.PRIMARY,
    content: @Composable RowScope.() -> Unit,
) {
    val style = AppTheme.popupButtons.forRole(role)
    val contentColor = if (enabled) style.contentColor else
        MaterialTheme.colorScheme.onSurface.copy(alpha = AppTheme.opacity.disabledContent)
    val outlineColor = if (enabled) style.outlineColor else
        MaterialTheme.colorScheme.onSurface.copy(alpha = AppTheme.opacity.disabledBorder)
    Row(
        modifier = modifier
            // Reserves a 48dp tap area around the button without drawing it; the
            // outline still hugs the caption.
            .minimumInteractiveComponentSize()
            .clip(style.shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .border(style.outlineWidth, outlineColor, style.shape)
            .padding(horizontal = style.horizontalInset, vertical = style.verticalInset),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(style.textStyle) { content() }
        }
    }
}
