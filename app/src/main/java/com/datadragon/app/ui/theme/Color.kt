package com.datadragon.app.ui.theme

import androidx.compose.ui.graphics.Color

val DragonGreen = Color(0xFF2E7D32)
val DragonGreenDark = Color(0xFF1B5E20)
val DragonAmber = Color(0xFFF9A825)
val DeleteRed = Color(0xFFC62828)

/** A stored/user-selected hex color is content, not an application palette literal. */
fun contentColorFromHex(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
