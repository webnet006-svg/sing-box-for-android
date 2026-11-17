package io.nekohasekai.sfa.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekohasekai.sfa.compose.navigation.A2MRoutes
import io.nekohasekai.sfa.compose.screen.am.A2MColors

private data class A2MNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** 3 onglets comme SniTunnel : le Routage et le Journal sont accessibles ailleurs. */
private val A2M_NAV_ITEMS =
    listOf(
        A2MNavItem(route = A2MRoutes.HOME, label = "Connexion", icon = Icons.Filled.Shield),
        A2MNavItem(route = A2MRoutes.SERVERS, label = "Serveurs", icon = Icons.Outlined.Language),
        A2MNavItem(route = A2MRoutes.SETTINGS, label = "Réglages", icon = Icons.Filled.Tune),
    )

private val A2M_NAV_PILL_SHAPE = RoundedCornerShape(999.dp)

/**
 * Barre de navigation A2M TUN — pilule flottante façon SniTunnel :
 * conteneur arrondi sombre translucide, onglet actif = pilule interne teal.
 */
@Composable
fun A2MNavigationBar(
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(A2M_NAV_PILL_SHAPE)
                .background(Color(0xE607141C))
                .border(1.dp, A2MColors.CardBorder, A2M_NAV_PILL_SHAPE)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            A2M_NAV_ITEMS.forEach { item ->
                val selected = selectedRoute == item.route
                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    modifier =
                    Modifier
                        .weight(1f)
                        .clip(A2M_NAV_PILL_SHAPE)
                        .background(if (selected) Color(0xFF0E2A33) else Color.Transparent)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onNavigate(item.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) A2MColors.Accent else A2MColors.Faint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) A2MColors.Accent else A2MColors.Faint,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
