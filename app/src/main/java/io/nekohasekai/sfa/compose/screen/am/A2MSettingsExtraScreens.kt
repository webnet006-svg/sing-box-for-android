package io.nekohasekai.sfa.compose.screen.am

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.compose.base.rememberApplyServiceChangeNotifier
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.dams.theme.ThemePackage
import io.nekohasekai.sfa.database.Settings

/**
 * Sous-écrans Réglages supplémentaires, tous raccordés au moteur ou à l'UI :
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

// ── Anti-censorship (fragmentation TLS et SNI réels) ────────────────────────

@Composable
fun A2MAntiCensorshipScreen(
    serviceStatus: Status,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)
    var tlsFragment by remember { mutableStateOf(Settings.tlsFragment) }
    var sniEnabled by remember { mutableStateOf(Settings.sniEnabled) }

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

            A2MSettingsGroup(title = "TLS", icon = Icons.Filled.Security) {
                A2MSettingSwitchRow(
                    icon = Icons.Filled.Security,
                    title = "Fragmentation TLS",
                    subtitle =
                    "Ajoute record_fragment aux sortants TLS : le handshake est découpé " +
                        "en plusieurs enregistrements pour gêner un filtrage SNI simple. " +
                        "Peut réduire légèrement les performances.",
                    checked = tlsFragment,
                    onCheckedChange = {
                        tlsFragment = it
                        Settings.tlsFragment = it
                        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                    },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingSwitchRow(
                    icon = Icons.Filled.Shield,
                    title = "Envoyer le SNI",
                    subtitle =
                    "Lorsque cette option est désactivée, disable_sni est ajouté aux " +
                        "sortants TLS. À ne désactiver que si le serveur et le réseau le supportent.",
                    checked = sniEnabled,
                    onCheckedChange = {
                        sniEnabled = it
                        Settings.sniEnabled = it
                        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                    },
                )
            }

            A2MSettingsNotice(
                "Ces options modifient les objets TLS des sortants de la configuration " +
                    "active. Elles ne concernent que les profils qui utilisent TLS et " +
                    "prennent effet au prochain rechargement du tunnel.",
            )
        }
    }
}

// ── Modes avancés (bloqueur, gaming, moteur) ────────────────────────────────

@Composable
fun A2MAdvancedModesScreen(
    serviceStatus: Status,
    onBack: () -> Unit,
    onOpenEngine: () -> Unit,
    onOpenHotspot: () -> Unit,
    onOpenAntiCensorship: () -> Unit,
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
            }

            A2MSettingsGroup(title = "Performance", icon = Icons.Filled.SportsEsports) {
                A2MSettingSwitchRow(
                    icon = Icons.Filled.SportsEsports,
                    title = "Mode gaming",
                    subtitle =
                    "Active TCP Fast Open et un keepalive court sur les sortants " +
                        "(30 s, intervalle 10 s). Redémarrage du tunnel requis.",
                    checked = gamingMode,
                    onCheckedChange = {
                        gamingMode = it
                        Settings.gamingMode = it
                        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                    },
                )
                A2MDividerLine(inset = 14.dp)
                A2MSettingRow(
                    icon = Icons.Filled.Security,
                    title = "Anti-censorship",
                    subtitle = "Fragmentation TLS et SNI",
                    onClick = onOpenAntiCensorship,
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
                "Le bloqueur de publicités et le mode gaming sont réellement appliqués " +
                    "à la configuration du tunnel. La fragmentation TLS et le contrôle du SNI " +
                    "sont disponibles dans « Anti-censorship ».",
            )
        }
    }
}

// ── Apparence (palettes, thème personnalisé, paquets .a2mtheme) ─────────────

@Composable
fun A2MAppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = A2MThemeState.settings
    val lightPalettes = A2MThemeCatalog.palettes.filter { !it.isDark }
    var clickFeedback by remember { mutableStateOf(Settings.clickFeedback) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var themeMessage by remember { mutableStateOf<String?>(null) }

    // Éditeur : repart du thème enregistré (ou du thème courant) à chaque application.
    val customRevision = A2MThemeState.customRevision
    val editorBase =
        remember(customRevision) {
            A2MThemeCatalog.storedCustom() ?: A2MThemeState.currentPackage()
        }
    var customDark by remember(customRevision) { mutableStateOf(editorBase.dark) }
    var accentHex by remember(customRevision) { mutableStateOf(editorBase.color("accent")) }
    var uploadHex by remember(customRevision) { mutableStateOf(editorBase.color("accentUpload")) }
    val customPreview = remember(customRevision, settings.paletteId) { A2MThemeCatalog.customPalette() }

    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use {
                                it.readBytes().toString(Charsets.UTF_8)
                            }
                        }.getOrNull()
                    }
                val data = text?.let { ThemePackage.decode(it) }
                if (data == null) {
                    themeMessage = "Fichier .a2mtheme illisible ou incomplet : rien n'a été modifié."
                } else {
                    A2MThemeState.applyCustom(data)
                    themeMessage = "Thème « ${data.name} » importé et appliqué."
                }
            }
        }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(ThemePackage.MIME_TYPE),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val data = A2MThemeState.currentPackage()
                val text = ThemePackage.encode(data)
                themeMessage =
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use {
                                it.write(text.toByteArray())
                            } ?: error("flux indisponible")
                        }
                        "Thème « ${data.name} » exporté."
                    } catch (e: Exception) {
                        "Export impossible : ${e.message ?: "erreur inconnue"}"
                    }
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
                title = "Apparence",
                subtitle = "Palettes, mode et thème personnalisé",
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

            A2MSettingsGroup(title = "Palettes", icon = Icons.Filled.Palette) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    (lightPalettes + customPreview).chunked(2).forEach { row ->
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

            A2MSettingsGroup(title = "Personnalisation", icon = Icons.Filled.Palette) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Base du thème",
                        style = MaterialTheme.typography.labelLarge,
                        color = A2MColors.Muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(true to "Sombre", false to "Clair").forEach { (dark, label) ->
                            val isSelected = customDark == dark
                            Box(
                                modifier =
                                Modifier
                                    .weight(1f)
                                    .clip(A2MShapes.chip)
                                    .background(if (isSelected) A2MColors.Accent else A2MColors.Tile)
                                    .border(
                                        1.dp,
                                        if (isSelected) A2MColors.Accent else A2MColors.CardBorder,
                                        A2MShapes.chip,
                                    )
                                    .clickable { customDark = dark }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) androidx.compose.ui.graphics.Color.White else A2MColors.Muted,
                                )
                            }
                        }
                    }
                    Text(
                        text = "Accent principal",
                        style = MaterialTheme.typography.labelLarge,
                        color = A2MColors.Muted,
                    )
                    A2MThemeSwatchGrid(selected = accentHex, onSelect = { accentHex = it })
                    Text(
                        text = "Accent secondaire (téléversements)",
                        style = MaterialTheme.typography.labelLarge,
                        color = A2MColors.Muted,
                    )
                    A2MThemeSwatchGrid(selected = uploadHex, onSelect = { uploadHex = it })
                    OutlinedButton(
                        onClick = {
                            A2MThemeState.applyDerivedCustom(customDark, accentHex, uploadHex)
                            themeMessage = "Palette personnalisée appliquée."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Appliquer cette palette")
                    }
                }
            }

            A2MSettingsGroup(title = "Import / Export", icon = Icons.Filled.Palette) {
                A2MSettingRow(
                    icon = Icons.Filled.Upload,
                    title = "Importer un thème",
                    subtitle = "Fichier .a2mtheme (JSON)",
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"),
                        )
                    },
                )
                HorizontalDivider(color = A2MColors.CardBorder)
                A2MSettingRow(
                    icon = Icons.Filled.Download,
                    title = "Exporter le thème actuel",
                    subtitle = "Enregistrer un fichier .a2mtheme",
                    onClick = {
                        val name =
                            A2MThemeState.currentPackage().name
                                .lowercase()
                                .replace(Regex("[^a-z0-9]+"), "-")
                                .trim('-')
                                .ifBlank { "theme" }
                        exportLauncher.launch("a2m-$name.a2mtheme")
                    },
                )
            }

            A2MSettingsGroup(title = "Retour tactile", icon = Icons.Filled.Palette) {
                A2MSettingRow(
                    icon = Icons.Filled.Info,
                    title = "Retour tactile",
                    subtitle = "Sur chaque clic dans les réglages",
                    value = clickFeedbackLabel(clickFeedback),
                    onClick = { showFeedbackDialog = true },
                )
                if (showFeedbackDialog) {
                    A2MChoiceDialog(
                        title = "Retour tactile",
                        options =
                        listOf(
                            A2MChoiceOption(A2MClickFeedback.SOUND, "Son"),
                            A2MChoiceOption(A2MClickFeedback.VIBRATE, "Vibreur"),
                            A2MChoiceOption(A2MClickFeedback.SILENT, "Silence"),
                        ),
                        selected = clickFeedback,
                        onDismiss = { showFeedbackDialog = false },
                        onSelect = {
                            clickFeedback = it
                            Settings.clickFeedback = it
                            showFeedbackDialog = false
                        },
                    )
                }
            }

            A2MInfoCard(
                title = "Thème actuel",
                message =
                    "${A2MThemeState.currentPalette().label} · mode ${settings.mode.label.lowercase()}. " +
                        "Le changement est appliqué immédiatement à toute l'interface et mémorisé.",
                icon = Icons.Filled.Palette,
            )

            A2MSettingsNotice(
                "Un fichier .a2mtheme ne contient que des couleurs : il est sans danger " +
                    "à partager. Il peut être importé sur un autre appareil pour retrouver " +
                    "exactement le même thème.",
            )
        }
    }

    themeMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { themeMessage = null },
            title = { Text("Apparence", color = A2MColors.Text) },
            text = { Text(message, color = A2MColors.Muted) },
            confirmButton = {
                TextButton(onClick = { themeMessage = null }) {
                    Text("OK", color = A2MColors.Accent, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

/** Grille de pastilles d'accent ; la sélection est cerclée. */
@Composable
private fun A2MThemeSwatchGrid(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val selectedKey = ThemePackage.normalizeHex(selected)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        A2MThemeCatalog.ACCENT_SWATCHES.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { hex ->
                    val isSelected = ThemePackage.normalizeHex(hex) == selectedKey
                    Box(
                        modifier =
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)))
                            .border(
                                if (isSelected) 3.dp else 1.dp,
                                if (isSelected) A2MColors.Text else A2MColors.CardBorder,
                                CircleShape,
                            )
                            .clickable { onSelect(hex) },
                    )
                }
            }
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
