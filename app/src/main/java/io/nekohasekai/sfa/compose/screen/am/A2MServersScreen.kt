package io.nekohasekai.sfa.compose.screen.am

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.bg.UpdateProfileWork
import io.nekohasekai.sfa.compose.screen.configuration.ProfileImportHandler
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.dams.importer.A2MExportFile
import io.nekohasekai.sfa.dams.importer.ClipboardImporter
import io.nekohasekai.sfa.dams.importer.ShareExport
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Gestionnaire de serveurs A2M TUN.
 *
 * - liste réelle des profils enregistrés (DashboardViewModel + base Room)
 * - sélection branchée sur le moteur (Settings.selectedProfile + serviceReload)
 * - test TCP individuel et test global avec progression/arrêt
 * - multi-sélection : test, export, suppression
 * - appui long : menu contextuel (tester, modifier, partager, sélectionner, supprimer)
 * - filtre protocole, tri, affichage liste/grille/tableau, tailles S/M/L
 * - import depuis un fichier, un lien ou le presse-papiers
 *   (ProfilImportHandler / ClipboardImporter existants, aucune logique dupliquée)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A2MServersScreen(
    dashboardViewModel: DashboardViewModel,
    onEditProfile: (Long) -> Unit = {},
    onCreateServer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val scaffoldPadding = LocalScaffoldPadding.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val importHandler = remember { ProfileImportHandler(context) }
    val clipboardImporter = remember { ClipboardImporter(context) }

    var pendingDelete by remember { mutableStateOf<Profile?>(null) }
    var pendingBulkDelete by remember { mutableStateOf(false) }
    var profileSummaries by remember { mutableStateOf<Map<Long, A2MProfileInfo.Summary>>(emptyMap()) }
    val protocols = remember(profileSummaries) { profileSummaries.mapValues { it.value.protocol } }
    val codes = remember(profileSummaries) { profileSummaries.mapValues { it.value.code } }
    val addresses = remember(profileSummaries) { profileSummaries.mapValues { it.value.address } }
    var searchQuery by remember { mutableStateOf("") }
    var protocolFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(ServerSort.fromKey(Settings.serversSort)) }
    var viewMode by remember { mutableStateOf(ServerViewMode.fromKey(Settings.serversView)) }
    var serverSize by remember { mutableStateOf(ServerSize.fromKey(Settings.serversSize)) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var contextTarget by remember { mutableStateOf<Profile?>(null) }
    var testResults by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var testLatencies by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var testingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var testAllJob by remember { mutableStateOf<Job?>(null) }
    var testAllRunning by remember { mutableStateOf(false) }
    var testAllDone by remember { mutableStateOf(0) }
    var testAllTotal by remember { mutableStateOf(0) }
    var selectingFastest by remember { mutableStateOf(false) }
    val favoritesPreferences = remember { context.getSharedPreferences("a2m_server_ui", android.content.Context.MODE_PRIVATE) }
    var favoriteIds by remember { mutableStateOf(favoritesPreferences.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showViewDialog by remember { mutableStateOf(false) }

    var showImportSheet by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<Pair<Profile, String>?>(null) }
    var a2mExportTargets by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var a2mExportCoded by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var subscriptionUrl by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.profiles) {
        val profiles = uiState.profiles
        val maps =
            withContext(Dispatchers.IO) {
                profiles.associate { it.id to A2MProfileInfo.summary(it) }
            }
        profileSummaries = maps
        val ids = profiles.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(ids)
        if (selectedIds.isEmpty()) selectionMode = false
        testResults = testResults.filterKeys { it in ids }
        testLatencies = testLatencies.filterKeys { it in ids }
    }

    val protocolOptions =
        remember(uiState.profiles, protocols) {
            uiState.profiles.mapNotNull { protocols[it.id] }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
    LaunchedEffect(protocolOptions) {
        if (protocolFilter != null && protocolFilter !in protocolOptions) {
            protocolFilter = null
        }
    }

    fun importText(text: String) {
        coroutineScope.launch {
            isImporting = true
            when (val outcome = clipboardImporter.importText(text)) {
                is ClipboardImporter.Outcome.Success -> {
                    importMessage = importSummary(outcome.result)
                    subscriptionUrl =
                        outcome.result.subscriptionUrl.takeIf { outcome.result.profiles.isEmpty() }
                }
                is ClipboardImporter.Outcome.Error -> importMessage = outcome.message
            }
            isImporting = false
        }
    }

    fun importSubscription(url: String) {
        coroutineScope.launch {
            isImporting = true
            when (val outcome = clipboardImporter.importSubscription(url, asRemote = false)) {
                is ClipboardImporter.Outcome.Success -> importMessage = importSummary(outcome.result)
                is ClipboardImporter.Outcome.Error -> importMessage = outcome.message
            }
            isImporting = false
        }
    }

    /** Bascule la mise à jour automatique d'un abonnement (mécanisme réel du moteur). */
    fun toggleAutoUpdate(profile: Profile) {
        coroutineScope.launch(Dispatchers.IO) {
            profile.typed.autoUpdate = !profile.typed.autoUpdate
            ProfileManager.update(profile)
            UpdateProfileWork.reconfigureUpdater()
        }
    }

    /**
     * Test TCP réel du serveur : la socket est ouverte vers l'adresse:port
     * réels de la configuration ; la durée d'établissement (résolution DNS
     * incluse) est affichée. Échec honnête si la connexion ne s'établit pas.
     */
    suspend fun tcpResult(profile: Profile): Pair<String, Long?> {
        A2MProfileInfo.tcpProbeUnsupportedReason(profile)?.let { return it to null }
        val target = withContext(Dispatchers.IO) { A2MProfileInfo.serverAddressAndPort(profile) }
        return if (target == null) {
            "Adresse inconnue" to (null as Long?)
        } else {
            when (val ms = A2MProfileInfo.tcpPing(target.first, target.second)) {
                null -> "✕ timeout" to (null as Long?)
                else -> "TCP $ms ms" to ms.toLong()
            }
        }
    }

    fun applyTestResult(
        profile: Profile,
        result: Pair<String, Long?>,
    ) {
        testResults = testResults + (profile.id to result.first)
        testLatencies =
            if (result.second != null) {
                testLatencies + (profile.id to result.second!!)
            } else {
                testLatencies - profile.id
            }
    }

    fun testProfile(profile: Profile) {
        coroutineScope.launch {
            testingIds = testingIds + profile.id
            applyTestResult(profile, tcpResult(profile))
            testingIds = testingIds - profile.id
        }
    }

    fun testProfiles(targets: List<Profile>) {
        if (targets.isEmpty() || testAllRunning) return
        testAllRunning = true
        testAllDone = 0
        testAllTotal = targets.size
        testAllJob =
            coroutineScope.launch {
                try {
                    val semaphore = Semaphore(4)
                    kotlinx.coroutines.coroutineScope {
                        targets.map { profile ->
                            async {
                                if (!isActive) return@async
                                testingIds = testingIds + profile.id
                                try {
                                    applyTestResult(profile, semaphore.withPermit { tcpResult(profile) })
                                } finally {
                                    testingIds = testingIds - profile.id
                                    testAllDone++
                                }
                            }
                        }.awaitAll()
                    }
                } finally {
                    testingIds = testingIds - targets.map { it.id }.toSet()
                    testAllRunning = false
                    testAllJob = null
                }
            }
    }

    fun stopTestAll() {
        testAllJob?.cancel()
        testAllJob = null
        testAllRunning = false
    }

    fun toggleFavorite(profile: Profile) {
        val updated = if (profile.id in favoriteIds) favoriteIds - profile.id else favoriteIds + profile.id
        favoriteIds = updated
        favoritesPreferences.edit().putStringSet("favorites", updated.map { it.toString() }.toSet()).apply()
    }

    /** Ouvre la feuille d'export (liens, QR, .a2m, config brute) d'un profil. */
    fun openExport(profile: Profile) {
        coroutineScope.launch {
            val text =
                withContext(Dispatchers.IO) {
                    runCatching { File(profile.typed.path).readText() }.getOrNull()
                }
            if (text.isNullOrBlank()) {
                importMessage = "Rien à exporter : configuration vide"
            } else {
                exportTarget = profile to text
            }
        }
    }

    fun shareText(text: String, title: String) {
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        context.startActivity(Intent.createChooser(send, title))
    }

    /** Partage réel : lien d'abonnement (distant) ou contenu de la config (local). */
    fun shareProfile(profile: Profile) {
        coroutineScope.launch(Dispatchers.IO) {
            val text =
                when (profile.typed.type) {
                    TypedProfile.Type.Remote -> profile.typed.remoteURL.takeIf { it.isNotBlank() }
                    else -> null
                } ?: runCatching { File(profile.typed.path).readText() }.getOrNull()
            withContext(Dispatchers.Main) {
                if (text.isNullOrBlank()) {
                    importMessage = "Rien à partager : configuration vide"
                } else {
                    shareText(text, "Partager « ${profile.name} »")
                }
            }
        }
    }

    /** Export groupé : un lien A2M autonome par configuration, partageable en un seul texte. */
    fun exportProfiles(targets: List<Profile>) {
        coroutineScope.launch {
            val sections =
                withContext(Dispatchers.IO) {
                    targets.mapNotNull { profile ->
                        val config =
                            runCatching { File(profile.typed.path).readText() }
                                .getOrNull()
                                ?.takeIf { it.isNotBlank() }
                                ?: return@mapNotNull null
                        val link = runCatching { ShareExport.codedLink(config) }.getOrNull()
                            ?: return@mapNotNull null
                        "=== ${profile.name} ===\n$link"
                    }
                }
            if (sections.isEmpty()) {
                importMessage = "Rien à exporter : configurations vides"
            } else {
                shareText(
                    "A2M TUN — export de ${sections.size} serveur(s)\n\n" +
                        sections.joinToString("\n\n"),
                    "Exporter les serveurs",
                )
            }
        }
    }

    fun toggleSelection(profile: Profile) {
        val next =
            if (profile.id in selectedIds) {
                selectedIds - profile.id
            } else {
                selectedIds + profile.id
            }
        selectedIds = next
        if (next.isEmpty()) selectionMode = false
    }

    fun enterSelection(profile: Profile) {
        selectionMode = true
        selectedIds = selectedIds + profile.id
    }

    fun deleteSelected(targets: List<Profile>) {
        targets.forEach { dashboardViewModel.deleteProfile(it) }
        selectedIds = emptySet()
        selectionMode = false
        pendingBulkDelete = false
    }

    val fileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    isImporting = true
                    when (val result = importHandler.importFromUri(uri)) {
                        is ProfileImportHandler.ImportResult.Success ->
                            importMessage =
                                result.summary ?: if (result.profiles.size == 1) {
                                    "Serveur importé : ${result.profile.name}"
                                } else {
                                    "${result.profiles.size} serveurs importés"
                                }
                        is ProfileImportHandler.ImportResult.Error ->
                            importMessage = "Import impossible : ${result.message}"
                    }
                    isImporting = false
                }
            }
        }

    val a2mFileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(A2MExportFile.MIME_TYPE),
        ) { uri ->
            val targets = a2mExportTargets
            val coded = a2mExportCoded
            a2mExportTargets = emptyList()
            if (uri != null && targets.isNotEmpty()) {
                val destination = uri
                coroutineScope.launch {
                    val outcome =
                        runCatching {
                            val text =
                                withContext(Dispatchers.IO) {
                                    val entries =
                                        targets.mapNotNull { profile ->
                                            val config =
                                                runCatching { File(profile.typed.path).readText() }
                                                    .getOrNull()
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?: return@mapNotNull null
                                            A2MExportFile.Entry(profile.name, config)
                                        }
                                    if (entries.isEmpty()) {
                                        error("Aucune configuration à exporter.")
                                    }
                                    if (coded) {
                                        A2MExportFile.encodeCoded(entries)
                                    } else {
                                        A2MExportFile.encodeSimple(entries)
                                    }
                                }
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openOutputStream(destination)?.use {
                                    it.write(text.toByteArray(Charsets.UTF_8))
                                } ?: error("Impossible d'ouvrir le fichier de destination.")
                            }
                        }
                    Toast.makeText(
                        context,
                        if (outcome.isSuccess) {
                            "Fichier .a2m enregistré."
                        } else {
                            "Échec de l'export : ${outcome.exceptionOrNull()?.message.orEmpty()}"
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

    fun select(profile: Profile) {
        if (profile.id != uiState.selectedProfileId) {
            dashboardViewModel.selectProfile(profile.id)
        }
    }

    /** Choisit le serveur testé le plus rapide et attend la reprise du tunnel actif. */
    fun selectFastest() {
        if (selectingFastest) return
        if (testAllRunning) {
            Toast.makeText(context, "Attendez la fin du test en cours.", Toast.LENGTH_SHORT).show()
            return
        }
        val targets = uiState.profiles
        if (targets.isEmpty()) {
            Toast.makeText(context, "Aucun serveur à tester.", Toast.LENGTH_LONG).show()
            return
        }
        selectingFastest = true
        coroutineScope.launch {
            try {
                val unsupported = withContext(Dispatchers.IO) {
                    targets.filter { A2MProfileInfo.tcpProbeUnsupportedReason(it) != null }
                }
                val comparableTargets = targets.filterNot { it in unsupported }
                if (comparableTargets.isEmpty()) {
                    error("Aucun serveur ne peut être comparé avec le test TCP ; les profils UDP/QUIC sont conservés.")
                }
                // Mesure fraîche pour cette sélection : une ancienne latence ne
                // doit pas envoyer le tunnel vers un serveur devenu indisponible.
                testResults = emptyMap()
                testLatencies = emptyMap()
                testProfiles(comparableTargets)
                val testJob = testAllJob
                testJob?.join()
                if (testJob?.isCancelled == true) error("Le test des serveurs a été annulé.")
                val fastest = fastestProfileId(testLatencies)
                    ?: error("Aucun serveur comparable n'a répondu au test TCP ; les profils UDP/QUIC n'ont pas été déclarés indisponibles.")
                val profile = uiState.profiles.firstOrNull { it.id == fastest }
                    ?: error("Le serveur le plus rapide n'existe plus.")
                val latency = testLatencies[fastest] ?: error("Mesure de latence indisponible.")
                val reconnected = dashboardViewModel.selectProfileAndReconnect(profile.id)
                val outcome = if (reconnected) "Tunnel connecté" else "Serveur sélectionné"
                val skipped = if (unsupported.isNotEmpty()) " · ${unsupported.size} profil(s) UDP/QUIC non comparé(s)" else " · meilleure latence TCP"
                Toast.makeText(
                    context,
                    "$outcome : ${profile.name} (${latency} ms)$skipped",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Échec du changement de serveur : ${e.message ?: "connexion indisponible"}",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                selectingFastest = false
            }
        }
    }

    fun exportA2M(
        targets: List<Profile>,
        coded: Boolean,
    ) {
        if (targets.isEmpty()) {
            Toast.makeText(context, "Aucun serveur sélectionné.", Toast.LENGTH_SHORT).show()
            return
        }
        a2mExportTargets = targets
        a2mExportCoded = coded
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        a2mFileLauncher.launch("a2m-servers-$stamp.a2m")
    }

    val query = searchQuery.trim()
    val filteredProfiles =
        uiState.profiles.filter { profile ->
            val matchesQuery =
                query.isEmpty() ||
                    profile.name.contains(query, ignoreCase = true) ||
                    (protocols[profile.id] ?: "").contains(query, ignoreCase = true) ||
                    (addresses[profile.id] ?: "").contains(query, ignoreCase = true)
            val matchesProtocol = protocolFilter == null || protocols[profile.id] == protocolFilter
            matchesQuery && matchesProtocol && (!favoritesOnly || profile.id in favoriteIds)
        }
    fun statusRank(profile: Profile): Int {
        val result = testResults[profile.id]
        return when {
            profile.id == uiState.selectedProfileId -> 0
            result?.startsWith("TCP") == true -> 1
            result == null -> 2
            else -> 3
        }
    }
    val sortedProfiles =
        when (sort) {
            ServerSort.NAME -> filteredProfiles.sortedBy { it.name.lowercase(Locale.getDefault()) }
            ServerSort.PROTOCOL ->
                filteredProfiles.sortedWith(
                    compareBy(
                        { protocols[it.id] ?: "" },
                        { it.name.lowercase(Locale.getDefault()) },
                    ),
                )
            ServerSort.STATUS ->
                filteredProfiles.sortedWith(
                    compareBy(
                        { statusRank(it) },
                        { it.name.lowercase(Locale.getDefault()) },
                    ),
                )
            ServerSort.PING -> filteredProfiles.sortedWith(compareBy({ testLatencies[it.id] == null }, { testLatencies[it.id] ?: Long.MAX_VALUE }, { it.name.lowercase(Locale.getDefault()) }))
        }
    val selectedProfiles = uiState.profiles.filter { it.id in selectedIds }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Serveurs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = A2MColors.Text,
                        )
                        Text(
                            text =
                            if (uiState.profiles.isEmpty()) {
                                "Aucune configuration enregistrée"
                            } else {
                                "${uiState.profiles.size} configuration(s) · " +
                                    "${uiState.selectedProfileName ?: "aucune sélectionnée"}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = A2MColors.Muted,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (selectionMode) {
                                selectionMode = false
                                selectedIds = emptySet()
                            } else {
                                selectionMode = true
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (selectionMode) Icons.Filled.Close else Icons.Filled.Checklist,
                            contentDescription = if (selectionMode) "Annuler la sélection" else "Sélectionner",
                            tint = if (selectionMode) A2MColors.Accent else A2MColors.Muted,
                        )
                    }
                    IconButton(
                        onClick = { showImportSheet = true },
                        enabled = !isImporting,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Importer un serveur",
                            tint = A2MColors.Accent,
                        )
                    }
                }
            }

            if (uiState.profiles.isEmpty()) {
                item { EmptyServersCard(onImport = { showImportSheet = true }) }
            } else {
                if (selectionMode) {
                    item {
                        Column(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(A2MShapes.card)
                                .background(A2MColors.CardBrush)
                                .border(1.dp, A2MColors.CardBorderSelected, A2MShapes.card)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${selectedIds.size} sélectionné(s)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = A2MColors.Text,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        selectedIds = filteredProfiles.map { it.id }.toSet()
                                    },
                                ) {
                                    Text("Tout", color = A2MColors.Accent)
                                }
                                TextButton(
                                    onClick = {
                                        selectedIds = emptySet()
                                        selectionMode = false
                                    },
                                ) {
                                    Text("Annuler", color = A2MColors.Muted)
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                A2MServerActionChip(
                                    label = "Tester",
                                    icon = Icons.Filled.NetworkCheck,
                                    enabled = selectedProfiles.isNotEmpty(),
                                    onClick = { testProfiles(selectedProfiles) },
                                )
                                A2MServerActionChip(
                                    label = "Exporter",
                                    icon = Icons.Filled.Share,
                                    enabled = selectedProfiles.isNotEmpty(),
                                    onClick = { exportProfiles(selectedProfiles) },
                                )
                                A2MServerActionChip(
                                    label = "A2M simple",
                                    icon = Icons.Filled.Save,
                                    enabled = selectedProfiles.isNotEmpty(),
                                    onClick = { exportA2M(selectedProfiles, coded = false) },
                                )
                                A2MServerActionChip(
                                    label = "A2M codé",
                                    icon = Icons.Filled.Save,
                                    enabled = selectedProfiles.isNotEmpty(),
                                    onClick = { exportA2M(selectedProfiles, coded = true) },
                                )
                                A2MServerActionChip(
                                    label = "Supprimer",
                                    icon = Icons.Filled.Delete,
                                    danger = true,
                                    enabled = selectedProfiles.isNotEmpty(),
                                    onClick = { pendingBulkDelete = true },
                                )
                            }
                        }
                    }
                }

                item {
                    A2MSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Rechercher une config",
                    )
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        A2MServerActionChip(
                            label =
                            if (testAllRunning) {
                                "Arrêter $testAllDone/$testAllTotal"
                            } else {
                                "Tester tout"
                            },
                            icon = if (testAllRunning) Icons.Filled.Stop else Icons.Filled.NetworkCheck,
                            danger = testAllRunning,
                            enabled = testAllRunning || sortedProfiles.isNotEmpty(),
                            onClick = {
                                if (testAllRunning) stopTestAll() else testProfiles(sortedProfiles)
                            },
                        )
                        A2MServerActionChip(
                            label = if (selectingFastest) "Reconnexion…" else "Sélectionner le plus rapide",
                            icon = Icons.Filled.Speed,
                            enabled = !selectingFastest && !testAllRunning,
                            onClick = { selectFastest() },
                        )
                        A2MServerActionChip(
                            label = "Tri : ${sort.label}",
                            onClick = { showSortDialog = true },
                        )
                        A2MServerActionChip(
                            label = "Vue : ${viewMode.label}",
                            onClick = { showViewDialog = true },
                        )
                        A2MServerActionChip(
                            label = "Taille : ${serverSize.label}",
                            onClick = {
                                val next = serverSize.next()
                                serverSize = next
                                Settings.serversSize = next.key
                            },
                        )
                        A2MServerActionChip(
                            label = if (favoritesOnly) "Favoris" else "Tous / favoris",
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = !favoritesOnly },
                        )
                    }
                }

                if (protocolOptions.isNotEmpty()) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            A2MServerActionChip(
                                label = "Tous",
                                selected = protocolFilter == null,
                                onClick = { protocolFilter = null },
                            )
                            protocolOptions.forEach { protocol ->
                                A2MServerActionChip(
                                    label = protocol,
                                    selected = protocolFilter == protocol,
                                    onClick = {
                                        protocolFilter =
                                            if (protocolFilter == protocol) null else protocol
                                    },
                                )
                            }
                        }
                    }
                }

                if (testAllRunning) {
                    item {
                        Column(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(A2MShapes.card)
                                .background(A2MColors.CardBrush)
                                .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Test TCP global : $testAllDone / $testAllTotal",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = A2MColors.Text,
                            )
                            LinearProgressIndicator(
                                progress = {
                                    if (testAllTotal > 0) {
                                        (testAllDone.toFloat() / testAllTotal).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "Quatre tests TCP au maximum tournent en parallèle. Le test peut être arrêté à tout moment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = A2MColors.Muted,
                            )
                        }
                    }
                }

                if (filteredProfiles.isEmpty()) {
                    item {
                        Text(
                            text =
                            if (query.isNotEmpty()) {
                                "Aucune config ne correspond à « $query »"
                            } else {
                                "Aucune config pour ce protocole"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = A2MColors.Muted,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                if (viewMode == ServerViewMode.GRID) {
                    items(
                        sortedProfiles.chunked(2),
                        key = { chunk -> chunk.joinToString("-") { it.id.toString() } },
                    ) { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            chunk.forEach { profile ->
                                ServerGridCard(
                                    profile = profile,
                                    protocol = protocols[profile.id] ?: A2MProfileInfo.typeLabel(profile),
                                    code = codes[profile.id] ?: "",
                                    address = addresses[profile.id],
                                    selected = profile.id == uiState.selectedProfileId,
                                    active = profile.id == uiState.selectedProfileId && uiState.serviceStatus == io.nekohasekai.sfa.constant.Status.Started,
                                    favorite = profile.id in favoriteIds,
                                    onFavorite = { toggleFavorite(profile) },
                                    selectionMode = selectionMode,
                                    checked = profile.id in selectedIds,
                                    size = serverSize,
                                    testResult = testResults[profile.id],
                                    testing = profile.id in testingIds,
                                    onClick = {
                                        if (selectionMode) toggleSelection(profile) else select(profile)
                                    },
                                    onLongClick = { contextTarget = profile },
                                    onMenu = { contextTarget = profile },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (chunk.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else if (viewMode == ServerViewMode.TABLE) {
                    item { ServerTableHeader() }
                    items(sortedProfiles, key = { it.id }) { profile ->
                        ServerTableRow(
                            profile = profile,
                            protocol = protocols[profile.id] ?: A2MProfileInfo.typeLabel(profile),
                            selected = profile.id == uiState.selectedProfileId,
                            active = profile.id == uiState.selectedProfileId && uiState.serviceStatus == io.nekohasekai.sfa.constant.Status.Started,
                            selectionMode = selectionMode,
                            checked = profile.id in selectedIds,
                            testResult = testResults[profile.id],
                            testing = profile.id in testingIds,
                            onClick = {
                                if (selectionMode) toggleSelection(profile) else select(profile)
                            },
                            onLongClick = { contextTarget = profile },
                        )
                    }
                } else {
                    items(sortedProfiles, key = { it.id }) { profile ->
                        ServerRow(
                            profile = profile,
                            protocol = protocols[profile.id] ?: A2MProfileInfo.typeLabel(profile),
                            code = codes[profile.id] ?: "",
                            address = addresses[profile.id],
                            selected = profile.id == uiState.selectedProfileId,
                            active = profile.id == uiState.selectedProfileId && uiState.serviceStatus == io.nekohasekai.sfa.constant.Status.Started,
                            favorite = profile.id in favoriteIds,
                            onFavorite = { toggleFavorite(profile) },
                            autoUpdateEnabled =
                            if (profile.typed.type == TypedProfile.Type.Remote) {
                                profile.typed.autoUpdate
                            } else {
                                null
                            },
                            onToggleAutoUpdate = { toggleAutoUpdate(profile) },
                            selectionMode = selectionMode,
                            checked = profile.id in selectedIds,
                            size = serverSize,
                            testResult = testResults[profile.id],
                            testing = profile.id in testingIds,
                            onClick = {
                                if (selectionMode) toggleSelection(profile) else select(profile)
                            },
                            onLongClick = { contextTarget = profile },
                            onMenu = { contextTarget = profile },
                        )
                    }
                }
            }
        }
    }

    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = A2MColors.BackgroundMid,
            contentColor = A2MColors.Text,
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Importer un serveur",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
                ImportOption(
                    icon = Icons.Filled.Description,
                    title = "Depuis un fichier",
                    subtitle = "Configuration sing-box (.json) ou export .a2m",
                    onClick = {
                        showImportSheet = false
                        fileLauncher.launch("*/*")
                    },
                )
                ImportOption(
                    icon = Icons.Filled.Link,
                    title = "Depuis un lien",
                    subtitle = "Lien de partage ou abonnement",
                    onClick = {
                        showImportSheet = false
                        showLinkDialog = true
                    },
                )
                ImportOption(
                    icon = Icons.Filled.EditNote,
                    title = "Créer manuellement",
                    subtitle = "Saisie guidée, sans écrire de JSON",
                    onClick = {
                        showImportSheet = false
                        onCreateServer()
                    },
                )
                ImportOption(
                    icon = Icons.Filled.ContentPaste,
                    title = "Depuis le presse-papiers",
                    subtitle = "Coller une configuration ou un lien",
                    onClick = {
                        showImportSheet = false
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val text =
                            clipboard?.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                        if (text.isNullOrBlank()) {
                            importMessage = "Presse-papiers vide"
                        } else {
                            importText(text)
                        }
                    },
                )
                Box(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = {
                showLinkDialog = false
                linkText = ""
            },
            title = { Text("Importer depuis un lien", color = A2MColors.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Collez un lien de partage (vless://, vmess://, trojan://, ss://…) ou une URL d'abonnement.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = A2MColors.Muted,
                    )
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Lien") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = linkText.isNotBlank(),
                    onClick = {
                        val url = linkText.trim()
                        showLinkDialog = false
                        linkText = ""
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            importSubscription(url)
                        } else {
                            importText(url)
                        }
                    },
                ) {
                    Text("Importer", color = A2MColors.Accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLinkDialog = false
                        linkText = ""
                    },
                ) {
                    Text("Annuler", color = A2MColors.Muted)
                }
            },
        )
    }

    subscriptionUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { subscriptionUrl = null },
            title = { Text("Abonnement détecté", color = A2MColors.Text) },
            text = {
                Text(
                    "Importer cet abonnement comme source distante actualisable ?\n$url",
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2MColors.Muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        subscriptionUrl = null
                        coroutineScope.launch {
                            isImporting = true
                            when (val outcome = clipboardImporter.importSubscription(url, asRemote = true)) {
                                is ClipboardImporter.Outcome.Success ->
                                    importMessage =
                                        "Abonnement importé : " +
                                            "${outcome.result.profiles.firstOrNull()?.name ?: "ok"}"
                                is ClipboardImporter.Outcome.Error -> importMessage = outcome.message
                            }
                            isImporting = false
                        }
                    },
                ) {
                    Text("Importer", color = A2MColors.Accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { subscriptionUrl = null }) {
                    Text("Ignorer", color = A2MColors.Muted)
                }
            },
        )
    }

    importMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { importMessage = null },
            title = { Text("Importation", color = A2MColors.Text) },
            text = { Text(message, color = A2MColors.Muted) },
            confirmButton = {
                TextButton(onClick = { importMessage = null }) {
                    Text("OK", color = A2MColors.Accent, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    exportTarget?.let { (profile, config) ->
        A2MExportSheet(
            profileName = profile.name,
            config = config,
            onDismiss = { exportTarget = null },
        )
    }

    contextTarget?.let { profile ->
        ModalBottomSheet(
            onDismissRequest = { contextTarget = null },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = A2MColors.BackgroundMid,
            contentColor = A2MColors.Text,
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = profile.name.ifBlank { "Sans nom" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
                Text(
                    text = addresses[profile.id] ?: A2MProfileInfo.typeLabel(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                )
                ImportOption(
                    icon = Icons.Filled.NetworkCheck,
                    title = "Tester (TCP)",
                    subtitle = "Vérifie que host:port est joignable",
                    onClick = {
                        contextTarget = null
                        testProfile(profile)
                    },
                )
                ImportOption(
                    icon = Icons.Filled.Edit,
                    title = "Modifier",
                    subtitle = "Éditer les champs du profil local",
                    onClick = {
                        contextTarget = null
                        onEditProfile(profile.id)
                    },
                )
                ImportOption(
                    icon = Icons.Filled.Share,
                    title = "Partager / exporter",
                    subtitle = "Lien, QR, fichier .a2m ou configuration brute",
                    onClick = {
                        contextTarget = null
                        openExport(profile)
                    },
                )
                if (profile.typed.type == TypedProfile.Type.Remote) {
                    ImportOption(
                        icon = Icons.Filled.Link,
                        title = "Partager l'URL d'abonnement",
                        subtitle = "Conserver le partage brut de l'abonnement distant",
                        onClick = {
                            contextTarget = null
                            shareProfile(profile)
                        },
                    )
                }
                ImportOption(
                    icon = Icons.Filled.CheckCircle,
                    title = "Sélectionner",
                    subtitle = "Ajouter à la sélection multiple",
                    onClick = {
                        contextTarget = null
                        enterSelection(profile)
                    },
                )
                ImportOption(
                    icon = Icons.Filled.Delete,
                    title = "Supprimer",
                    subtitle = "Retirer ce serveur de l'application",
                    onClick = {
                        contextTarget = null
                        pendingDelete = profile
                    },
                )
                Box(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Trier les serveurs", color = A2MColors.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ServerSort.entries.filter { it != ServerSort.PING || testLatencies.isNotEmpty() }.forEach { option ->
                        TextButton(
                            onClick = {
                                sort = option
                                Settings.serversSort = option.key
                                showSortDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = option.label + if (sort == option) "  ✓" else "",
                                color = if (sort == option) A2MColors.Accent else A2MColors.Text,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Fermer", color = A2MColors.Muted)
                }
            },
        )
    }

    if (showViewDialog) {
        AlertDialog(
            onDismissRequest = { showViewDialog = false },
            title = { Text("Affichage", color = A2MColors.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ServerViewMode.entries.forEach { option ->
                        TextButton(
                            onClick = {
                                viewMode = option
                                Settings.serversView = option.key
                                showViewDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = option.label + if (viewMode == option) "  ✓" else "",
                                color = if (viewMode == option) A2MColors.Accent else A2MColors.Text,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Text(
                        text = "Taille actuelle : ${serverSize.label} " +
                            "(touchez « Taille » pour changer)",
                        style = MaterialTheme.typography.bodySmall,
                        color = A2MColors.Muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showViewDialog = false }) {
                    Text("Fermer", color = A2MColors.Muted)
                }
            },
        )
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Supprimer le serveur ?", color = A2MColors.Text) },
            text = {
                Text(
                    "« ${profile.name} » sera supprimé de l'application.",
                    color = A2MColors.Muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dashboardViewModel.deleteProfile(profile)
                        pendingDelete = null
                    },
                ) {
                    Text("Supprimer", color = A2MColors.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Annuler", color = A2MColors.Muted)
                }
            },
        )
    }

    if (pendingBulkDelete) {
        AlertDialog(
            onDismissRequest = { pendingBulkDelete = false },
            title = { Text("Supprimer la sélection ?", color = A2MColors.Text) },
            text = {
                Text(
                    "${selectedProfiles.size} serveur(s) seront supprimés de l'application. " +
                        "Cette action est irréversible.",
                    color = A2MColors.Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteSelected(selectedProfiles) }) {
                    Text("Supprimer", color = A2MColors.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkDelete = false }) {
                    Text("Annuler", color = A2MColors.Muted)
                }
            },
        )
    }
}

private enum class ServerViewMode(
    val key: String,
    val label: String,
) {
    LIST("list", "Liste"),
    GRID("grid", "Grille"),
    TABLE("table", "Tableau"),
    ;

    companion object {
        fun fromKey(key: String): ServerViewMode =
            entries.firstOrNull { it.key == key } ?: LIST
    }
}

private enum class ServerSize(
    val key: String,
    val label: String,
) {
    SMALL("s", "S"),
    MEDIUM("m", "M"),
    LARGE("l", "L"),
    ;

    fun next(): ServerSize = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromKey(key: String): ServerSize =
            entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

private enum class ServerSort(
    val key: String,
    val label: String,
) {
    NAME("name", "Nom"),
    PROTOCOL("protocol", "Protocole"),
    STATUS("status", "Statut"),
    PING("ping", "Ping"),
    ;

    companion object {
        fun fromKey(key: String): ServerSort =
            entries.firstOrNull { it.key == key } ?: NAME
    }
}

/** Nettoyage purement visuel : le nom stocké, exporté et partagé reste intact. */
private fun cleanServerDisplayName(rawName: String, protocol: String): String {
    var name = rawName.ifBlank { "Sans nom" }.trim()
    val known = setOf(protocol.lowercase(), "ss", "shadowsocks", "vm", "vmess", "vl", "vless", "tr", "trojan", "hy", "hysteria", "hysteria2", "tuic", "wireguard", "wg", "socks", "http", "anytls", "shadowtls", "ssh")
    repeat(4) {
        val match = Regex("^\\[([^]]+)]\\s*").find(name) ?: return name
        if (match.groupValues[1].trim().lowercase() !in known) return name
        name = name.substring(match.range.last + 1).trimStart()
    }
    return name.ifBlank { rawName.ifBlank { "Sans nom" } }
}

/** Pays dérivé du code figurant dans le nom d'hôte, sans requête réseau. */
private fun countryLabel(host: String): String {
    val countries = mapOf(
        "us" to ("🇺🇸" to "États-Unis"), "sg" to ("🇸🇬" to "Singapour"),
        "fr" to ("🇫🇷" to "France"), "de" to ("🇩🇪" to "Allemagne"),
        "gb" to ("🇬🇧" to "Royaume-Uni"), "nl" to ("🇳🇱" to "Pays-Bas"),
        "ca" to ("🇨🇦" to "Canada"), "au" to ("🇦🇺" to "Australie"),
        "jp" to ("🇯🇵" to "Japon"), "kr" to ("🇰🇷" to "Corée du Sud"),
        "in" to ("🇮🇳" to "Inde"), "br" to ("🇧🇷" to "Brésil"),
        "tr" to ("🇹🇷" to "Turquie"), "ae" to ("🇦🇪" to "Émirats arabes unis"),
        "za" to ("🇿🇦" to "Afrique du Sud"),
    )
    val pattern = Regex("(?i)(?:^|[.-])(${countries.keys.joinToString("|")})(?:-?\\d+)?(?=[.-]|$)")
    val match = pattern.findAll(host).lastOrNull() ?: return host
    val (flag, country) = countries[match.groupValues[1].lowercase()] ?: return host
    return "$flag $country · $host"
}

private fun importSummary(result: ClipboardImporter.Imported): String =
    buildString {
        if (result.profiles.isNotEmpty()) append("${result.profiles.size} serveur(s) importé(s)")
        if (result.skipped > 0) {
            if (isNotEmpty()) append("\n")
            append("${result.skipped} doublon(s) ignoré(s)")
        }
        if (result.failures.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("${result.failures.size} échec(s) : ${result.failures.first()}")
        }
        if (isEmpty()) append("Aucun contenu importable trouvé")
    }

@Composable
private fun A2MServerActionChip(
    label: String,
    icon: ImageVector? = null,
    selected: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val feedbackContext = LocalContext.current
    val accent = if (danger) A2MColors.Danger else A2MColors.Accent
    Row(
        modifier =
        Modifier
            .clip(A2MShapes.chip)
            .background(if (selected) A2MColors.Tile else A2MColors.BackgroundMid)
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.7f) else A2MColors.CardBorder,
                A2MShapes.chip,
            )
            .clickable(enabled = enabled) { A2MClickFeedback.applyConfigured(feedbackContext); onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = if (enabled) accent else A2MColors.Faint,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) accent else A2MColors.Faint,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerRow(
    profile: Profile,
    protocol: String,
    code: String,
    address: String?,
    selected: Boolean,
    active: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    autoUpdateEnabled: Boolean?,
    onToggleAutoUpdate: () -> Unit,
    selectionMode: Boolean,
    checked: Boolean,
    size: ServerSize,
    testResult: String?,
    testing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val feedbackContext = LocalContext.current
    val badgeSize =
        when (size) {
            ServerSize.SMALL -> 34.dp
            ServerSize.MEDIUM -> 42.dp
            ServerSize.LARGE -> 52.dp
        }
    val verticalPadding =
        when (size) {
            ServerSize.SMALL -> 8.dp
            ServerSize.MEDIUM -> 14.dp
            ServerSize.LARGE -> 18.dp
        }
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(
                width = 1.dp,
                color = if (selected) A2MColors.CardBorderSelected else A2MColors.CardBorder,
                shape = A2MShapes.card,
            )
            .combinedClickable(
                onClick = { A2MClickFeedback.applyConfigured(feedbackContext); onClick() },
                onLongClick = { A2MClickFeedback.applyConfigured(feedbackContext); onLongClick() },
            )
            .padding(horizontal = 16.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectionMode) {
            Checkbox(checked = checked, onCheckedChange = { onClick() })
        }
        Box(
            modifier =
            Modifier
                .size(badgeSize)
                .clip(A2MShapes.tile)
                .background(if (code.isNotBlank()) protocolBadgeColor(code) else A2MColors.Tile),
            contentAlignment = Alignment.Center,
        ) {
            if (code.isNotBlank()) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = null,
                    tint = A2MColors.Accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = cleanServerDisplayName(profile.name, protocol),
                    style =
                    if (size == ServerSize.LARGE) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (active || selected) {
                    Text(
                        text = if (active) "ACTIF" else "SÉLECTIONNÉ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (active) A2MDesignTokens.success else A2MColors.Accent,
                        modifier =
                        Modifier
                            .clip(A2MShapes.chip)
                            .background(A2MColors.Tile)
                            .border(1.dp, A2MColors.Accent.copy(alpha = 0.5f), A2MShapes.chip)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = address?.takeIf { it.isNotBlank() }?.let(::countryLabel) ?: "$protocol · ${A2MProfileInfo.typeLabel(profile)}",
                style =
                if (size == ServerSize.SMALL) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = A2MColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (testResult != null || testing) {
                Text(
                    text = if (testing) "Test en cours…" else "TCP : $testResult",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color =
                    when {
                        testing -> A2MColors.Faint
                        testResult?.endsWith("ms") == true -> A2MColors.Accent
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
        }
        autoUpdateEnabled?.let { autoUpdate ->
            TextButton(onClick = onToggleAutoUpdate) {
                Text(
                    text = if (autoUpdate) "MàJ auto" else "MàJ off",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (autoUpdate) A2MColors.Accent else A2MColors.Muted,
                )
            }
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(40.dp)) {
            Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = if (favorite) "Retirer des favoris" else "Ajouter aux favoris", tint = if (favorite) A2MDesignTokens.warning else A2MColors.Muted)
        }
        IconButton(onClick = onMenu) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Actions",
                tint = A2MColors.Muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerGridCard(
    profile: Profile,
    protocol: String,
    code: String,
    address: String?,
    selected: Boolean,
    active: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    selectionMode: Boolean,
    checked: Boolean,
    size: ServerSize,
    testResult: String?,
    testing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedbackContext = LocalContext.current
    val badgeSize =
        when (size) {
            ServerSize.SMALL -> 30.dp
            ServerSize.MEDIUM -> 38.dp
            ServerSize.LARGE -> 46.dp
        }
    Column(
        modifier =
        modifier
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(
                1.dp,
                if (selected) A2MColors.CardBorderSelected else A2MColors.CardBorder,
                A2MShapes.card,
            )
            .combinedClickable(
                onClick = { A2MClickFeedback.applyConfigured(feedbackContext); onClick() },
                onLongClick = { A2MClickFeedback.applyConfigured(feedbackContext); onLongClick() },
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = checked, onCheckedChange = { onClick() })
            } else {
                Box(
                    modifier =
                    Modifier
                        .size(badgeSize)
                        .clip(A2MShapes.tile)
                        .background(if (code.isNotBlank()) protocolBadgeColor(code) else A2MColors.Tile),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = code.ifBlank { "?" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = if (favorite) "Retirer des favoris" else "Ajouter aux favoris", tint = if (favorite) A2MDesignTokens.warning else A2MColors.Muted)
            }
            if (active) {
                Text(
                    text = "ACTIF",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = A2MDesignTokens.success,
                )
            } else if (selected) {
                Text("SÉLECTIONNÉ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = A2MColors.Accent)
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Actions",
                    tint = A2MColors.Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = cleanServerDisplayName(profile.name, protocol),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = address?.takeIf { it.isNotBlank() }?.let(::countryLabel) ?: protocol,
            style = MaterialTheme.typography.labelSmall,
            color = A2MColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
            when {
                testing -> "Test en cours…"
                testResult != null -> "TCP : $testResult"
                else -> protocol
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color =
            when {
                testing -> A2MColors.Faint
                testResult?.endsWith("ms") == true -> A2MColors.Accent
                testResult != null -> MaterialTheme.colorScheme.error
                else -> A2MColors.Muted
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ServerTableHeader() {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.chip)
            .background(A2MColors.Tile)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "NOM",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Muted,
            modifier = Modifier.weight(1.5f),
        )
        Text(
            text = "PROTOCOLE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "STATUT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Muted,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerTableRow(
    profile: Profile,
    protocol: String,
    selected: Boolean,
    active: Boolean,
    selectionMode: Boolean,
    checked: Boolean,
    testResult: String?,
    testing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val feedbackContext = LocalContext.current
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(
                1.dp,
                if (selected) A2MColors.CardBorderSelected else A2MColors.CardBorder,
                A2MShapes.card,
            )
            .combinedClickable(
                onClick = { A2MClickFeedback.applyConfigured(feedbackContext); onClick() },
                onLongClick = { A2MClickFeedback.applyConfigured(feedbackContext); onLongClick() },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectionMode) {
            Checkbox(checked = checked, onCheckedChange = { onClick() })
        }
        Text(
            text = cleanServerDisplayName(profile.name, protocol),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = A2MColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f),
        )
        Text(
            text = protocol,
            style = MaterialTheme.typography.labelSmall,
            color = A2MColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
            when {
                active -> "ACTIF"
                selected -> "SÉLECTIONNÉ"
                testing -> "Test…"
                testResult != null -> testResult
                else -> "—"
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color =
            when {
                active -> A2MDesignTokens.success
                selected -> A2MColors.Accent
                testing -> A2MColors.Faint
                testResult?.endsWith("ms") == true -> A2MColors.Accent
                testResult != null -> MaterialTheme.colorScheme.error
                else -> A2MColors.Muted
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ImportOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = A2MColors.Accent,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }
    }
}

/** Champ de recherche compact, style pilule. */
@Composable
private fun A2MSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
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
            textStyle =
            MaterialTheme.typography.bodyMedium.copy(color = A2MColors.Text),
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

@Composable
private fun EmptyServersCard(onImport: () -> Unit) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
            Modifier
                .size(52.dp)
                .clip(A2MShapes.tile)
                .background(A2MColors.Tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.VpnKey,
                contentDescription = null,
                tint = A2MColors.Accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = "Aucun serveur",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Text,
        )
        Text(
            text = "Importez une configuration pour commencer.",
            style = MaterialTheme.typography.bodyMedium,
            color = A2MColors.Muted,
        )
        TextButton(onClick = onImport) {
            Text("Importer", color = A2MColors.Accent, fontWeight = FontWeight.Bold)
        }
    }
}
