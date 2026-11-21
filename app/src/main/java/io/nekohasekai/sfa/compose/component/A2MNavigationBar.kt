package io.nekohasekai.sfa.compose.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        BoxWithConstraints(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(A2M_NAV_PILL_SHAPE)
                .background(Color(0xE8071A38))
                .border(1.dp, A2MColors.CardBorder, A2M_NAV_PILL_SHAPE)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            val selectedIndex = A2M_NAV_ITEMS.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
            val itemWidth = maxWidth / A2M_NAV_ITEMS.size
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = tween(durationMillis = 320),
                label = "navigationIndicatorOffset",
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .size(width = itemWidth, height = maxHeight)
                    .padding(horizontal = 2.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(A2MColors.Accent.copy(alpha = 0.22f), Color(0xFF287BFF).copy(alpha = 0.18f)),
                        ),
                    )
                    .border(1.dp, A2MColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                A2M_NAV_ITEMS.forEach { item ->
                    val selected = selectedRoute == item.route
                    val tint by animateColorAsState(
                        targetValue = if (selected) A2MColors.Accent else A2MColors.Faint,
                        animationSpec = tween(220),
                        label = "navigationTint",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(A2M_NAV_PILL_SHAPE)
                            .selectable(selected = selected, role = Role.Tab) { onNavigate(item.route) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = item.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = tint,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
