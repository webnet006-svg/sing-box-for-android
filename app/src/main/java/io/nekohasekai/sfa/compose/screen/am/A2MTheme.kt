package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design system A2M TUN, partagé entre tous les écrans.
 *
 * Style : interface VPN moderne sombre — quasi-noir bleuté, accent teal/menthe,
 * surfaces en dégradé subtil, bordures discrètes, grands chiffres de débit.
 */
object A2MColors {
    // Palette claire (visuel de référence : fond menthe très pâle, cartes blanches, teal profond)
    val BackgroundTop = Color(0xFFDDF3EE)
    val BackgroundMid = Color(0xFFF7FBFA)
    val Accent = Color(0xFF0FA98F)
    val Text = Color(0xFF0D2B33)
    val Muted = Color(0xFF54787F)
    val Faint = Color(0xFF93B3B8)
    val CardBorder = Color(0xFFD8E8E5)
    val CardBorderSelected = Color(0xFF0FA98F)
    val AccentUpload = Color(0xFF7C5CFF)
    val Danger = Color(0xFFE5484D)
    val Amber = Color(0xFFD97706)

    /** Surface d'une tuile interne (icône, chip) légèrement plus sombre que la carte. */
    val Tile = Color(0xFFE3F2EE)

    val BackgroundBrush = Brush.verticalGradient(listOf(BackgroundTop, BackgroundMid, BackgroundMid))
    val CardBrush = Brush.linearGradient(listOf(Color.White, Color(0xFFF4FAF8)))
}

/**
 * Formes standardisées : évite les rayons incohérents.
 */
object A2MShapes {
    val card = RoundedCornerShape(20.dp)
    val tile = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(999.dp)
}

/** Couleur du badge protocole, dérivée du code court réel (VM, VL, TR…). */
internal fun protocolBadgeColor(code: String): Color =
    when (code) {
        "VM" -> Color(0xFF7C5CFF)
        "VL" -> Color(0xFF189AE8)
        "TR" -> Color(0xFFFF7043)
        "SS" -> Color(0xFF2EAF6F)
        "HY", "H2" -> Color(0xFFFFB300)
        "TU" -> Color(0xFFE040FB)
        "WG" -> Color(0xFF8BC34A)
        else -> A2MColors.Accent
    }
