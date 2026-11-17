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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.compose.base.rememberApplyServiceChangeNotifier
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.navigation.A2MRoutes
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.provider.Settings as AndroidSettings

/**
 * Écran Paramètres A2M TUN — transplant du système ONFAA, adapté au moteur réel.
 *
 * Règle d'honnêteté adaptée au projet : les options déjà câblées au moteur
 * (notification dynamique, bloqueur, DNS, MTU, mux, hotspot, routage, logs)
 * restent réelles ; les options non raccordées (fragmentation TLS, SNI, gaming,
 * reconnexion auto) sont persistées et affichées avec une notice explicite —
 * aucun effet n'est simulé.
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
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)

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

    // Options mémorisées (réelles + persistées) lues à chaque recomposition.
    var adBlock by remember { mutableStateOf(Settings.adBlockEnabled) }

    // Entrées de recherche (façon ONFAA : 12 destinations réelles).
    data class Entry(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String, val keywords: List<String>)

    val entries =
        listOf(
            Entry("Connexion", "Vitesse, reconnexion, timeout", Icons.Filled.Bolt, A2MRoutes.SETTINGS_CONNECTION, listOf("connexion", "notification", "timeout", "reconnexion", "arrière-plan", "vitesse")),
            Entry("Anti-censorship", "TLS, SNI, fragmentation", Icons.Filled.Security, A2MRoutes.SETTINGS_ANTI_CENSORSHIP, listOf("anti", "censure", "tls", "sni", "fragmentation")),
            Entry("Modes avancés", "Ad blocker, gaming", Icons.Filled.SportsEsports, A2MRoutes.SETTINGS_ADVANCED, listOf("avancé", "avance", "ad", "bloqueur", "publicité", "gaming", "jeux")),
            Entry("DNS", "DoH, serveur DNS", Icons.Filled.Dns, A2MRoutes.SETTINGS_DNS, listOf("dns", "doh", "résolution", "serveur")),
            Entry("Routage", "Applications et réseaux", Icons.Filled.Route, A2MRoutes.ROUTING, listOf("routage", "applications", "réseaux", "per", "app")),
            Entry("Moteur réseau", "MTU, mux, pile TUN", Icons.Filled.Memory, A2MRoutes.SETTINGS_ENGINE, listOf("moteur", "mtu", "mux", "pile", "tun", "gvisor")),
            Entry("Partage hotspot", "Proxy pour le partage", Icons.Filled.WifiTethering, A2MRoutes.SETTINGS_HOTSPOT, listOf("hotspot", "partage", "proxy", "socks")),
            Entry("IP Finder", "Trouver une IP du réseau mobile", Icons.Filled.NetworkCheck, A2MRoutes.IP_FINDER, listOf("ip", "finder", "mode", "avion", "recherche", "réseau")),
            Entry("Apparence", "Thèmes et mode d'affichage", Icons.Filled.Palette, A2MRoutes.APPEARANCE, listOf("apparence", "thème", "theme", "couleur", "palette", "sombre", "clair")),
            Entry("Automatisation", "Deep links, connexion", Icons.Filled.Link, A2MRoutes.SETTINGS_AUTOMATION, listOf("automatisation", "liens", "deep", "a2mtun")),
            Entry("Diagnostics", "Pourquoi la connexion échoue", Icons.Filled.BugReport, A2MRoutes.SETTINGS_DIAGNOSTICS, listOf("diagnostic", "panne", "échec", "echec", "vérifications")),
            Entry("À propos", "Version et moteur", Icons.Filled.Info, A2MRoutes.SETTINGS_ABOUT, listOf("propos", "version", "moteur", "application")),
            Entry("Journal", "Événements réels du moteur", Icons.Filled.Description, A2MRoutes.JOURNAL, listOf("journal", "log", "événements", "evenements")),
        )

    val filtered =
        if (searchQuery.trim().isEmpty()) {
            emptyList()
        } else {
            entries.filter { entry ->
                searchQuery.trim().split(" ").all { word ->
                    word.isEmpty() ||
                        entry.title.contains(word, ignoreCase = true) ||
                        entry.subtitle.contains(word, ignoreCase = true) ||
                        entry.keywords.any { it.contains(word, ignoreCase = true) }
                }
            }
        }

    LazyColumn(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Paramètres",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
                Text(
                    text = "Connexion, réseau et interface",
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2MColors.Muted,
                )
            }
        }

        item {
            A2MSettingsSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )
        }

        // ── Résultats de recherche ──────────────────────────────────────
        if (searchQuery.trim().isNotEmpty()) {
            item {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(A2MShapes.card)
                        .background(A2MColors.CardBrush)
                        .border(1.dp, A2MColors.CardBorder, A2MShapes.card),
                ) {
                    if (filtered.isEmpty()) {
                        Text(
                            text = "Aucun réglage ne correspond à « ${searchQuery.trim()} ».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = A2MColors.Muted,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        filtered.forEachIndexed { index, entry ->
                            if (index > 0) A2MDividerLine(inset = 14.dp)
                            A2MSettingRow(
                                icon = entry.icon,
                                title = entry.title,
                                subtitle = entry.subtitle,
                                onClick = { onNavigate(entry.route) },
                            )
                        }
                    }
                }
            }
        }

        if (searchQuery.trim().isNotEmpty()) return@LazyColumn

        // ── ID de l'appareil (identifiant réel, copiable) ───────────────
        item {
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

        // ── Connexion ───────────────────────────────────────────────────
        item {
            A2MSettingsGroup(title = "Connexion", icon = Icons.Filled.Shield) {
                A2MSettingSwitchRow(
                    icon = Icons.Filled.Bolt,
                    title = "Vitesse dans la notification",
                    subtitle = "Affiche le débit réel dans la notification du tunnel",
                    checked = Settings.dynamicNotification,
                    onCheckedChange = { Settings.dynamicNotification = it },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Shield,
                    title = "Timeout de connexion",
                    subtitle = null,
                    iconTint = A2MColors.Accent,
                    value = "${Settings.connectionTimeoutSec} s",
                    onClick = { onNavigate(A2MRoutes.SETTINGS_CONNECTION) },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Bolt,
                    title = "Délai de reconnexion",
                    subtitle = null,
                    iconTint = A2MColors.Accent,
                    value = "${Settings.reconnectDelaySec} s",
                    onClick = { onNavigate(A2MRoutes.SETTINGS_CONNECTION) },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Shield,
                    title = "Comportement en arrière-plan",
                    subtitle = null,
                    iconTint = A2MColors.Accent,
                    value = backgroundBehaviorLabel(Settings.backgroundBehavior),
                    onClick = { onNavigate(A2MRoutes.SETTINGS_CONNECTION) },
                )
            }
        }

        // ── Anti-censorship ─────────────────────────────────────────────
        item {
            A2MSettingsGroup(title = "Anti-censorship", icon = Icons.Filled.Security) {
                A2MSettingRow(
                    icon = Icons.Filled.Security,
                    title = "Paramètres TLS & SNI",
                    subtitle = if (Settings.sniEnabled) "Optimisés" else "Désactivés",
                    iconTint = A2MColors.AccentUpload,
                    onClick = { onNavigate(A2MRoutes.SETTINGS_ANTI_CENSORSHIP) },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Bolt,
                    title = "Fragmentation TLS",
                    subtitle = if (Settings.tlsFragment) "Activée" else "Désactivée",
                    iconTint = A2MColors.AccentUpload,
                    onClick = { onNavigate(A2MRoutes.SETTINGS_ANTI_CENSORSHIP) },
                )
            }
        }

        // ── Modes avancés ───────────────────────────────────────────────
        item {
            A2MSettingsGroup(title = "Modes avancés", icon = Icons.Filled.SportsEsports) {
                A2MSettingSwitchRow(
                    icon = Icons.Filled.Shield,
                    title = "Ad blocker",
                    subtitle = "Bloque les publicités et traqueurs (routage réel, redémarrage du tunnel)",
                    checked = adBlock,
                    onCheckedChange = {
                        adBlock = it
                        Settings.adBlockEnabled = it
                        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                    },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.SportsEsports,
                    title = "Mode gaming",
                    subtitle = "Priorité aux jeux",
                    iconTint = A2MColors.Amber,
                    value = if (Settings.gamingMode) "Activé" else "Désactivé",
                    onClick = { onNavigate(A2MRoutes.SETTINGS_ADVANCED) },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Memory,
                    title = "Réglages avancés",
                    subtitle = "MTU, mux, moteur réseau",
                    iconTint = A2MColors.Accent,
                    onClick = { onNavigate(A2MRoutes.SETTINGS_ENGINE) },
                )
            }
        }

        // ── Réseau ──────────────────────────────────────────────────────
        item {
            A2MSettingsGroup(title = "Réseau", icon = Icons.Filled.Dns) {
                A2MSettingRow(
                    icon = Icons.Filled.Dns,
                    title = "DNS",
                    subtitle =
                    if (Settings.dnsOverrideEnabled) {
                        "Personnalisé · ${Settings.dnsRemoteServer}"
                    } else if (dnsServers.isEmpty()) {
                        "Aucun serveur déclaré"
                    } else {
                        "${dnsServers.first()}${if (dnsServers.size > 1) " + ${dnsServers.size - 1} autre(s)" else ""}"
                    },
                    iconTint = Color(0xFF2EAF6F),
                    onClick = { onNavigate(A2MRoutes.SETTINGS_DNS) },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Route,
                    title = "Routage",
                    subtitle = "Par application · groupes de sortie",
                    iconTint = Color(0xFF2EAF6F),
                    onClick = { onNavigate(A2MRoutes.ROUTING) },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Memory,
                    title = "Moteur réseau",
                    subtitle = "MTU ${mtuSummary()} · Mux ${if (Settings.engineMuxEnabled) "on" else "off"}",
                    iconTint = Color(0xFFE8A13A),
                    onClick = { onNavigate(A2MRoutes.SETTINGS_ENGINE) },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.WifiTethering,
                    title = "Partage hotspot",
                    subtitle =
                    if (Settings.hotspotProxyEnabled) {
                        "Activé · port ${Settings.hotspotProxyPort}"
                    } else {
                        "Désactivé"
                    },
                    iconTint = Color(0xFF3D6EF7),
                    onClick = { onNavigate(A2MRoutes.SETTINGS_HOTSPOT) },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.NetworkCheck,
                    title = "IP Finder",
                    subtitle = "Trouver une IP précise du réseau mobile",
                    iconTint = Color(0xFF3D6EF7),
                    onClick = { onNavigate(A2MRoutes.IP_FINDER) },
                )
            }
        }

        // ── Interface ───────────────────────────────────────────────────
        item {
            val themeSettings = A2MThemeState.settings
            A2MSettingsGroup(title = "Interface", icon = Icons.Filled.Palette) {
                A2MSettingRow(
                    icon = Icons.Filled.Palette,
                    title = "Apparence",
                    subtitle =
                    "${A2MThemeCatalog.palettes.firstOrNull { it.id == themeSettings.paletteId }?.label ?: themeSettings.paletteId}" +
                        " · mode ${themeSettings.mode.label.lowercase()}",
                    iconTint = A2MColors.Accent,
                    onClick = { onNavigate(A2MRoutes.APPEARANCE) },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Description,
                    title = "Journaux",
                    subtitle = "Événements réels et diagnostic",
                    iconTint = Color(0xFF5B6470),
                    onClick = { onNavigate(A2MRoutes.JOURNAL) },
                )
            }
        }

        // ── Plus ────────────────────────────────────────────────────────
        item {
            A2MSettingsGroup(title = "Plus", icon = Icons.Filled.Info) {
                A2MSettingRow(
                    icon = Icons.Filled.Link,
                    title = "Automatisation",
                    subtitle = "Deep links a2mtun:// et connexion",
                    iconTint = A2MColors.Muted,
                    onClick = { onNavigate(A2MRoutes.SETTINGS_AUTOMATION) },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.BugReport,
                    title = "Diagnostics",
                    subtitle = "Vérifications réelles de l'environnement",
                    iconTint = A2MColors.Danger,
                    onClick = { onNavigate(A2MRoutes.SETTINGS_DIAGNOSTICS) },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Info,
                    title = "À propos",
                    subtitle = "Version ${
                        BuildConfig.VERSION_NAME
                    } · moteur ${coreVersion}",
                    iconTint = A2MColors.Muted,
                    onClick = { onNavigate(A2MRoutes.SETTINGS_ABOUT) },
                )
            }
        }

        // ── Notice d'honnêteté (adaptée : la plupart des réglages sont réels) ──
        item {
            A2MSettingsNotice(
                "Les réglages d'interface et de moteur affichés ici sont actifs et mémorisés. " +
                    "Trois options avancées (fragmentation TLS, SNI optimisé, mode gaming, reconnexion " +
                    "automatique) sont mémorisées mais ne sont pas encore appliquées au tunnel : " +
                    "aucun effet n'est simulé, elles porteront la mention « non raccordé ».",
            )
        }
    }
}

private fun mtuSummary(): String =
    when (Settings.engineMtuMode) {
        "auto" -> "auto"
        "custom" -> "${Settings.engineMtuCustom}"
        else -> Settings.engineMtuMode
    }

/** Champ de recherche compact, style pilule. */
@Composable
internal fun A2MSettingsSearchField(
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
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = A2MColors.Text),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(A2MColors.Accent),
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
