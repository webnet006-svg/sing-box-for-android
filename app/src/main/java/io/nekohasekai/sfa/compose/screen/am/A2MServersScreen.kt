package io.nekohasekai.sfa.compose.screen.am

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.bg.UpdateProfileWork
import io.nekohasekai.sfa.compose.screen.configuration.ProfileImportHandler
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.dams.importer.ClipboardImporter
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestionnaire de serveurs A2M TUN.
 *
 * - liste réelle des profils enregistrés (DashboardViewModel + base Room)
 * - sélection branchée sur le moteur (Settings.selectedProfile + serviceReload)
 * - suppression
 * - import depuis un fichier, un lien ou le presse-papiers
 *   (ProfilImportHandler / ClipboardImporter existants, aucune logique dupliquée)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A2MServersScreen(
    dashboardViewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val scaffoldPadding = LocalScaffoldPadding.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val importHandler = remember { ProfileImportHandler(context) }
    val clipboardImporter = remember { ClipboardImporter(context) }

    var pendingDelete by remember { mutableStateOf<Profile?>(null) }
    var protocols by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    var showImportSheet by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var subscriptionUrl by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.profiles) {
        val profiles = uiState.profiles
        protocols =
            withContext(Dispatchers.IO) {
                profiles.associate { it.id to A2MProfileInfo.protocolLabel(it) }
            }
    }

    fun importText(text: String) {
        coroutineScope.launch {
            isImporting = true
            when (val outcome = clipboardImporter.importText(text)) {
                is ClipboardImporter.Outcome.Success -> {
                    importMessage = importSummary(outcome.result)
                    // Ne proposer l'abonnement que si aucun serveur n'a été extrait du texte.
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

    val fileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    isImporting = true
                    when (val result = importHandler.importFromUri(uri)) {
                        is ProfileImportHandler.ImportResult.Success ->
                            importMessage = "Serveur importé : ${result.profile.name}"
                        is ProfileImportHandler.ImportResult.Error ->
                            importMessage = "Import impossible : ${result.message}"
                    }
                    isImporting = false
                }
            }
        }

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
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                "${uiState.profiles.size} configuration(s) · ${uiState.selectedProfileName ?: "aucune sélectionnée"}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = A2MColors.Muted,
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
                item {
                    EmptyServersCard(onImport = { showImportSheet = true })
                }
            } else {
                items(uiState.profiles, key = { it.id }) { profile ->
                    ServerRow(
                        profile = profile,
                        protocol = protocols[profile.id] ?: A2MProfileInfo.typeLabel(profile),
                        selected = profile.id == uiState.selectedProfileId,
                        autoUpdateEnabled =
                        if (profile.typed.type == TypedProfile.Type.Remote) {
                            profile.typed.autoUpdate
                        } else {
                            null
                        },
                        onToggleAutoUpdate = { toggleAutoUpdate(profile) },
                        onClick = {
                            if (profile.id != uiState.selectedProfileId) {
                                dashboardViewModel.selectProfile(profile.id)
                            }
                        },
                        onDelete = { pendingDelete = profile },
                    )
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
                    icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                    title = "Depuis un fichier",
                    subtitle = "Configuration sing-box (.json)",
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
                Box(modifier = Modifier.size(12.dp))
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
                                    importMessage = "Abonnement importé : ${outcome.result.profiles.firstOrNull()?.name ?: "ok"}"
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

@Composable
private fun ServerRow(
    profile: Profile,
    protocol: String,
    selected: Boolean,
    autoUpdateEnabled: Boolean?,
    onToggleAutoUpdate: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
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
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
            Modifier
                .size(42.dp)
                .clip(A2MShapes.tile)
                .background(A2MColors.Tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.VpnKey,
                contentDescription = null,
                tint = A2MColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name.ifBlank { "Sans nom" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
            Text(
                text = "$protocol · ${A2MProfileInfo.typeLabel(profile)}",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }

        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Sélectionné",
                tint = A2MColors.CardBorderSelected,
                modifier = Modifier.size(22.dp),
            )
        }

        autoUpdateEnabled?.let { autoUpdate ->
            TextButton(onClick = onToggleAutoUpdate) {
                Text(
                    text = if (autoUpdate) "MàJ auto" else "MàJ auto off",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (autoUpdate) A2MColors.Accent else A2MColors.Muted,
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Supprimer",
                tint = A2MColors.Muted,
                modifier = Modifier.size(20.dp),
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
