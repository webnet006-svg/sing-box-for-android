package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.compose.base.rememberApplyServiceChangeNotifier
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings

/**
 * Sous-écrans Réglages supplémentaires (transplant ONFAA, adapté au réel) :
 * Anti-censorship, Modes avancés, Apparence, À propos.
 */

@Composable
private fun A2MExtraSubScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Column(
        modifier =
        Modifier
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

// ── Anti-censorship (persisté, non raccordé au moteur : notice explicite) ───

@Composable
fun A2MAntiCensorshipScreen(
    serviceStatus: Status,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    var sniEnabled by remember { mutableStateOf(Settings.sniEnabled) }
    var tlsFragment by remember { mutableStateOf(Settings.tlsFragment) }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            A2MExtraSubScreenHeader(
                title = "Anti-censorship",
                subtitle = "TLS, SNI et fragmentation",
                onBack = onBack,
            )

            A2MSettingsGroup(title = "TLS & SNI", icon = Icons.Filled.Security) {
                A2MSettingSwitchRow(
                    icon = Icons.Filled.Security,
                    title = "Optimiser TLS & SNI",
                    subtitle = "Ajuste le SNI présenté au serveur",
                    checked = sniEnabled,
                    onCheckedChange = {
                        sniEnabled = it
                        Settings.sniEnabled = it
                    },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingSwitchRow(
                    icon = Icons.Filled.Bolt,
                    title = "Fragmentation TLS",
                    subtitle = "Découpe la poignée de main TLS",
                    iconTint = A2MColors.AccentUpload,
                    checked = tlsFragment,
                    onCheckedChange = {
                        tlsFragment = it
                        Settings.tlsFragment = it
                    },
                )
            }

            A2MInfoCard(
                title = "Option mémorisée, non raccordée",
                message =
                "La fragmentation TLS et l'optimisation SNI sont enregistrées dans l'application " +
                    "mais ne modifient pas encore la configuration du tunnel. Aucune protection " +
                    "supplémentaire n'est annoncée à tort : ces options seront raccordées au moteur " +
                    "dans une phase ultérieure.",
                icon = Icons.Filled.Info,
            )
        }
    }
}

// ── Modes avancés (bloqueur réel · gaming mémorisé) ─────────────────────────

@Composable
fun A2MAdvancedModesScreen(
    serviceStatus: Status,
    onBack: () -> Unit,
    onOpenEngine: () -> Unit,
    onOpenHotspot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)
    var adBlock by remember { mutableStateOf(Settings.adBlockEnabled) }
    var gamingMode by remember { mutableStateOf(Settings.gamingMode) }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            A2MExtraSubScreenHeader(
                title = "Modes avancés",
                subtitle = "Blocage, jeu et partage",
                onBack = onBack,
            )

            A2MSettingsGroup(title = "Protection", icon = Icons.Filled.Shield) {
                A2MSettingSwitchRow(
                    icon = Icons.Filled.Shield,
                    title = "Ad blocker",
                    subtitle = "Bloque les domaines publicitaires connus (geosite ads-all). Redémarrage du tunnel requis.",
                    checked = adBlock,
                    onCheckedChange = {
                        adBlock = it
                        Settings.adBlockEnabled = it
                        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                    },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingSwitchRow(
                    icon = Icons.Filled.SportsEsports,
                    title = "Mode gaming",
                    subtitle = "Priorité aux jeux",
                    iconTint = A2MColors.Amber,
                    checked = gamingMode,
                    onCheckedChange = {
                        gamingMode = it
                        Settings.gamingMode = it
                    },
                )
            }

            A2MSettingsGroup(title = "Moteur", icon = Icons.Filled.Memory) {
                A2MSettingRow(
                    icon = Icons.Filled.Memory,
                    title = "Moteur réseau",
                    subtitle = "MTU, mux, pile TUN",
                    onClick = onOpenEngine,
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.WifiTethering,
                    title = "Partage hotspot",
                    subtitle = if (Settings.hotspotProxyEnabled) "Activé" else "Désactivé",
                    onClick = onOpenHotspot,
                )
            }

            A2MSettingsNotice(
                "Le bloqueur de publicités est réellement appliqué au routage du tunnel. " +
                    "Le mode gaming est mémorisé mais n'est pas encore raccordé au moteur : " +
                    "aucun effet n'est simulé.",
            )
        }
    }
}

