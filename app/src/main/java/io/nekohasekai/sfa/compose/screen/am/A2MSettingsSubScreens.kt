package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.base.rememberApplyServiceChangeNotifier
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sous-écrans de Réglages, façon SniTunnel : en-tête avec retour,
 * titre plein, sections dédiées. Paramètres réels uniquement.
 */

@Composable
private fun A2MSubScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable(onClick = onBack),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = A2MColors.Accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Réglages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = A2MColors.Accent,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Text,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = A2MColors.Muted,
        )
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

// ── Sous-écran Connexion ────────────────────────────────────────────────────

@Composable
fun A2MSettingsConnectionScreen(
    serviceStatus: Status,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)

    var dynamicNotification by remember { mutableStateOf(Settings.dynamicNotification) }
    var allowBypass by remember { mutableStateOf(Settings.allowBypass) }

    // Options mémorisées dont l'effet moteur n'est pas encore raccordé.
    var timeoutSec by remember { mutableStateOf(Settings.connectionTimeoutSec) }
    var reconnectDelaySec by remember { mutableStateOf(Settings.reconnectDelaySec) }
    var backgroundBehavior by remember { mutableStateOf(Settings.backgroundBehavior) }

    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showDelayDialog by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            A2MSubScreenHeader(
                title = "Connexion",
                subtitle = "Comportement du tunnel",
                onBack = onBack,
            )
        }

        item {
            A2MSettingsGroup(title = "Notifications", icon = Icons.Filled.Bolt) {
                A2MSettingSwitchRow(
                    icon = Icons.Filled.Bolt,
                    title = "Vitesse dans la notification",
                    subtitle = "Affiche le débit réel et l'état du tunnel dans la notification",
                    checked = dynamicNotification,
                    onCheckedChange = {
                        dynamicNotification = it
                        Settings.dynamicNotification = it
                    },
                )
            }
        }

        item {
            A2MSettingsGroup(title = "Reconnexion", icon = Icons.Filled.Shield) {
                A2MSettingSwitchRow(
                    icon = Icons.Filled.Shield,
                    title = "Reconnexion automatique",
                    subtitle = "Réessayer lorsque le tunnel tombe",
                    checked = false,
                    onCheckedChange = { /* Non raccordé : conservé pour l'effet réel à venir */ },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Shield,
                    title = "Timeout de connexion",
                    value = "$timeoutSec s",
                    iconTint = A2MColors.Accent,
                    onClick = { showTimeoutDialog = true },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Bolt,
                    title = "Délai de reconnexion",
                    value = "$reconnectDelaySec s",
                    iconTint = A2MColors.Accent,
                    onClick = { showDelayDialog = true },
                )
            }
        }

        item {
            A2MSettingsGroup(title = "Arrière-plan", icon = Icons.Filled.Shield) {
                A2MSettingRow(
                    icon = Icons.Filled.Shield,
                    title = "Comportement en arrière-plan",
                    value = backgroundBehaviorLabel(backgroundBehavior),
                    iconTint = A2MColors.Accent,
                    onClick = { showBackgroundDialog = true },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingSwitchRow(
                    icon = Icons.Filled.Shield,
                    title = "Autoriser le contournement du VPN",
                    subtitle = "Autorise les applications à ignorer le tunnel (redémarrage requis)",
                    checked = allowBypass,
                    onCheckedChange = {
                        allowBypass = it
                        Settings.allowBypass = it
                        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                    },
                )
            }
        }

        item {
            A2MSettingsNotice(
                "Ces préférences sont enregistrées. Le service VPN actuel reste actif en " +
                    "arrière-plan. La reconnexion automatique, le timeout et le comportement " +
                    "en arrière-plan sont mémorisés mais ne sont pas encore appliqués au " +
                    "cycle de vie du tunnel : aucun effet n'est simulé. La notification " +
                    "dynamique et le contournement VPN sont, eux, réellement appliqués.",
            )
        }
    }

    if (showTimeoutDialog) {
        A2MChoiceDialog(
            title = "Timeout de connexion",
            options = timeoutChoices(),
            selected = timeoutSec.toString(),
            onDismiss = { showTimeoutDialog = false },
            onSelect = { value ->
                timeoutSec = value.toInt()
                Settings.connectionTimeoutSec = value.toInt()
                showTimeoutDialog = false
            },
        )
    }

    if (showDelayDialog) {
        A2MChoiceDialog(
            title = "Délai de reconnexion",
            options = reconnectDelayChoices(),
            selected = reconnectDelaySec.toString(),
            onDismiss = { showDelayDialog = false },
            onSelect = { value ->
                reconnectDelaySec = value.toInt()
                Settings.reconnectDelaySec = value.toInt()
                showDelayDialog = false
            },
        )
    }

    if (showBackgroundDialog) {
        A2MChoiceDialog(
            title = "Comportement en arrière-plan",
            options = backgroundBehaviorChoices(),
            selected = backgroundBehavior,
            onDismiss = { showBackgroundDialog = false },
            onSelect = { value ->
                backgroundBehavior = value
                Settings.backgroundBehavior = value
                showBackgroundDialog = false
            },
        )
    }
}

