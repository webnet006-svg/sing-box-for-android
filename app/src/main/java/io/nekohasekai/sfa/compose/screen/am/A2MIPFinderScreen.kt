package io.nekohasekai.sfa.compose.screen.am

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * IP Finder (façon Dams Tunnel) : trouver une IP précise du réseau mobile en
 * recyclant le réseau — le mode avion est forcé puis relâché selon un temps
 * précis, dans une boucle définie par l'utilisateur — jusqu'à ce que l'IP
 * obtenue respecte une règle, puis (option) connecter le VPN.
 *
 * Honnêteté : l'IP est lue réellement sur les interfaces de l'appareil ; la
 * bascule du mode avion exige la permission WRITE_SECURE_SETTINGS (via ADB),
 * sinon mode manuel (l'app détecte chaque changement d'IP) ; le rôle
 * Assistant est déclaré pour piloter le mode avion — aucun audio, aucun
 * écran lu ; l'auto-connect passe par le vrai chemin de connexion.
 */

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun A2MIPFinderScreen(
    serviceStatus: Status,
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scaffoldPadding = LocalScaffoldPadding.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var rulesText by remember { mutableStateOf(Settings.ipFinderRules.ifBlank { DEFAULT_IP_RULES }) }
    var cyclesText by remember { mutableStateOf(Settings.ipFinderCycles.toString()) }
    var pauseText by remember { mutableStateOf(Settings.ipFinderPauseSec.toString()) }
    var autoConnect by remember { mutableStateOf(Settings.ipFinderAutoConnect) }
    var canToggleAirplane by remember {
        mutableStateOf(A2MNetworkInfo.canToggleAirplaneMode(context))
    }
    var currentIp by remember { mutableStateOf<String?>(null) }
    var wifiActive by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var foundIp by remember { mutableStateOf<String?>(null) }
    var observedCount by remember { mutableStateOf(0) }
    var searchStartedAt by remember { mutableStateOf<Long?>(null) }
    var searchDurationSec by remember { mutableStateOf<Long?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var historyVersion by remember { mutableStateOf(0) }
    var diagVersion by remember { mutableStateOf(0) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val rules = remember(rulesText) { effectiveIpRules(rulesText) }
    val ruleProblems = remember(rulesText) { validateIpRules(rulesText) }


    // IP réelle + Wi-Fi : relus toutes les 3 s tant que l'écran est visible.
    LaunchedEffect(Unit) {
        while (true) {
            val ip = withContext(Dispatchers.IO) { A2MNetworkInfo.readCellularIp() }
            if (ip != null) {
                currentIp = ip
            }
            wifiActive = withContext(Dispatchers.IO) { A2MNetworkInfo.isWifiActive() }
            delay(3000)
        }
    }

    // Diagnostic assistant : relu au départ de l'écran (le rôle peut changer
    // pendant que l'utilisateur passe par les réglages d'Android).
    LaunchedEffect(Unit) {
        delay(600)
        diagVersion++
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
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── En-tête ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = A2MColors.Accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Retour",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = A2MColors.Accent,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (canToggleAirplane) {
                    Text(
                        text = "PRÊT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = A2MColors.Accent,
                        modifier =
                        Modifier
                            .clip(A2MShapes.card)
                            .border(1.dp, A2MColors.Accent.copy(alpha = 0.4f), A2MShapes.card)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                text = "A2M TUN / OUTILS RÉSEAU",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Accent,
            )
            Text(
                text = "IP Finder",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )

            // ── IP cellulaire actuelle ─────────────────────────────────────
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(A2MShapes.card)
                    .background(A2MColors.CardBrush)
                    .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ADRESSE IP CELLULAIRE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = A2MColors.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    if (wifiActive) {
                        Text(
                            text = "Wi-Fi actif",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = A2MColors.Text,
                        )
                    }
                }
                Text(
                    text = currentIp ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (currentIp != null) A2MColors.Accent else A2MColors.Faint,
                )
                val matched = currentIp?.let { ip -> rules.firstOrNull { ipMatchesRule(ip, it) } }
                Text(
                    text =
                    if (currentIp == null) {
                        "Aucune adresse cellulaire observée."
                    } else if (rules.isEmpty()) {
                        "Aucune règle définie."
                    } else {
                        "Correspond à une règle : " +
                            (matched?.second?.let { "oui (règle $it)" } ?: "non")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2MColors.Muted,
                )
                Text(
                    text = if (serviceStatus == Status.Started) "VPN A2M actif." else "VPN A2M inactif.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2MColors.Muted,
                )
                if (wifiActive) {
                    Text(
                        text = "Le Wi-Fi est actif ; seule l'adresse cellulaire est évaluée.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(A2MShapes.card)
                            .background(Color(0xFF8B3A3A))
                            .padding(10.dp),
                    )
                }
            }

            if (!canToggleAirplane) {
                A2MSettingsNotice(
                    text =
                    "Bascule automatique du mode avion indisponible : la permission " +
                        "WRITE_SECURE_SETTINGS n'est pas accordée. Mode manuel : basculez " +
                        "le mode avion vous-même pendant la recherche, l'app détecte chaque " +
                        "nouvelle IP et vérifie vos règles.\n\n" +
                        "Commande ADB (une seule fois) :\n" +
                        "adb shell pm grant ${context.packageName} " +
                        "android.permission.WRITE_SECURE_SETTINGS",
                )
            }

            // ── Diagnostic de l'assistant ──────────────────────────────────
            val diag = remember(diagVersion) { AssistantDiagnostics.of(context) }
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(A2MShapes.card)
                    .background(A2MColors.CardBrush)
                    .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Diagnostic de l'assistant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
                AssistantDiagRow("Service déclaré", diag.declared)
                AssistantDiagRow("Reconnaissance déclarée", diag.recognitionDeclared)
                AssistantDiagRow("Service résolu par Android", diag.ourServiceActive)
                AssistantDiagRow("Rôle Assistant actif pour A2M TUN", diag.roleHeld)
                Text(
                    text = "Application assistante actuelle : ${diag.currentLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2MColors.Muted,
                )
                Text(
                    text =
                        if (diag.roleHeld) {
                            "Le geste d'assistant ouvre l'IP Finder. Le rôle Assistant " +
                                "n'accorde pas la permission de basculer le mode avion."
                        } else {
                            "Sélectionnez A2M TUN comme application d'assistance : le geste " +
                                "d'assistant ouvrira alors directement l'IP Finder."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                )
                if (diag.roleHeld) {
                    Text(
                        text = "Assistant A2M TUN actif",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = A2MColors.Accent,
                    )
                }
                A2MFinderButton(
                    label = "Activer comme assistant",
                    enabled = !diag.roleHeld,
                    onClick = {
                        diagVersion++
                        AssistantDiagnostics.requestAssistantRole(context)
                    },
                )
                A2MFinderButton(
                    label = "Ouvrir les réglages d'assistant",
                    outline = true,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(AndroidSettings.ACTION_VOICE_INPUT_SETTINGS))
                        }
                    },
                )

            }

            // ── IP recherchées ─────────────────────────────────────────────
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(A2MShapes.card)
                    .background(A2MColors.CardBrush)
                    .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "IP recherchées",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
                Text(
                    text =
                    "Liste par défaut intégrée (préfixes 10.xx) : remplacez-la " +
                        "ci-dessous par vos propres adresses, même format. " +
                        "^ commence par · \$ se termine par · = exact · sans symbole : " +
                        "contient n'importe où dans l'adresse · séparez par ligne, " +
                        "virgule ou ;",
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                )
                OutlinedTextField(
                    value = rulesText,
                    onValueChange = {
                        rulesText = it
                        Settings.ipFinderRules = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("IP recherchées") },
                    placeholder = { Text("10.204 ; 10.205 ; ^10.21 ; =100.66.7.29") },
                    textStyle =
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    singleLine = false,
                    maxLines = 6,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        text = "Réinitialiser la liste",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = A2MColors.Accent,
                        modifier =
                        Modifier
                            .clickable {
                                rulesText = DEFAULT_IP_RULES
                                Settings.ipFinderRules = DEFAULT_IP_RULES
                            }
                            .padding(vertical = 4.dp),
                    )
                    Text(
                        text = "Préfixer avec ^",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = A2MColors.Accent,
                        modifier =
                        Modifier
                            .clickable {
                                rulesText =
                                    rulesText.split(";;", ";", ",", "\n")
                                        .joinToString(";") { rule ->
                                            val trimmed = rule.trim()
                                            if (trimmed.isEmpty() || trimmed[0] in "^\$=") trimmed
                                            else "^$trimmed"
                                        }
                                Settings.ipFinderRules = rulesText
                            }
                            .padding(vertical = 4.dp),
                    )
                }
                Text(
                    text = "Règles actives : ${rules.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = A2MColors.Text,
                )
                if (ruleProblems.isNotEmpty()) {
                    Text(
                        text = "Corrigez la liste avant de lancer la recherche :",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    ruleProblems.take(4).forEach { problem ->
                        Text(
                            text = "• $problem",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (ruleProblems.size > 4) {
                        Text(
                            text = "• … et ${ruleProblems.size - 4} autre(s) problème(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text(
                        text = "Liste valide : la recherche peut démarrer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = A2MColors.Accent,
                    )
                }

            }

            // ── Réglages de recherche ──────────────────────────────────────
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(A2MShapes.card)
                    .background(A2MColors.CardBrush)
                    .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Réglages de recherche",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = cyclesText,
                        onValueChange = { value ->
                            cyclesText = value.filter { it.isDigit() }.take(4)
                            cyclesText.toIntOrNull()?.let { Settings.ipFinderCycles = it }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Nombre de cycles") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = pauseText,
                        onValueChange = { value ->
                            pauseText = value.filter { it.isDigit() }.take(3)
                            pauseText.toIntOrNull()?.let { Settings.ipFinderPauseSec = it }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Pause entre cycles (s)") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connecter le VPN automatiquement quand l'IP est trouvée",
                            style = MaterialTheme.typography.bodyLarge,
                            color = A2MColors.Text,
                        )
                        Text(
                            text =
                            if (serviceStatus == Status.Stopped) {
                                "Connexion via le vrai chemin (permission VPN incluse)"
                            } else {
                                "VPN déjà actif — reconnexion non déclenchée"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = A2MColors.Muted,
                        )
                    }
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = {
                            autoConnect = it
                            Settings.ipFinderAutoConnect = it
                        },
                    )
                }
            }

            // ── Progression de la recherche ────────────────────────────────
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(A2MShapes.card)
                    .background(A2MColors.CardBrush)
                    .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Progression de la recherche",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = A2MColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Tentative $attempt / ${cyclesText.toIntOrNull() ?: 0}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = A2MColors.Muted,
                    )
                }
                Text(
                    text =
                    "Pendant la recherche, l'application reste active même écran " +
                        "éteint, ce qui peut décharger la batterie. Arrêtez la " +
                        "recherche avec le bouton Arrêter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                )
                if (searching) {
                    statusText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = A2MColors.Accent,
                        )
                    }
                }
                A2MFinderButton(
                    label = if (searching) "Recherche en cours…" else "Trouver des IP",
                    enabled = !searching && ruleProblems.isEmpty(),
                    onClick = {
                        if (searching) return@A2MFinderButton
                        if (ruleProblems.isNotEmpty()) {
                            lastError = "Corrigez les règles d'IP avant de lancer la recherche."
                            return@A2MFinderButton
                        }
                        foundIp = null
                        lastError = null
                        observedCount = 0
                        searchDurationSec = null
                        attempt = 0
                        searchStartedAt = System.currentTimeMillis()
                        statusText = "Cycle 1 — activation du mode avion…"
                        val job =
                            scope.launch {
                                searching = true
                                try {
                                    val found =
                                        runIpSearch(
                                            context = context,
                                            serviceStatus = serviceStatus,
                                            dashboardViewModel = dashboardViewModel,
                                            maxCycles =
                                            cyclesText.toIntOrNull()?.coerceIn(1, 1000) ?: 100,
                                            pauseMs =
                                            (pauseText.toIntOrNull()?.coerceIn(1, 120) ?: 3) *
                                                1000L,
                                            canToggle = { canToggleAirplane },
                                            onAttempt = { attempt = it },
                                            onStatus = { statusText = it },
                                            onIp = { ip -> currentIp = ip },
                                            onFallback = { canToggleAirplane = false },
                                            onObserved = {
                                                observedCount++
                                                historyVersion++
                                            },
                                        )
                                    foundIp = found
                                    statusText =
                                        if (found != null) {
                                            "IP trouvée : $found"
                                        } else {
                                            "Recherche terminée sans IP correspondante."
                                        }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    statusText = "Recherche arrêtée."
                                    throw e
                                } catch (e: Exception) {
                                    lastError = e.message ?: e.toString()
                                    statusText = "Recherche interrompue."
                                } finally {
                                    searching = false
                                    searchStartedAt?.let { started ->
                                        searchDurationSec =
                                            ((System.currentTimeMillis() - started) / 1000L)
                                                .coerceAtLeast(0L)
                                    }
                                    searchStartedAt = null
                                    searchJob = null
                                }
                            }
                        searchJob = job
                    },
                )
                if (!searching && (searchDurationSec != null || foundIp != null || lastError != null)) {
                    Text(
                        text =
                        buildString {
                            append("Bilan : ")
                            append("$attempt cycle(s)")
                            append(" · $observedCount IP observée(s)")
                            searchDurationSec?.let { append(" · ${it}s") }
                            when {
                                foundIp != null -> append(" · IP correspondante trouvée")
                                lastError != null -> append(" · erreur : $lastError")
                                else -> append(" · aucune IP correspondante")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color =
                        when {
                            foundIp != null -> A2MColors.Accent
                            lastError != null -> MaterialTheme.colorScheme.error
                            else -> A2MColors.Muted
                        },
                    )
                }
                foundIp?.let {
                    Text(
                        text = "✔ Trouvée : $it",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = A2MColors.Accent,
                    )
                }

            }

            // ── Chronologie ────────────────────────────────────────────────
            val history = remember(historyVersion) { readIpHistory() }
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(A2MShapes.card)
                    .background(A2MColors.CardBrush)
                    .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Chronologie",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = A2MColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    val historyEmpty = history.isEmpty()
                    if (historyEmpty) {
                        Text(
                            text = "Effacer l'historique",
                            style = MaterialTheme.typography.labelLarge,
                            color = A2MColors.Faint,
                        )
                    } else {
                        Text(
                            text = "Effacer l'historique",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier =
                            Modifier
                                .clickable {
                                    Settings.ipFinderHistory = ""
                                    historyVersion++
                                }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
                if (history.isEmpty()) {
                    Text(
                        text = "Aucune activité pour le moment. Lancez une recherche pour afficher sa chronologie ici.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = A2MColors.Muted,
                    )
                } else {
                    history.takeLast(15).reversed().forEach { (time, ip) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = A2MColors.Faint,
                            )
                            Text(
                                text = ip,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = A2MColors.Text,
                                modifier = Modifier.weight(1f),
                            )
                            if (rules.any { ipMatchesRule(ip, it) }) {
                                Text(
                                    text = "✔",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = A2MColors.Accent,
                                )
                            }
                        }
                        A2MDividerLine(inset = 0.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Barre épinglée : Arrêter ───────────────────────────────────────
        A2MFinderButton(
            label = "Arrêter",
            enabled = searching,
            fillMaxWidth = true,
            onClick = {
                searchJob?.cancel()
                statusText = "Recherche arrêtée."
            },
        )
    }
}

/** Vrai si l'IP respecte une règle donnée. */
private fun ipMatchesRule(ip: String, rule: Pair<Char, String>): Boolean =
    when (rule.first) {
        '^' -> ip.startsWith(rule.second)
        '$' -> ip.endsWith(rule.second)
        '=' -> ip == rule.second
        else -> ip.contains(rule.second)
    }

private fun readIpHistory(): List<Pair<String, String>> =
    Settings.ipFinderHistory.split("\n")
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val idx = line.indexOf('|')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }

private fun appendIpHistory(time: String, ip: String) {
    val existing = Settings.ipFinderHistory.split("\n").filter { it.isNotBlank() }
    Settings.ipFinderHistory = (existing + "$time|$ip").takeLast(120).joinToString("\n")
}

/**
 * Boucle de recherche : mode avion forcé, attendu « pause » secondes, relâché,
 * puis attente de la nouvelle IP cellulaire (lecture réelle des interfaces).
 * Sans permission de bascule : veille manuelle — chaque changement d'IP est
 * évalué pendant que l'utilisateur bascule le mode avion lui-même.
 */
private suspend fun runIpSearch(
    context: Context,
    serviceStatus: Status,
    dashboardViewModel: DashboardViewModel,
    maxCycles: Int,
    pauseMs: Long,
    canToggle: () -> Boolean,
    onAttempt: (Int) -> Unit,
    onStatus: (String) -> Unit,
    onIp: (String) -> Unit,
    onFallback: () -> Unit,
    onObserved: () -> Unit,
): String? {
    val rules = effectiveIpRules(Settings.ipFinderRules)
    var lastIp: String? = null

    suspend fun observe(ip: String): String? {
        lastIp = ip
        onIp(ip)
        appendIpHistory(TIME_FORMAT.format(Date()), ip)
        onObserved()
        return if (rules.any { ipMatchesRule(ip, it) }) ip else null
    }

    var attempt = 0
    var airplaneEnabledBySearch = false
    try {
        if (canToggle()) {
            while (attempt < maxCycles && kotlinx.coroutines.currentCoroutineContext().isActive) {
                attempt++
                onAttempt(attempt)
                onStatus("Tentative $attempt/$maxCycles — activation du mode avion…")
                if (!A2MNetworkInfo.setAirplaneMode(context, true)) {
                    onFallback()
                    break // bascule refusée → veille manuelle
                }
                airplaneEnabledBySearch = true
                delay(pauseMs)
                onStatus("Tentative $attempt/$maxCycles — attente du réseau mobile…")
                if (A2MNetworkInfo.setAirplaneMode(context, false)) {
                    airplaneEnabledBySearch = false
                }
                var waited = 0
                var found: String? = null
                while (waited < 45_000) {
                    val ip = withContext(Dispatchers.IO) { A2MNetworkInfo.readCellularIp() }
                    if (ip != null && ip != lastIp) {
                        found = observe(ip)
                        break
                    }
                    delay(2000)
                    waited += 2000
                }
                if (found != null) {
                    maybeAutoConnect(serviceStatus, dashboardViewModel)
                    return found
                }
                onStatus("Tentative $attempt/$maxCycles — IP non correspondante, cycle suivant…")
                delay(pauseMs)
            }
        } else {
            onFallback()
        }

        if (!canToggle() || attempt >= maxCycles) {
            // Veille manuelle : l'utilisateur bascule le mode avion, l'app observe.
            onStatus("Mode manuel — basculez le mode avion, l'app observe chaque IP…")
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val ip = withContext(Dispatchers.IO) { A2MNetworkInfo.readCellularIp() }
                if (ip != null && ip != lastIp) {
                    val found = observe(ip)
                    if (found != null) {
                        maybeAutoConnect(serviceStatus, dashboardViewModel)
                        return found
                    }
                }
                delay(2000)
            }
        }
        return null
    } finally {
        // Sécurité : ne jamais laisser l'appareil en mode avion après une
        // annulation, une erreur ou la fin de la recherche. NonCancellable
        // garantit que la remise à zéro s'exécute même si le job est annulé.
        if (airplaneEnabledBySearch) {
            withContext(kotlinx.coroutines.NonCancellable) {
                A2MNetworkInfo.setAirplaneMode(context, false)
            }
        }
    }
}

