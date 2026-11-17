package io.nekohasekai.sfa.compose.screen.am

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.dams.transfer.A2MTransferManager
import io.nekohasekai.sfa.dams.transfer.TransferKind
import io.nekohasekai.sfa.dams.transfer.TransferRules
import io.nekohasekai.sfa.dams.transfer.TransferStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transferts de fichiers A2M TUN.
 *
 * Téléversement : fichier choisi sur le téléphone → serveur HTTP(S) (PUT/POST).
 * Téléchargement : URL → destination choisie par l'utilisateur (SAF).
 * Les transferts continuent quand on quitte l'écran (scope applicatif).
 */
@Composable
fun A2MTransfersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val context = LocalContext.current
    val progress by A2MTransferManager.progress.collectAsState()
    val history by A2MTransferManager.history.collectAsState()

    var uploadUrl by remember { mutableStateOf("") }
    var uploadMethod by remember { mutableStateOf("PUT") }
    var uploadToken by remember { mutableStateOf("") }
    var uploadUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var uploadName by remember { mutableStateOf<String?>(null) }
    var uploadSize by remember { mutableStateOf(-1L) }
    var uploadMime by remember { mutableStateOf("application/octet-stream") }

    var downloadUrl by remember { mutableStateOf("") }

    var formMessage by remember { mutableStateOf<String?>(null) }

    val openFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val (name, size) = A2MTransferManager.describe(uri)
            uploadUri = uri
            uploadName = name
            uploadSize = size
            uploadMime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            formMessage = null
        }

    val saveDestinationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val url = downloadUrl
            val name = TransferRules.fileNameFromUrl(url)
            A2MTransferManager.download(url, uri, name)
            formMessage = null
        }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── En-tête ──
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                text = "Transferts",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
            Text(
                text = "Télécharger et téléverser des fichiers depuis le téléphone",
                style = MaterialTheme.typography.bodyMedium,
                color = A2MColors.Muted,
            )
        }

        // ── Transfert en cours ──
        progress?.let { state ->
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector =
                        if (state.kind == TransferKind.UPLOAD) Icons.Filled.Upload else Icons.Filled.Download,
                        contentDescription = null,
                        tint = if (state.kind == TransferKind.UPLOAD) A2MColors.AccentUpload else A2MColors.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "  ${if (state.kind == TransferKind.UPLOAD) "Téléversement" else "Téléchargement"} · ${state.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = A2MColors.Text,
                    )
                }
                val fraction =
                    if (state.total > 0L) {
                        (state.transferred.toFloat() / state.total).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                if (state.isRunning) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text =
                        when (state.status) {
                            TransferStatus.RUNNING -> TransferRules.formatProgress(state.transferred, state.total)
                            else -> state.message ?: ""
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (state.status == TransferStatus.FAILED) {
                            A2MColors.Danger
                        } else {
                            A2MColors.Muted
                        },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isRunning) {
                        TextButton(onClick = { A2MTransferManager.cancel() }) {
                            Text(text = "Annuler", color = A2MColors.Danger)
                        }
                    } else {
                        TextButton(onClick = { A2MTransferManager.clear() }) {
                            Text(text = "Fermer")
                        }
                    }
                }
            }
        }

        // ── Téléversement ──
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
                text = "Téléverser un fichier",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
            OutlinedTextField(
                value = uploadUrl,
                onValueChange = {
                    uploadUrl = it
                    formMessage = null
                },
                label = { Text("Adresse du serveur (https://…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Méthode",
                    style = MaterialTheme.typography.labelLarge,
                    color = A2MColors.Muted,
                )
                listOf("PUT", "POST").forEach { method ->
                    Text(
                        text = method,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (uploadMethod == method) FontWeight.Bold else FontWeight.Normal,
                        color = if (uploadMethod == method) A2MColors.Accent else A2MColors.Muted,
                        modifier = Modifier.clickable { uploadMethod = method },
                    )
                }
            }
            OutlinedTextField(
                value = uploadToken,
                onValueChange = { uploadToken = it },
                label = { Text("Jeton Bearer (facultatif, non enregistré)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            uploadName?.let { name ->
                Text(
                    text =
                        "Fichier choisi : $name" +
                            if (uploadSize > 0) " (${TransferRules.formatBytes(uploadSize)})" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                )
            }
            OutlinedButton(
                onClick = { openFileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Upload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = "  Choisir un fichier…")
            }
            if (uploadUri != null) {
                OutlinedButton(
                    onClick = {
                        val url = TransferRules.normalizeUrl(uploadUrl)
                        if (url == null) {
                            formMessage = "Adresse invalide : http(s):// sans identifiants."
                            return@OutlinedButton
                        }
                        val uri = uploadUri
                        if (uri == null) {
                            formMessage = "Choisissez d'abord un fichier."
                            return@OutlinedButton
                        }
                        A2MTransferManager.upload(
                            uri = uri,
                            url = url,
                            method = uploadMethod,
                            bearerToken = uploadToken,
                            mimeType = uploadMime,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Envoyer vers le serveur")
                }
            }
            Text(
                text =
                    "Le fichier est envoyé par le téléphone vers votre serveur. " +
                        "HTTPS recommandé : en HTTP, l'URL et le contenu transitent en clair.",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Faint,
            )
        }

        // ── Téléchargement ──
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
                text = "Télécharger depuis une URL",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
            OutlinedTextField(
                value = downloadUrl,
                onValueChange = {
                    downloadUrl = it
                    formMessage = null
                },
                label = { Text("Adresse du fichier (https://…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    val url = TransferRules.normalizeUrl(downloadUrl)
                    if (url == null) {
                        formMessage = "Adresse invalide : http(s):// sans identifiants."
                        return@OutlinedButton
                    }
                    saveDestinationLauncher.launch(TransferRules.fileNameFromUrl(url))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = "  Choisir la destination et télécharger")
            }
            Text(
                text =
                    "Vous choisissez l'emplacement de destination ; le fichier est " +
                        "écrit au fur et à mesure, sans permission de stockage.",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Faint,
            )
        }

        formMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Danger,
            )
        }

        // ── Historique de session ──
        if (history.isNotEmpty()) {
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
                    text = "Historique de session",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
                val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                history.forEach { record ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = formatter.format(Date(record.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = A2MColors.Faint,
                        )
                        Text(
                            text =
                                "  ${if (record.kind == TransferKind.UPLOAD) "↑" else "↓"} " +
                                    "${record.name} · ${record.detail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (record.success) A2MColors.Muted else A2MColors.Danger,
                        )
                    }
                }
            }
        }
    }
}
