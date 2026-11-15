package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Palette de l'interface A2M TUN, partagée entre les écrans.
 */
object A2MColors {
    val BackgroundTop = Color(0xFF02091A)
    val BackgroundMid = Color(0xFF03112A)
    val Cyan = Color(0xFF12E7FF)
    val Text = Color(0xFFF4F8FF)
    val Muted = Color(0xFF9DB8DF)
    val CardBorder = Color(0xFF1766BD)
    val CardBorderSelected = Color(0xFF08DFFF)
    val AccentUpload = Color(0xFF9B5CFF)
    val Danger = Color(0xFFFF5C7A)

    val BackgroundBrush = Brush.linearGradient(listOf(BackgroundTop, BackgroundMid, BackgroundTop))
    val CardBrush = Brush.linearGradient(listOf(Color(0xFF071B5B), Color(0xFF04112A)))
}
