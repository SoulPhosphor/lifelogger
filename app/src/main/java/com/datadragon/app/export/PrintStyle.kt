package com.datadragon.app.export

import android.graphics.Typeface

/** Print appearance in points (A4 at 72dpi). Independent of device theme and font scale. */
object PrintStyle {
    const val pageWidth = 595
    const val pageHeight = 842
    const val margin = 40f
    const val subIndent = 24f
    const val titleSize = 20f
    const val metadataSize = 11f
    const val headingSize = 14f
    const val bodySize = 12f
    const val sectionGap = 8f
    const val relatedGap = 4f
    const val ruleWidth = 1f
    val bodyColor = 0xFF000000.toInt()
    val metadataColor = 0xFF555555.toInt()
    val ruleColor = 0xFFCCCCCC.toInt()
    val boldTypeface: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
