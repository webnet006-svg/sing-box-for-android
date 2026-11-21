package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/** Palette Cyber Security commune aux écrans A2M TUN. */
object A2MDesignTokens {
    val background = Color(0xFF06152E)
    val backgroundDeep = Color(0xFF030C1C)
    val backgroundHalo = Color(0xFF14418A)
    val cardTop = Color(0x7314325F)
    val cardBottom = Color(0xB30A1E46)
    val border = Color(0x3850A0FF)
    val cyan = Color(0xFF20DFFF)
    val blue = Color(0xFF287BFF)
    val violet = Color(0xFF9A8CFF)
    val success = Color(0xFF2BF0A8)
    val warning = Color(0xFFFFC93C)
    val error = Color(0xFFFF5C6C)
    val text = Color(0xFFE8F2FF)
    val textSecondary = Color(0xFF8CA6C8)
    val disabled = Color(0xFF4A6A99)
    val cardShape = RoundedCornerShape(22.dp)
    val tileShape = RoundedCornerShape(14.dp)
    val pillShape = RoundedCornerShape(999.dp)
}

/** Styles système : chiffres fixes sans téléchargement de police à l'exécution. */
object A2MType {
    private val selectedFamily get() = A2MFontCatalog.family(A2MThemeState.fontId)
    val screenTitle get() = TextStyle(fontFamily = selectedFamily, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    val cardTitle get() = TextStyle(fontFamily = selectedFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val body get() = TextStyle(fontFamily = selectedFamily, fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val secondary get() = TextStyle(fontFamily = selectedFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp)
    val metric = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 0.sp)
}

/**
 * Design system A2M TUN, partagé entre tous les écrans.
 *
 * Les couleurs sont pilotées par [A2MThemeState] : changer de palette ou de mode
 * (clair/sombre) met à jour ces états et recolore instantanément tout l'arbre
 * Compose, sans modifier les écrans. Les valeurs initiales = palette « a2m »
 * claire (visuel de référence : fond menthe très pâle, cartes blanches, teal profond).
 */
object A2MColors {
    var BackgroundTop by mutableStateOf(A2MDesignTokens.backgroundHalo)
        private set
    var BackgroundMid by mutableStateOf(A2MDesignTokens.background)
        private set
    var Accent by mutableStateOf(A2MDesignTokens.cyan)
        private set
    var Text by mutableStateOf(A2MDesignTokens.text)
        private set
    var Muted by mutableStateOf(A2MDesignTokens.textSecondary)
        private set
    var Faint by mutableStateOf(A2MDesignTokens.disabled)
        private set
    val Disabled: Color get() = Faint
    var CardBorder by mutableStateOf(A2MDesignTokens.border)
        private set
    var CardBorderSelected by mutableStateOf(A2MDesignTokens.cyan)
        private set
    var AccentUpload by mutableStateOf(A2MDesignTokens.violet)
        private set
    var Danger by mutableStateOf(A2MDesignTokens.error)
        private set
    var Amber by mutableStateOf(A2MDesignTokens.warning)
        private set

    /** Surface d'une tuile interne (icône, chip) légèrement plus sombre que la carte. */
    var Tile by mutableStateOf(Color(0xFF102A50))
        private set

    var BackgroundBrush by mutableStateOf(
        Brush.verticalGradient(listOf(A2MDesignTokens.backgroundHalo, A2MDesignTokens.background, A2MDesignTokens.backgroundDeep)),
    )
        private set
    var CardBrush by mutableStateOf(Brush.linearGradient(listOf(A2MDesignTokens.cardTop, A2MDesignTokens.cardBottom)))
        private set

    /** Couleurs de dégradé de carte courantes (utilisées par le catalogue de thèmes). */
    var CardTop by mutableStateOf(A2MDesignTokens.cardTop)
        private set
    var CardBottom by mutableStateOf(A2MDesignTokens.cardBottom)
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
        BackgroundBrush = Brush.verticalGradient(listOf(p.backgroundTop, p.backgroundMid, A2MDesignTokens.backgroundDeep))
        CardBrush = Brush.linearGradient(listOf(p.cardTop, p.cardBottom))
    }
}

/**
 * Formes standardisées : évite les rayons incohérents.
 */
object A2MShapes {
    val card = A2MDesignTokens.cardShape
    val tile = A2MDesignTokens.tileShape
    val chip = A2MDesignTokens.pillShape
}

/** Couleur du badge protocole, dérivée du code court réel (VM, VL, TR…). */
internal fun protocolBadgeColor(code: String): Color =
    when (code) {
        "VM" -> A2MDesignTokens.violet
        "VL" -> A2MDesignTokens.blue
        "TR" -> Color(0xFFFF8A4C)
        "SS" -> A2MDesignTokens.success
        "HY", "H2" -> A2MDesignTokens.warning
        "TU" -> Color(0xFFFF72D2)
        "WG" -> Color(0xFFB8E986)
        else -> A2MColors.Accent
    }
