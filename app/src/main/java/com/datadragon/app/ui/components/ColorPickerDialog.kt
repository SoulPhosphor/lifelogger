package com.datadragon.app.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import com.datadragon.app.ui.components.AccessibleOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.datadragon.app.ui.theme.AppTheme

/**
 * The color picker for a calendar color swatch: a circular rainbow wheel, a
 * brightness control beneath it, and a hex field, kept in sync. No alpha control
 * — the chosen color is always fully opaque and returned as a 6-digit `#RRGGBB`.
 *
 * Opens initialized to [initialHex]. Cancel closes without change; Okay hands the
 * selected color back through [onConfirm].
 */
@Composable
fun ColorPickerDialog(
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val controller = rememberColorPickerController()
    val fallbackColor = AppTheme.colors.invalidContentColor
    val initialColor = remember(initialHex, fallbackColor) { parseHexOrNull(initialHex) ?: fallbackColor }

    // The selected color is the source of truth for Okay; the hex field mirrors it.
    var selectedColor by remember { mutableStateOf(initialColor) }
    var hexInput by remember { mutableStateOf(toRgbHex(initialColor)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PopupButton(onClick = { onConfirm(toRgbHex(selectedColor)) }) { Text("Okay") }
        },
        dismissButton = {
            PopupButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.rowInset)) {
                HsvColorPicker(
                    modifier = Modifier.fillMaxWidth().height(AppTheme.sizes.colorWheelHeight),
                    controller = controller,
                    initialColor = initialColor,
                    onColorChanged = { envelope: ColorEnvelope ->
                        selectedColor = envelope.color
                        // Reflect only wheel/brightness moves into the field, so
                        // typing in the field isn't overwritten mid-edit (typed
                        // changes drive the wheel with fromUser = false below).
                        if (envelope.fromUser) hexInput = toRgbHex(envelope.color)
                    },
                )
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().height(AppTheme.sizes.brightnessHeight),
                    controller = controller,
                )
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        hexInput = input
                        parseHexOrNull(input)?.let { controller.selectByColor(it, fromUser = false) }
                    },
                    label = { Text("Hex Color") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/** "#RRGGBB" (uppercase, fully opaque) for [color]. */
private fun toRgbHex(color: Color): String = "#%06X".format(0xFFFFFF and color.toArgb())

/** Parse "#RRGGBB" (with or without the leading #) to an opaque color, or null. */
private fun parseHexOrNull(input: String): Color? {
    val trimmed = input.trim().removePrefix("#")
    if (!trimmed.matches(Regex("[0-9a-fA-F]{6}"))) return null
    return runCatching { Color(android.graphics.Color.parseColor("#$trimmed")) }.getOrNull()
}
