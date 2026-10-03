package com.datadragon.app.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics

/** External field labels remain outside the outline; their names travel to the editable node. */
val LocalAccessibleFieldLabel = staticCompositionLocalOf<String?> { null }

/** Material rendering and editing behavior are preserved; only accessibility metadata is added. */
@Composable
fun AccessibleOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    accessibleLabel: String? = LocalAccessibleFieldLabel.current,
    accessibleError: String? = null,
) {
    MaterialOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics {
            // Floating Material labels already label their own field.
            if (label == null && !accessibleLabel.isNullOrBlank()) contentDescription = accessibleLabel
            if (isError && accessibleError != null) error(accessibleError)
        },
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
    )
}
