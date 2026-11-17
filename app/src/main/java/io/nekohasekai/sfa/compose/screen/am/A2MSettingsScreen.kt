package io.nekohasekai.sfa.compose.screen.am

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.compose.navigation.A2MRoutes
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.provider.Settings as AndroidSettings

/**
 * Réglages A2M TUN, façon SniTunnel : recherche, ID d'appareil, sections
 * avec tuiles colorées et sous-écrans. Paramètres réels uniquement.
 */
@Composable
fun A2MSettingsScreen(
    serviceStatus: Status,
    onNavigate: (String) -> Unit = {},
    dashboardViewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val context = LocalContext.current

    var serviceMode by remember { mutableStateOf(ServiceMode.VPN) }
    LaunchedEffect(serviceStatus) {
        serviceMode =
            withContext(Dispatchers.IO) {
                io.nekohasekai.sfa.database.Settings.detectServiceMode()
            }
    }
    val coreVersion = remember { runCatching { Libbox.version() }.getOrDefault("—") }

    // ID d'appareil réel (ANDROID_ID) pour la carte « ID de cet appareil ».
    val deviceId = remember {
        AndroidSettings.Secure.getString(context.contentResolver, AndroidSettings.Secure.ANDROID_ID) ?: "—"
    }

    // DNS réellement déclaré par la config active (pour le sous-titre de la ligne DNS).
    val uiState by dashboardViewModel.uiState.collectAsState()
    var dnsServers by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(uiState.selectedProfileId, uiState.profiles) {
        val profile = uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }
        dnsServers =
            withContext(Dispatchers.IO) {
                profile?.let { A2MProfileInfo.dnsServers(it) } ?: emptyList()
            }
    }

    var searchQuery by remember { mutableStateOf("") }

    // Filtre réel : une ligne est visible si sa recherche correspond.
    fun matches(vararg keywords: String): Boolean {
        val query = searchQuery.trim()
        if (query.isEmpty()) return true
        return keywords.any { it.contains(query, ignoreCase = true) }
    }

    LazyColumn(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Réglages",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
            }
        }

        item {
            A2MSettingsSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )
        }

        item {
            // Carte ID d'appareil (identifiant réel Android, copiable).
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(A2MShapes.card)
                    .background(A2MColors.CardBrush)
                    .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = A2MColors.Accent,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ID de cet appareil",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = A2MColors.Text,
                    )
                    Text(
                        text = deviceId,
                        style = MaterialTheme.typography.bodySmall,
                        color = A2MColors.Muted,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copier l'identifiant",
                    tint = A2MColors.Muted,
                    modifier =
                    Modifier
                        .size(19.dp)
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("A2M device ID", deviceId))
                            Toast.makeText(context, "Identifiant copié", Toast.LENGTH_SHORT).show()
                        },
                )
            }
        }

        if (matches("apparence", "theme", "thème")) {
            item { SubSectionLabel("Apparence") }
            item {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(A2MShapes.card)
                        .background(A2MColors.CardBrush)
                        .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Thème",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = A2MColors.Text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Sombre A2M",
                            style = MaterialTheme.typography.bodyMedium,
                            color = A2MColors.Muted,
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = A2MColors.Faint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        if (matches("connexion", "notification", "tunnel", "protection")) {
            item { SubSectionLabel("Connexion") }
            item {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(A2MShapes.card)
                        .background(A2MColors.CardBrush)
                        .border(1.dp, A2MColors.CardBorder, A2MShapes.card),
                ) {
                    SettingTileRow(
                        icon = Icons.Filled.Shield,
                        tileColor = Color(0xFF3D6EF7),
                        title = "Connexion",
                        subtitle = "Notification : " +
                            (if (io.nekohasekai.sfa.database.Settings.dynamicNotification) "on" else "off") +
                            " · Pub : " +
                            (if (io.nekohasekai.sfa.database.Settings.adBlockEnabled) "on" else "off"),
                        onClick = { onNavigate(A2MRoutes.SETTINGS_CONNECTION) },
                    )
                    if (matches("journal", "log")) {
                        HorizontalDivider(color = A2MColors.CardBorder)
                        SettingTileRow(
                            icon = Icons.Filled.Description,
                            tileColor = Color(0xFF5B6470),
                            title = "Journal",
                            subtitle = "Journaux du moteur en direct",
                            onClick = { onNavigate(A2MRoutes.JOURNAL) },
                        )
                    }
                }
            }
        }

        if (matches("reseau", "réseau", "dns", "routage", "network", "diagnostic", "panne", "échec", "echec")) {
            item { SubSectionLabel("Réseau") }
            item {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(A2MShapes.card)
                        .background(A2MColors.CardBrush)
                        .border(1.dp, A2MColors.CardBorder, A2MShapes.card),
                ) {
                    SettingTileRow(
                        icon = Icons.Filled.Dns,
                        tileColor = Color(0xFF2EAF6F),
                        title = "DNS",
                        subtitle =
                        if (io.nekohasekai.sfa.database.Settings.dnsOverrideEnabled) {
                            "Personnalisé · ${io.nekohasekai.sfa.database.Settings.dnsRemoteServer}"
                        } else if (dnsServers.isEmpty()) {
                            "Aucun serveur déclaré"
                        } else {
                            "${dnsServers.first()}${if (dnsServers.size > 1) " + ${dnsServers.size - 1} autre(s)" else ""}"
                        },
                        onClick = { onNavigate(A2MRoutes.SETTINGS_DNS) },
                    )
                    HorizontalDivider(color = A2MColors.CardBorder)
                    SettingTileRow(
                        icon = Icons.Filled.Tune,
                        tileColor = Color(0xFFE8A13A),
                        title = "Moteur réseau",
                        subtitle = "MTU · Pile TUN · Multiplexage · Journal",
                        onClick = { onNavigate(A2MRoutes.SETTINGS_ENGINE) },
                    )
                    HorizontalDivider(color = A2MColors.CardBorder)
                    SettingTileRow(
                        icon = Icons.Filled.SwapVert,
                        tileColor = Color(0xFF2EAF6F),
                        title = "Routage",
                        subtitle = "Par application · groupes de sortie",
                        onClick = { onNavigate(A2MRoutes.ROUTING) },
                    )
                    HorizontalDivider(color = A2MColors.CardBorder)
                    SettingTileRow(
                        icon = Icons.Filled.BugReport,
                        tileColor = Color(0xFFE05C5C),
                        title = "Diagnostic",
                        subtitle = "Pourquoi la connexion échoue · vérifications réelles",
                        onClick = { onNavigate(A2MRoutes.SETTINGS_DIAGNOSTICS) },
                    )
                }
            }
        }

        if (matches("hotspot", "partage", "automatisation", "automat", "liens")) {
            item { SubSectionLabel("Partage et automatisation") }
            item {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(A2MShapes.card)
                        .background(A2MColors.CardBrush)
                        .border(1.dp, A2MColors.CardBorder, A2MShapes.card),
                ) {
                    SettingTileRow(
                        icon = Icons.Filled.Wifi,
                        tileColor = Color(0xFF3D6EF7),
                        title = "Partage hotspot",
                        subtitle =
                        "Proxy HTTP/SOCKS5 : " +
                            (if (io.nekohasekai.sfa.database.Settings.hotspotProxyEnabled) "on · port " +
                            io.nekohasekai.sfa.database.Settings.hotspotProxyPort else "off"),
                        onClick = { onNavigate(A2MRoutes.SETTINGS_HOTSPOT) },
                    )
                    HorizontalDivider(color = A2MColors.CardBorder)
                    SettingTileRow(
                        icon = Icons.Filled.Bolt,
                        tileColor = Color(0xFF16E0C4),
                        title = "Automatisation",
                        subtitle = "Liens de contrôle a2mtun://",
                        onClick = { onNavigate(A2MRoutes.SETTINGS_AUTOMATION) },
                    )
                }
            }
        }

        if (matches("propos", "version", "application", "moteur", "mode")) {
            item { SubSectionLabel("À propos") }
            item {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(A2MShapes.card)
                        .background(A2MColors.CardBrush)
                        .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingInfo("Nom", "A2M TUN")
                    HorizontalDivider(color = A2MColors.CardBorder)
                    SettingInfo("Version", BuildConfig.VERSION_NAME)
                    HorizontalDivider(color = A2MColors.CardBorder)
                    SettingInfo("Moteur", coreVersion)
                    HorizontalDivider(color = A2MColors.CardBorder)
                    SettingInfo(
                        "Mode",
                        if (serviceMode == ServiceMode.VPN) "VPN (TUN)" else "Proxy local",
                    )
                }
            }
        }
    }
}

@Composable
private fun SubSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = A2MColors.Faint,
        modifier = Modifier.padding(start = 2.dp),
    )
}

@Composable
private fun SettingTileRow(
    icon: ImageVector,
    tileColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Tuile colorée façon SniTunnel.
        Box(
            modifier =
            Modifier
                .size(42.dp)
                .clip(A2MShapes.tile)
                .background(tileColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = A2MColors.Faint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingInfo(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = A2MColors.Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Text,
        )
    }
}

/** Champ de recherche compact, style pilule. */
@Composable
private fun A2MSettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.chip)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.chip)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = A2MColors.Faint,
            modifier = Modifier.size(18.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = A2MColors.Text),
            cursorBrush = SolidColor(A2MColors.Accent),
            modifier = Modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Effacer",
                tint = A2MColors.Faint,
                modifier =
                Modifier
                    .size(16.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}
