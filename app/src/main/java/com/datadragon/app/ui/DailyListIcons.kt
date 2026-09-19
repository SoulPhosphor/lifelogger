package com.datadragon.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.datadragon.app.R
import com.datadragon.app.data.CelebrationIcon

/**
 * Resolves each Daily List celebration choice to its **exact** icon.
 *
 * `Check Circle` and `Celebration` come from the bundled Compose extended
 * icon artifact. `Fire Check`, `Cheer`, `Award Star`, and `Family Star`
 * (like `Event Note`, `Calendar Add On`, and `Cycle` elsewhere in the
 * feature) exist only in Material Symbols, so they are bundled locally as
 * vector drawables traced verbatim from Google's Material Symbols source —
 * nothing is hotlinked or loaded from the network at runtime.
 */
@Composable
fun celebrationIconVector(icon: CelebrationIcon): ImageVector = when (icon) {
    CelebrationIcon.CHECK_CIRCLE -> Icons.Filled.CheckCircle
    CelebrationIcon.FIRE_CHECK -> ImageVector.vectorResource(R.drawable.ic_fire_check)
    CelebrationIcon.CELEBRATION -> Icons.Filled.Celebration
    CelebrationIcon.CHEER -> ImageVector.vectorResource(R.drawable.ic_cheer)
    CelebrationIcon.AWARD_STAR -> ImageVector.vectorResource(R.drawable.ic_award_star)
    CelebrationIcon.FAMILY_STAR -> ImageVector.vectorResource(R.drawable.ic_family_star)
}
