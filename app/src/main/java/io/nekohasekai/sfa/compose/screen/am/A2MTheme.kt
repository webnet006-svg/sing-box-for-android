package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design system A2M TUN, partagé entre tous les écrans.
 *
 * Les couleurs sont pilotées par [A2MThemeState] : changer de palette ou de mode
 * (clair/sombre) met à jour ces états et recolore instantanément tout l'arbre
 * Compose, sans modifier les écrans. Les valeurs initiales = palette « a2m »
 * claire (visuel de référence : fond menthe très pâle, cartes blanches, teal profond).
 */
object A2MColors {
    var BackgroundTop by mutableStateOf(Color(0xFFDDF3EE))
        private set
    var BackgroundMid by mutableStateOf(Color(0xFFF7FBFA))
        private set
    var Accent by mutableStateOf(Color(0xFF0FA98F))
        private set
    var Text by mutableStateOf(Color(0xFF0D2B33))
        private set
    var Muted by mutableStateOf(Color(0xFF54787F))
        private set
    var Faint by mutableStateOf(Color(0xFF93B3B8))
        private set
    var CardBorder by mutableStateOf(Color(0xFFD8E8E5))
        private set
    var CardBorderSelected by mutableStateOf(Color(0xFF0FA98F))
        private set
    var AccentUpload by mutableStateOf(Color(0xFF7C5CFF))
        private set
    var Danger by mutableStateOf(Color(0xFFE5484D))
        private set
    var Amber by mutableStateOf(Color(0xFFD97706))
        private set

    /** Surface d'une tuile interne (icône, chip) légèrement plus sombre que la carte. */
    var Tile by mutableStateOf(Color(0xFFE3F2EE))
        private set

    var BackgroundBrush by mutableStateOf(
        Brush.verticalGradient(listOf(Color(0xFFDDF3EE), Color(0xFFF7FBFA), Color(0xFFF7FBFA))),
    )
        private set
    var CardBrush by mutableStateOf(Brush.linearGradient(listOf(Color.White, Color(0xFFF4FAF8))))
        private set

    /** Couleurs de dégradé de carte courantes (utilisées par le catalogue de thèmes). */
    var CardTop by mutableStateOf(Color.White)
        private set
    var CardBottom by mutableStateOf(Color(0xFFF4FAF8))
        private set

    /** Applique une palette complète (appelé uniquement par A2MThemeState). */
    internal fun applyPalette(p: A2MPaletteTokens) {
        BackgroundTop = p.backgroundTop
        BackgroundMid = p.backgroundMid
        Accent = p.accent
        Text = p.text
        Muted = p.textMuted
        Faint = p.faint
        CardBorder = p.border
        CardBorderSelected = p.accent
        AccentUpload = p.accentUpload
        Danger = p.danger
        Amber = p.amber
        Tile = p.tile
        CardTop = p.cardTop
        CardBottom = p.cardBottom
        BackgroundBrush = Brush.verticalGradient(listOf(p.backgroundTop, p.backgroundMid, p.backgroundMid))
        CardBrush = Brush.linearGradient(listOf(p.cardTop, p.cardBottom))
    }
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
