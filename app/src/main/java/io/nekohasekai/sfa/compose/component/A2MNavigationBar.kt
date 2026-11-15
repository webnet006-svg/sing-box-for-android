package io.nekohasekai.sfa.compose.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.compose.navigation.A2MRoutes

private data class A2MNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val A2M_NAV_ITEMS =
    listOf(
        A2MNavItem(route = A2MRoutes.HOME, label = "Accueil", icon = Icons.Filled.Home),
        A2MNavItem(route = A2MRoutes.SERVERS, label = "Serveurs", icon = Icons.Filled.Language),
        A2MNavItem(route = A2MRoutes.ROUTING, label = "Routage", icon = Icons.Filled.SwapVert),
        A2MNavItem(route = A2MRoutes.SETTINGS, label = "Réglages", icon = Icons.Filled.Settings),
    )

@Composable
fun A2MNavigationBar(
    selectedRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
    ) {
        A2M_NAV_ITEMS.forEach { item ->
            NavigationBarItem(
                selected = selectedRoute == item.route,
                onClick = { onNavigate(item.route) },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(text = item.label) },
            )
        }
    }
}