private suspend fun maybeAutoConnect(serviceStatus: Status, dashboardViewModel: DashboardViewModel) {
    if (Settings.ipFinderAutoConnect && serviceStatus == Status.Stopped) {
        dashboardViewModel.toggleService()
    }
}

/** Diagnostic de l'assistant : état réel lu dans le système Android. */
private class AssistantDiagnostics(
    val declared: Boolean,
    val recognitionDeclared: Boolean,
    val ourServiceActive: Boolean,
    val roleHeld: Boolean,
    val currentLabel: String,
) {
    companion object {
        private const val VOICE_INTERACTION_SERVICE = "voice_interaction_service"
        private const val OUR_SERVICE =
            "io.nekohasekai.sfa/io.nekohasekai.sfa.bg.A2MVoiceInteractionService"

        fun of(context: Context): AssistantDiagnostics {
            val services =
                runCatching {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.GET_SERVICES,
                    ).services?.toList().orEmpty()
                }.getOrDefault(emptyList())
            val declared = services.any { it.name.endsWith("A2MVoiceInteractionService") }
            val recognitionDeclared = services.any { it.name.endsWith("A2MRecognitionService") }
            val current =
                AndroidSettings.Secure.getString(context.contentResolver, VOICE_INTERACTION_SERVICE)
            val ourServiceActive = current == OUR_SERVICE
            val roleHeld =
                if (Build.VERSION.SDK_INT >= 29) {
                    val rm = context.getSystemService(android.app.role.RoleManager::class.java)
                    rm?.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) == true
                } else {
                    ourServiceActive
                }
            val currentLabel =
                if (current.isNullOrBlank()) {
                    "—"
                } else if (ourServiceActive) {
                    "A2M TUN"
                } else {
                    runCatching {
                        val pkg = current.substringBefore('/')
                        context.packageManager
                            .getApplicationInfo(pkg, 0)
                            .loadLabel(context.packageManager).toString()
                    }.getOrDefault(current)
                }
            return AssistantDiagnostics(
                declared = declared,
                recognitionDeclared = recognitionDeclared,
                ourServiceActive = ourServiceActive,
                roleHeld = roleHeld,
                currentLabel = currentLabel,
            )
        }

        fun requestAssistantRole(context: Context) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    val rm = context.getSystemService(android.app.role.RoleManager::class.java)
                    val intent = rm?.createRequestRoleIntent(android.app.role.RoleManager.ROLE_ASSISTANT)
                    if (intent != null) {
                        context.startActivity(intent)
                        return
                    }
                }
                context.startActivity(Intent(AndroidSettings.ACTION_VOICE_INPUT_SETTINGS))
            }
        }
    }
}

@Composable
private fun AssistantDiagRow(label: String, ok: Boolean) {
    Text(
        text = "$label : ${if (ok) "Oui" else "Non"}",
        style = MaterialTheme.typography.bodyLarge,
        color = A2MColors.Text,
    )
}

/** Bouton plein façon Dams Tunnel (arrondi, pleine largeur en option). */
@Composable
private fun A2MFinderButton(
    label: String,
    enabled: Boolean = true,
    outline: Boolean = false,
    fillMaxWidth: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = A2MColors.Accent
    val shape = A2MShapes.card
    val modifier =
        (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(shape)
            .background(
                when {
                    outline -> Color.Transparent
                    !enabled -> tint.copy(alpha = 0.35f)
                    else -> tint
                },
            )
            .then(
                if (outline) {
                    Modifier.border(1.dp, A2MColors.Accent.copy(alpha = 0.5f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (outline) A2MColors.Muted else Color.White,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier,
    )
}
