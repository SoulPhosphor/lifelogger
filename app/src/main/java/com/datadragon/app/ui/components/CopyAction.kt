package com.datadragon.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/** Uses the same clipboard path as individual field copying. */
@Composable
fun rememberCopyText(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return { clipboard.setText(AnnotatedString(it)) }
}