// ── Sous-écran DNS ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun A2MSettingsDnsScreen(
    serviceStatus: Status,
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)

    var overrideEnabled by remember { mutableStateOf(Settings.dnsOverrideEnabled) }
    var remoteType by remember { mutableStateOf(Settings.dnsRemoteType) }
    var remoteServer by remember { mutableStateOf(Settings.dnsRemoteServer) }
    var bootstrapServer by remember { mutableStateOf(Settings.dnsBootstrapServer) }

    // Serveurs déclarés par la configuration (affichés quand le remplacement est désactivé).
    val uiState by dashboardViewModel.uiState.collectAsState()
    var dnsServers by remember { mutableStateOf<List<String>>(emptyList()) }
    var profileName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.selectedProfileId, uiState.profiles) {
        val profile = uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }
        profileName = profile?.name
        dnsServers =
            withContext(Dispatchers.IO) {
                profile?.let { A2MProfileInfo.dnsServers(it) } ?: emptyList()
            }
    }

    // Écriture + rechargement temporisés (900 ms) pour ne pas recharger à chaque frappe.
    val firstRun = remember { mutableStateOf(true) }
    LaunchedEffect(overrideEnabled, remoteType, remoteServer, bootstrapServer) {
        if (firstRun.value) {
            firstRun.value = false
            return@LaunchedEffect
        }
        Settings.dnsOverrideEnabled = overrideEnabled
        Settings.dnsRemoteType = remoteType
        Settings.dnsRemoteServer = remoteServer.trim()
        Settings.dnsBootstrapServer = bootstrapServer.trim()
        delay(900)
        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
    }

    LazyColumn(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            A2MSubScreenHeader(
                title = "DNS",
                subtitle =
                "Les noms dans le tunnel sont résolus par le DNS distant. " +
                    "Le nom du serveur de configuration est résolu hors du tunnel par le DNS bootstrap.",
                onBack = onBack,
            )
        }

        item { SubSectionLabel("Résolution") }

        item {
            SubScreenCard {
                SettingSwitch(
                    title = "Personnaliser le DNS",
                    subtitle = "Remplace le DNS déclaré par la configuration du serveur",
                    checked = overrideEnabled,
                    onCheckedChange = { overrideEnabled = it },
                )
            }
        }

        if (overrideEnabled) {
            item { SubSectionLabel("Type de DNS distant") }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        A2MRadioCard(
                            title = "DoH",
                            subtitle = "Sur HTTPS. Le plus compatible.",
                            selected = remoteType == "https",
                            onClick = { remoteType = "https" },
                            modifier = Modifier.weight(1f),
                        )
                        A2MRadioCard(
                            title = "DoT",
                            subtitle = "TLS sur le port 853.",
                            selected = remoteType == "tls",
                            onClick = { remoteType = "tls" },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        A2MRadioCard(
                            title = "DoQ",
                            subtitle = "QUIC. Faible latence.",
                            selected = remoteType == "quic",
                            onClick = { remoteType = "quic" },
                            modifier = Modifier.weight(1f),
                        )
                        A2MRadioCard(
                            title = "UDP",
                            subtitle = "Non chiffré. Le plus rapide.",
                            selected = remoteType == "udp",
                            onClick = { remoteType = "udp" },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item { SubSectionLabel("Serveur DNS distant") }

            item {
                SubScreenCard {
                    Text(
                        text = "Serveur DNS distant",
                        style = MaterialTheme.typography.labelMedium,
                        color = A2MColors.Muted,
                    )
                    A2MOutlinedField(
                        value = remoteServer,
                        onValueChange = { remoteServer = it },
                        placeholder = "1.1.1.1",
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Cloudflare" to "1.1.1.1",
                            "Google" to "8.8.8.8",
                            "Quad9" to "9.9.9.9",
                            "AdGuard" to "94.140.14.14",
                        ).forEach { (name, address) ->
                            A2MQuickChip(
                                label = name,
                                selected = remoteServer.trim() == address,
                                onClick = { remoteServer = address },
                            )
                        }
                    }
                }
            }

            item { SubSectionLabel("DNS bootstrap (direct)") }

            item {
                SubScreenCard {
                    Text(
                        text = "DNS bootstrap (direct)",
                        style = MaterialTheme.typography.labelMedium,
                        color = A2MColors.Muted,
                    )
                    A2MOutlinedField(
                        value = bootstrapServer,
                        onValueChange = { bootstrapServer = it },
                        placeholder = "1.1.1.1",
                    )
                    Text(
                        text =
                        "Résout uniquement le nom du serveur de configuration, hors du tunnel.\n\n" +
                            "Le DNS distant résout à travers le proxy ; le DNS bootstrap résout par la connexion directe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = A2MColors.Muted,
                    )
                }
            }
        } else {
            item { SubSectionLabel("Serveurs déclarés") }

            item {
                SubScreenCard {
                    if (dnsServers.isEmpty()) {
                        Text(
                            text = "Aucun serveur DNS déclaré" + (profileName?.let { " par « $it »" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = A2MColors.Muted,
                        )
                    } else {
                        Text(
                            text = "Config : ${profileName ?: "—"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = A2MColors.Accent,
                        )
                        dnsServers.forEachIndexed { index, server ->
                            if (index > 0) HorizontalDivider(color = A2MColors.CardBorder)
                            Text(
                                text = server,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = A2MColors.Text,
                                modifier = Modifier.padding(top = if (index == 0) 8.dp else 10.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Les changements s'appliquent au prochain démarrage ou rechargement du tunnel.",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Faint,
            )
        }
    }
}

// ── Sous-écran Partage par point d'accès ────────────────────────────────────

@Composable
fun A2MSettingsHotspotScreen(
    serviceStatus: Status,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)

    var enabled by remember { mutableStateOf(Settings.hotspotProxyEnabled) }
    var portText by remember { mutableStateOf(Settings.hotspotProxyPort.toString()) }

    val firstRun = remember { mutableStateOf(true) }
    LaunchedEffect(enabled, portText) {
        if (firstRun.value) {
            firstRun.value = false
            return@LaunchedEffect
        }
        Settings.hotspotProxyEnabled = enabled
        portText.toIntOrNull()?.takeIf { it in 1..65535 }?.let { Settings.hotspotProxyPort = it }
        delay(900)
        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
    }

    LazyColumn(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            A2MSubScreenHeader(
                title = "Partage par point d'accès",
                subtitle = "Partage la connexion du tunnel aux appareils de votre point d'accès.",
                onBack = onBack,
            )
        }

        item { SubSectionLabel("Partage") }

        item {
            SubScreenCard {
                SettingSwitch(
                    title = "Partager le VPN par point d'accès",
                    subtitle = "Lance un proxy HTTP et SOCKS5 sur un port à l'intérieur du VPN actif.",
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }
        }

        item { SubSectionLabel("Port") }

        item {
            SubScreenCard {
                Text(
                    text = "Port d'écoute",
                    style = MaterialTheme.typography.labelMedium,
                    color = A2MColors.Muted,
                )
                A2MOutlinedField(
                    value = portText,
                    onValueChange = { portText = it },
                    placeholder = "2080",
                    number = true,
                )
                Text(
                    text =
                    "Les appareils connectés au point d'accès utilisent l'adresse IP de ce téléphone " +
                        "et ce port comme proxy HTTP ou SOCKS5.",
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                )
            }
        }

        item {
            Text(
                text =
                "Ne partagez ces identifiants qu'avec des appareils de confiance. " +
                    "Les applications clientes qui ignorent le proxy système et le trafic QUIC/UDP peuvent le contourner.",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Faint,
            )
        }
    }
}

// ── Sous-écran Automatisation ───────────────────────────────────────────────

@Composable
fun A2MSettingsAutomationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LazyColumn(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            A2MSubScreenHeader(
                title = "Automatisation",
                subtitle =
                "Ouvrez ces liens depuis un navigateur, un raccourci ou une application " +
                    "d'automatisation pour contrôler le tunnel. Connexion et bascule agissent " +
                    "sur la configuration sélectionnée.",
                onBack = onBack,
            )
        }

        item { SubSectionLabel("Tunnel") }

        item {
            SubScreenCard {
                AutomationActionRow(
                    icon = Icons.Filled.Bolt,
                    title = "Connexion",
                    link = "a2mtun://connect",
                    clipboard = clipboard,
                    context = context,
                )
                HorizontalDivider(color = A2MColors.CardBorder)
                AutomationActionRow(
                    icon = Icons.Filled.Close,
                    title = "Déconnexion",
                    link = "a2mtun://disconnect",
                    clipboard = clipboard,
                    context = context,
                )
                HorizontalDivider(color = A2MColors.CardBorder)
                AutomationActionRow(
                    icon = Icons.Filled.Autorenew,
                    title = "Basculer la connexion",
                    link = "a2mtun://toggle",
                    clipboard = clipboard,
                    context = context,
                )
                HorizontalDivider(color = A2MColors.CardBorder)
                AutomationActionRow(
                    icon = Icons.Filled.PhoneAndroid,
                    title = "Ouvrir l'application",
                    link = "a2mtun://open",
                    clipboard = clipboard,
                    context = context,
                )
            }
        }

        item { SubSectionLabel("Importation") }

        item {
            SubScreenCard {
                AutomationActionRow(
                    icon = Icons.Filled.Add,
                    title = "Ajouter une configuration",
                    link = "a2mtun://server/<lien-de-partage>",
                    clipboard = clipboard,
                    context = context,
                )
                HorizontalDivider(color = A2MColors.CardBorder)
                AutomationActionRow(
                    icon = Icons.Filled.RssFeed,
                    title = "Ajouter un abonnement",
                    link = "a2mtun://add/<url-abonnement-https>",
                    clipboard = clipboard,
                    context = context,
                )
                HorizontalDivider(color = A2MColors.CardBorder)
                AutomationActionRow(
                    icon = Icons.Filled.QrCode2,
                    title = "Importer une configuration complète",
                    link = "a2mtun://config/<lien-codé>",
                    clipboard = clipboard,
                    context = context,
                )
            }
        }

        item {
            SubScreenCard {
                listOf(
                    "Sur Android, utilisez Tasker ou toute application capable d'ouvrir un lien.",
                    "Copiez un lien ci-dessus et collez-le, par exemple a2mtun://toggle.",
                ).forEachIndexed { index, step ->
                    if (index > 0) HorizontalDivider(color = A2MColors.CardBorder)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = A2MColors.Accent,
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = A2MColors.Text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationActionRow(
    icon: ImageVector,
    title: String,
    link: String,
    clipboard: ClipboardManager,
    context: android.content.Context,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(A2MColors.Accent.copy(alpha = 0.14f)),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = A2MColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = A2MColors.Text,
            )
            Text(
                text = link,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }
        IconButton(onClick = {
            clipboard.setText(AnnotatedString(link))
            android.widget.Toast.makeText(context, "Copié", android.widget.Toast.LENGTH_SHORT).show()
        }) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copier $title",
                tint = A2MColors.Muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Carte de sous-écran ─────────────────────────────────────────────────────

@Composable
private fun SubScreenCard(
    content: @Composable () -> Unit,
) {
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
        content()
    }
}

// ── Interrupteur partagé avec l'écran Réglages ──────────────────────────────

@Composable
internal fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = A2MColors.Text,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── Sous-écran Moteur réseau ────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun A2MSettingsEngineScreen(
    serviceStatus: Status,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)

    var mtuMode by remember { mutableStateOf(Settings.engineMtuMode) }
    var mtuCustom by remember { mutableStateOf(Settings.engineMtuCustom) }
    var mtuText by remember { mutableStateOf(Settings.engineMtuCustom.toString()) }
    var stackMode by remember { mutableStateOf(Settings.engineStackMode) }
    var muxEnabled by remember { mutableStateOf(Settings.engineMuxEnabled) }
    var logLevel by remember { mutableStateOf(Settings.engineLogLevel) }

    // Écriture + rechargement temporisés (900 ms).
    val firstRun = remember { mutableStateOf(true) }
    LaunchedEffect(mtuMode, mtuCustom, stackMode, muxEnabled, logLevel) {
        if (firstRun.value) {
            firstRun.value = false
            return@LaunchedEffect
        }
        Settings.engineMtuMode = mtuMode
        Settings.engineMtuCustom = mtuCustom
        Settings.engineStackMode = stackMode
        Settings.engineMuxEnabled = muxEnabled
        Settings.engineLogLevel = logLevel
        delay(900)
        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
    }

    val effectiveMtu =
        when (mtuMode) {
            "1280" -> "1280"
            "1500" -> "1500"
            "9000" -> "9000"
            "custom" -> if (mtuCustom > 0) mtuCustom.toString() else "—"
            else -> "Auto (profil)"
        }

    LazyColumn(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            A2MSubScreenHeader(
                title = "Moteur réseau",
                subtitle = "Comment le noyau du tunnel transporte les paquets.",
                onBack = onBack,
            )
        }

        item {
            SubScreenCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "MTU effectif",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = A2MColors.Faint,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = effectiveMtu,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = A2MColors.Text,
                            modifier = Modifier.weight(1f),
                        )
                        A2MBadge(
                            text = if (stackMode == "auto") "pile : auto" else "pile : $stackMode",
                            accent = stackMode != "auto",
                        )
                        A2MBadge(text = if (muxEnabled) "mux : on" else "mux : off")
                    }
                }
            }
        }

        item { SubSectionLabel("Préréglage MTU") }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    A2MRadioCard("Auto", "Réglé par le réseau", mtuMode == "auto", { mtuMode = "auto" }, Modifier.weight(1f))
                    A2MRadioCard("Compatible", "1280", mtuMode == "1280", { mtuMode = "1280" }, Modifier.weight(1f))
                    A2MRadioCard("Standard", "1500", mtuMode == "1500", { mtuMode = "1500" }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    A2MRadioCard("Élevé", "9000", mtuMode == "9000", { mtuMode = "9000" }, Modifier.weight(1f))
                    A2MRadioCard(
                        "Personnalisé",
                        mtuText.ifBlank { "—" },
                        mtuMode == "custom",
                        { mtuMode = "custom" },
                        Modifier.weight(1f),
                    )
                }
                if (mtuMode == "custom") {
                    SubScreenCard {
                        Text(
                            text = "MTU personnalisé",
                            style = MaterialTheme.typography.labelMedium,
                            color = A2MColors.Muted,
                        )
                        A2MOutlinedField(
                            value = mtuText,
                            onValueChange = { value ->
                                mtuText = value
                                value.toIntOrNull()?.let { mtuCustom = it }
                            },
                            placeholder = "1500",
                            number = true,
                        )
                    }
                }
            }
        }

        item { SubSectionLabel("Pile TUN") }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                A2MSegmented(
                    options =
                    listOf(
                        "auto" to "Auto",
                        "gvisor" to "gVisor",
                        "mixed" to "Mixed",
                        "system" to "System",
                    ),
                    selectedId = stackMode,
                    onSelect = { stackMode = it },
                )
                Text(
                    text =
                    "gVisor est le choix le plus stable sur la plupart des réseaux. " +
                        "System économise la batterie mais peut mal se comporter sur certains opérateurs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                )
            }
        }

        item { SubSectionLabel("Multiplexage") }

        item {
            SubScreenCard {
                SettingSwitch(
                    title = "Multiplexage",
                    subtitle =
                    "Transporte plusieurs connexions sur une seule liaison du tunnel, ce qui peut " +
                        "réduire la latence à l'ouverture de nombreuses requêtes. " +
                        "Laissez désactivé si votre serveur ne le supporte pas.",
                    checked = muxEnabled,
                    onCheckedChange = { muxEnabled = it },
                )
            }
        }

        item { SubSectionLabel("Niveau de journal") }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("auto", "off", "error", "warn", "info", "debug", "trace").forEach { level ->
                        A2MQuickChip(
                            label = if (level == "auto") "Auto" else level,
                            selected = logLevel == level,
                            onClick = { logLevel = level },
                        )
                    }
                }
                Text(
                    text = "Auto conserve la valeur du profil. Le niveau trace est très verbeux.",
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                )
            }
        }

        item {
            Text(
                text = "Les changements s'appliquent au prochain démarrage ou rechargement du tunnel.",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Faint,
            )
        }
    }
}