// ── Apparence (thèmes réels, appliqués à toute l'interface) ─────────────────

@Composable
fun A2MAppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val settings = A2MThemeState.settings
    val lightPalettes = A2MThemeCatalog.palettes.filter { !it.isDark }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            A2MExtraSubScreenHeader(
                title = "Apparence",
                subtitle = "Thèmes et mode d'affichage",
                onBack = onBack,
            )

            A2MSettingsGroup(title = "Mode d'affichage", icon = Icons.Filled.Palette) {
                Column(modifier = Modifier.padding(14.dp)) {
                    A2MThemeModeSelector(
                        selected = settings.mode,
                        onSelect = { A2MThemeState.setMode(it) },
                    )
                }
            }

            A2MSettingsGroup(title = "Thème", icon = Icons.Filled.Palette) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    lightPalettes.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { palette ->
                                Box(modifier = Modifier.weight(1f)) {
                                    A2MThemePreviewCard(
                                        palette = palette,
                                        selected = palette.id == settings.paletteId,
                                        onClick = { A2MThemeState.setPalette(palette.id) },
                                    )
                                }
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            A2MInfoCard(
                title = "Thème actuel",
                message =
                "${lightPalettes.firstOrNull { it.id == settings.paletteId }?.label ?: settings.paletteId}" +
                    " · mode ${settings.mode.label.lowercase()}. Le changement est appliqué " +
                    "immédiatement à toute l'interface et mémorisé.",
                icon = Icons.Filled.Palette,
            )

            A2MSettingsNotice(
                "Le mode « Sombre » applique la variante sombre de la palette choisie. " +
                    "Le mode « Système » suit la configuration Android au prochain lancement " +
                    "de l'application.",
            )
        }
    }
}

// ── À propos (données réelles uniquement) ───────────────────────────────────

@Composable
fun A2MAboutScreen(
    serviceStatus: Status,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val coreVersion = remember { runCatching { Libbox.version() }.getOrDefault("—") }
    var serviceMode by remember { mutableStateOf(ServiceMode.VPN) }
    LaunchedEffect(Unit) {
        serviceMode =
            withContext(Dispatchers.IO) {
                runCatching { Settings.detectServiceMode() }.getOrDefault(ServiceMode.VPN)
            }
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            A2MExtraSubScreenHeader(
                title = "À propos",
                subtitle = "Version et moteur",
                onBack = onBack,
            )

            A2MSettingsGroup(title = "Application", icon = Icons.Filled.Info) {
                AboutRow("Nom", "A2M TUN")
                A2MDividerLine(inset = 14.dp)
                AboutRow("Version", BuildConfig.VERSION_NAME)
                A2MDividerLine(inset = 14.dp)
                AboutRow("Mode de service", if (serviceMode == ServiceMode.VPN) "VPN (TUN)" else "Proxy local")
            }

            A2MSettingsGroup(title = "Moteur", icon = Icons.Filled.Bolt) {
                AboutRow("Noyau", coreVersion)
                A2MDividerLine(inset = 14.dp)
                AboutRow("Plateforme", "Android ${android.os.Build.VERSION.RELEASE ?: "—"}")
            }

            A2MSettingsNotice(
                "A2M TUN s'appuie sur le noyau sing-box pour la connexion VPN. " +
                    "Le tunnel n'exécute aucune action silencieuse : chaque réglage activé " +
                    "est appliqué de manière traçable.",
            )
        }
    }
}

@Composable
private fun AboutRow(
    title: String,
    value: String,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = A2MColors.Text,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = A2MColors.Muted,
        )
    }
}
