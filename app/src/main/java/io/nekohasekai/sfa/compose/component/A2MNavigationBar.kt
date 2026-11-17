package io.nekohasekai.sfa.compose.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

private val A2M_NAV_ITEMS =
    listOf(
        A2MNavItem(route = A2MRoutes.HOME, label = "Accueil", icon = Icons.Filled.Home),
        A2MNavItem(route = A2MRoutes.SERVERS, label = "Serveurs", icon = Icons.Outlined.Language),
        A2MNavItem(route = A2MRoutes.ROUTING, label = "Routage", icon = Icons.Filled.SwapVert),
        A2MNavItem(route = A2MRoutes.SETTINGS, label = "Réglages", icon = Icons.Filled.Settings),
    )

/**
 * Barre de navigation A2M TUN — style nuit cohérent avec le design system :
 * fond sombre translucide, indicateur discret, libellés compacts.
 */
@Composable
fun A2MNavigationBar(
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = Color(0xF007141C),
        contentColor = A2MColors.Text,
        tonalElevation = 0.dp,
    ) {
        A2M_NAV_ITEMS.forEach { item ->
            val selected = selectedRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor = A2MColors.Accent,
                    selectedTextColor = A2MColors.Accent,
                    indicatorColor = Color(0xFF0E2A33),
                    unselectedIconColor = A2MColors.Faint,
                    unselectedTextColor = A2MColors.Faint,
                ),
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}
