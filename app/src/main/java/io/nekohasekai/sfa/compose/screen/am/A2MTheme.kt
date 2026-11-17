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
    val BackgroundTop = Color(0xFF030C14)
    val BackgroundMid = Color(0xFF06161F)
    val Accent = Color(0xFF16E0C4)
    val Text = Color(0xFFF2FAF9)
    val Muted = Color(0xFF9FB8BD)
    val Faint = Color(0xFF5E7B80)
    val CardBorder = Color(0xFF1B3540)
    val CardBorderSelected = Color(0xFF2AD9C0)
    val AccentUpload = Color(0xFF9B5CFF)
    val Danger = Color(0xFFFF5C7A)
    val Amber = Color(0xFFFFC24B)

    /** Surface d'une tuile interne (icône, chip) légèrement plus claire que la carte. */
    val Tile = Color(0xFF0E2A33)

    val BackgroundBrush = Brush.verticalGradient(listOf(BackgroundTop, BackgroundMid, BackgroundTop))
    val CardBrush = Brush.linearGradient(listOf(Color(0xFF0B2231), Color(0xFF071520)))
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
