package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.base.rememberApplyServiceChangeNotifier
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
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
    var adBlockEnabled by remember { mutableStateOf(Settings.adBlockEnabled) }

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
                title = "Connexion",
                subtitle = "Démarrage du tunnel et affichage système.",
                onBack = onBack,
            )
        }

        item { SubSectionLabel("Notification") }

        item {
            SubScreenCard {
                SettingSwitch(
                    title = "Notification dynamique",
                    subtitle = "Affiche le débit et l'état du tunnel dans la notification",
                    checked = dynamicNotification,
                    onCheckedChange = {
                        dynamicNotification = it
                        Settings.dynamicNotification = it
                    },
                )
            }
        }

        item { SubSectionLabel("Protection") }

        item {
            SubScreenCard {
                SettingSwitch(
                    title = "Bloqueur de publicités",
                    subtitle = "Bloque les domaines publicitaires connus (geosite ads-all) au niveau du routage. " +
                        "Nécessite un redémarrage du tunnel.",
                    checked = adBlockEnabled,
                    onCheckedChange = {
                        adBlockEnabled = it
                        Settings.adBlockEnabled = it
                        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                    },
                )
            }
        }

        item { SubSectionLabel("Système") }

        item {
            SubScreenCard {
                SettingSwitch(
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
    }
}

// ── Sous-écran DNS (lecture seule, déclaré par la configuration) ────────────

@Composable
fun A2MSettingsDnsScreen(
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
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
                subtitle = "Serveurs déclarés par la configuration active (lecture seule).",
                onBack = onBack,
            )
        }

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

        item {
            Text(
                text = "Le DNS appliqué provient du fichier de configuration du serveur. " +
                    "Pour le changer, importez une configuration différente.",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Faint,
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
