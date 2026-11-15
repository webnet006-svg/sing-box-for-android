package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding

private val A2M_PLACEHOLDER_BG =
    Brush.linearGradient(listOf(Color(0xFF02091A), Color(0xFF03112A), Color(0xFF02091A)))

/**
 * Écran A2M TUN générique utilisé pendant la reconstruction des pages
 * Serveurs / Routage / Réglages / Journal (étapes 2 à 6).
 */
@Composable
fun A2MPlaceholderScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2M_PLACEHOLDER_BG)
            .padding(scaffoldPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "A2M TUN",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF12E7FF),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFF4F8FF),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9DB8DF),
                textAlign = TextAlign.Center,
            )
        }
    }
}
