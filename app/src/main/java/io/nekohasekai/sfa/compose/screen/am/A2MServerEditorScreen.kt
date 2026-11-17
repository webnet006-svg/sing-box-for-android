package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.dams.importer.ClipboardImporter
import io.nekohasekai.sfa.dams.importer.Importer
import io.nekohasekai.sfa.dams.importer.ServerDraft
import io.nekohasekai.sfa.dams.importer.ServerDrafts
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Éditeur de serveur « en mode texte ».
 *
 * Saisie de champs lisibles (nom, adresse, port, UUID, TLS, transport) puis
 * génération de la configuration sing-box ; l'utilisateur n'a pas à écrire
 * de JSON. Un aperçu du JSON produit reste disponible à la demande.
 *
 * [profileId] null crée un nouveau serveur local, sinon le profil existant est
 * prérempli (profils locaux uniquement).
 */
@Composable
fun A2MServerEditorScreen(
    profileId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val importer = remember { ClipboardImporter(context) }

    var draft by remember { mutableStateOf(ServerDraft()) }
    var portText by remember { mutableStateOf("443") }
    var alterIdText by remember { mutableStateOf("0") }
    var loading by remember { mutableStateOf(profileId != null) }
    var isRemote by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var showExamples by remember { mutableStateOf(false) }

    fun applyDraft(next: ServerDraft) {
        draft = next
        portText = next.port.toString()
        alterIdText = next.alterId.toString()
    }

    LaunchedEffect(profileId) {
        if (profileId == null) {
            applyDraft(draft.copy(name = ""))
            loading = false
            return@LaunchedEffect
        }
        val loaded =
            withContext(Dispatchers.IO) {
                val profile = ProfileManager.get(profileId)
                if (profile == null) {
                    null
                } else {
                    val remote = profile.typed.type != TypedProfile.Type.Local
                    val content =
                        if (remote) "" else runCatching { File(profile.typed.path).readText() }.getOrDefault("")
                    Triple(profile.name, remote, content)
                }
            }
        if (loaded == null) {
            message = "Profil introuvable."
        } else {
            val (name, remote, content) = loaded
            isRemote = remote
            val parsed = if (remote) null else ServerDrafts.fromConfig(content)
            applyDraft((parsed ?: ServerDraft()).copy(name = name))
            if (remote) {
                message = "Profil d'abonnement : la configuration vient du serveur distant."
            } else if (parsed == null) {
                message = "Configuration actuelle non reconnue par l'éditeur ; les champs sont vides."
            }
        }
        loading = false
    }

    val securityOptions =
        when (draft.protocol) {
            Importer.Protocol.VLESS -> listOf("none", "tls", "reality")
            else -> listOf("none", "tls")
        }
    val preview =
        if (showPreview) {
            runCatching { ServerDrafts.buildConfig(draft) }.getOrElse { "Configuration incomplète : ${it.message}" }
        } else {
            ""
        }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
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
                    text = "Serveurs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = A2MColors.Accent,
                )
            }
            Text(
                text = if (profileId == null) "Créer un serveur" else "Modifier le serveur",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
            Text(
                text = "Saisie guidée, sans écrire de JSON",
                style = MaterialTheme.typography.bodyMedium,
                color = A2MColors.Muted,
            )
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // ── Protocole ──
        A2MEditorCard(title = "Protocole") {
            A2MChipRow(
                options =
                    listOf(
                        Importer.Protocol.VLESS.key to "VLESS",
                        Importer.Protocol.VMESS.key to "VMess",
                        Importer.Protocol.TROJAN.key to "Trojan",
                    ),
                selected = draft.protocol.key,
                onSelect = { key ->
                    val protocol =
                        when (key) {
                            Importer.Protocol.VMESS.key -> Importer.Protocol.VMESS
                            Importer.Protocol.TROJAN.key -> Importer.Protocol.TROJAN
                            else -> Importer.Protocol.VLESS
                        }
                    val security =
                        when {
                            protocol == Importer.Protocol.TROJAN && draft.security == "none" -> "tls"
                            else -> draft.security
                        }
                    applyDraft(draft.copy(protocol = protocol, security = security))
                    message = null
                },
            )
        }

        // ── Identité du serveur ──
        A2MEditorCard(title = "Serveur") {
            A2MText(
                label = "Nom (facultatif)",
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                placeholder = "Mon serveur",
            )
            A2MText(
                label = "Adresse",
                value = draft.host,
                onValueChange = { draft = draft.copy(host = it) },
                placeholder = "exemple.com",
            )
            A2MText(
                label = "Port",
                value = portText,
                onValueChange = { text ->
                    portText = text.filter { it.isDigit() }.take(5)
                    portText.toIntOrNull()?.let { draft = draft.copy(port = it) }
                },
                placeholder = "443",
                keyboardType = KeyboardType.Number,
            )
            A2MText(
                label = draft.identityLabel,
                value = draft.identity,
                onValueChange = { draft = draft.copy(identity = it) },
                placeholder =
                    if (draft.protocol == Importer.Protocol.TROJAN) {
                        "mot de passe"
                    } else {
                        "00000000-0000-0000-0000-000000000000"
                    },
                secret = true,
            )
        }

        // ── Sécurité ──
        A2MEditorCard(title = "Sécurité") {
            A2MChipRow(
                options = securityOptions.map { it to it.uppercase() },
                selected = draft.security,
                onSelect = { value ->
                    draft = draft.copy(security = value)
                    message = null
                },
            )
            if (draft.security != "none") {
                A2MText(
                    label = "SNI / nom du serveur (facultatif)",
                    value = draft.sni,
                    onValueChange = { draft = draft.copy(sni = it) },
                    placeholder = draft.host.ifBlank { "exemple.com" },
                )
                A2MText(
                    label = "ALPN (séparé par des virgules, facultatif)",
                    value = draft.alpn,
                    onValueChange = { draft = draft.copy(alpn = it) },
                    placeholder = "h2,http/1.1",
                )
                A2MText(
                    label = "Empreinte uTLS (facultatif)",
                    value = draft.fingerprint,
                    onValueChange = { draft = draft.copy(fingerprint = it) },
                    placeholder = "chrome",
                )
                A2MToggle(
                    label = "Accepter les certificats non valides",
                    checked = draft.allowInsecure,
                    onCheckedChange = { draft = draft.copy(allowInsecure = it) },
                )
            }
            if (draft.security == "reality") {
                A2MText(
                    label = "Clé publique REALITY",
                    value = draft.realityPublicKey,
                    onValueChange = { draft = draft.copy(realityPublicKey = it) },
                    placeholder = "clé publique",
                )
                A2MText(
                    label = "Short ID REALITY (facultatif)",
                    value = draft.realityShortId,
                    onValueChange = { draft = draft.copy(realityShortId = it) },
                    placeholder = "0123abcd",
                )
            }
            if (draft.protocol == Importer.Protocol.VMESS) {
                A2MChipRow(
                    options =
                        listOf(
                            "auto" to "Auto",
                            "aes-128-gcm" to "AES-128",
                            "chacha20-poly1305" to "ChaCha20",
                        ),
                    selected = draft.vmessSecurity,
                    onSelect = { draft = draft.copy(vmessSecurity = it) },
                )
                A2MText(
                    label = "Alter ID",
                    value = alterIdText,
                    onValueChange = { text ->
                        alterIdText = text.filter { it.isDigit() }.take(3)
                        draft = draft.copy(alterId = alterIdText.toIntOrNull() ?: 0)
                    },
                    placeholder = "0",
                    keyboardType = KeyboardType.Number,
                )
            }
        }

        // ── Transport ──
        A2MEditorCard(title = "Transport") {
            A2MChipRow(
                options =
                    listOf(
                        "tcp" to "TCP",
                        "ws" to "WebSocket",
                        "grpc" to "gRPC",
                        "httpupgrade" to "HTTPUpgrade",
                        "http" to "HTTP",
                    ),
                selected = draft.transport,
                onSelect = { draft = draft.copy(transport = it) },
                scrollable = true,
            )
            when (draft.transport) {
                "ws", "httpupgrade", "http" -> {
                    A2MText(
                        label = "Chemin",
                        value = draft.path,
                        onValueChange = { draft = draft.copy(path = it) },
                        placeholder = "/ws",
                    )
                    A2MText(
                        label = "En-tête Host (facultatif)",
                        value = draft.hostHeader,
                        onValueChange = { draft = draft.copy(hostHeader = it) },
                        placeholder = draft.host.ifBlank { "exemple.com" },
                    )
                }
                "grpc" -> {
                    A2MText(
                        label = "Nom du service gRPC",
                        value = draft.serviceName,
                        onValueChange = { draft = draft.copy(serviceName = it) },
                        placeholder = "grpc",
                    )
                }
            }
            if (draft.transport == "tcp" && draft.protocol == Importer.Protocol.VLESS) {
                A2MText(
                    label = "Flow (facultatif)",
                    value = draft.flow,
                    onValueChange = { draft = draft.copy(flow = it) },
                    placeholder = "xtls-rprx-vision",
                )
            }
            if (draft.transport == "http" && draft.security == "none") {
                Text(
                    text = "Le transport HTTP nécessite TLS : choisissez « TLS » ou « REALITY ».",
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Amber,
                )
            }
        }

        // ── Actions ──
        OutlinedButton(
            onClick = {
                if (saving || isRemote) return@OutlinedButton
                val config =
                    try {
                        ServerDrafts.buildConfig(draft)
                    } catch (e: Exception) {
                        message = e.message ?: "Configuration incomplète."
                        return@OutlinedButton
                    }
                scope.launch {
                    saving = true
                    message = null
                    try {
                        withContext(Dispatchers.IO) {
                            Libbox.checkConfig(config)
                            importer.saveLocalProfile(
                                profileId = profileId,
                                name = draft.name.trim(),
                                config = config,
                            )
                        }
                        onSaved()
                    } catch (e: Exception) {
                        message = "Enregistrement impossible : ${e.message ?: "erreur inconnue"}"
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = !saving && !isRemote,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (saving) "Enregistrement…" else "Enregistrer le serveur")
        }

        if (profileId == null) {
            TextButton(
                onClick = { showExamples = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Charger un exemple")
            }
        }

        OutlinedButton(
            onClick = { showPreview = !showPreview },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.Code,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = if (showPreview) "  Masquer le JSON" else "  Aperçu du JSON")
        }

        if (showPreview) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(A2MShapes.card)
                    .background(A2MColors.Tile)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Configuration générée",
                        style = MaterialTheme.typography.labelLarge,
                        color = A2MColors.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Copier",
                        style = MaterialTheme.typography.labelLarge,
                        color = A2MColors.Accent,
                        modifier = Modifier.clickable { clipboard.setText(AnnotatedString(preview)) },
                    )
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = A2MColors.Text,
                )
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("Enregistrement impossible")) A2MColors.Danger else A2MColors.Muted,
            )
        }

        Text(
            text =
                "Les champs restent sur le téléphone. Les secrets (UUID, mot de passe, " +
                    "clé REALITY) ne sont jamais journalisés.",
            style = MaterialTheme.typography.bodySmall,
            color = A2MColors.Faint,
        )
    }

    if (showExamples) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExamples = false },
            title = { Text("Exemples de configuration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Remplacez l'adresse, l'UUID et les clés par vos valeurs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = A2MColors.Muted,
                    )
                    ServerDrafts.examples.forEach { (label, example) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = A2MColors.Accent,
                            modifier =
                            Modifier.clickable {
                                applyDraft(example)
                                showExamples = false
                                message = null
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExamples = false }) {
                    Text(text = "Fermer")
                }
            },
        )
    }
}

@Composable
private fun A2MEditorCard(
    title: String,
    content: @Composable () -> Unit,
) {
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
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Text,
        )
        content()
    }
}

@Composable
private fun A2MText(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun A2MChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    scrollable: Boolean = false,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (key, label) ->
            val active = selected == key
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) A2MColors.Accent else A2MColors.Muted,
                modifier =
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) A2MColors.Tile else androidx.compose.ui.graphics.Color.Transparent)
                    .border(
                        1.dp,
                        if (active) A2MColors.CardBorderSelected else A2MColors.CardBorder,
                        RoundedCornerShape(999.dp),
                    )
                    .clickable { onSelect(key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun A2MToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = A2MColors.Text,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
