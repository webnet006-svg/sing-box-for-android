package io.nekohasekai.sfa.compose.screen.am

import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.nekohasekai.sfa.compose.component.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic « Pourquoi la connexion échoue » : uniquement des données réelles —
 * alerte moteur active, vérifications effectives (profil, fichier de config,
 * permission VPN, permission notification), informations du profil sélectionné.
 */
@Composable
fun A2MDiagnosticsScreen(
    serviceStatus: Status,
    lastAlert: Pair<String, String?>?,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val context = LocalContext.current

    data class Check(val label: String, val detail: String, val ok: Boolean)
    data class ProfileInfo(val name: String, val type: String, val url: String, val updated: String, val fileOk: Boolean, val fileDetail: String)

    var checks by remember { mutableStateOf<List<Check>?>(null) }
    var profileInfo by remember { mutableStateOf<ProfileInfo?>(null) }

    LaunchedEffect(serviceStatus) {
        withContext(Dispatchers.IO) {
            val vpnGranted = VpnService.prepare(context) == null
            val notifGranted =
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

            val selectedId = io.nekohasekai.sfa.database.Settings.selectedProfile
            val profile = ProfileManager.get(selectedId)
            val profileSelected = selectedId != -1L && profile != null

            var fileOk = false
            var fileDetail = "—"
            if (profile != null) {
                try {
                    val content = File(profile.typed.path).readText()
                    fileOk = content.isNotBlank()
                    fileDetail =
                        if (fileOk) {
                            "Lisible · ${content.length / 1024 + 1} Ko"
                        } else {
                            "Fichier vide"
                        }
                } catch (e: Exception) {
                    fileDetail = "Illisible : ${e.message ?: e.javaClass.simpleName}"
                }
            } else {
                fileDetail = "Aucun profil sélectionné"
            }

            profileInfo =
                ProfileInfo(
                    name = profile?.name ?: "Aucun",
                    type = profile?.typed?.type?.name ?: "—",
                    url = profile?.typed?.remoteURL?.takeIf { it.isNotBlank() } ?: "—",
                    updated =
                    profile?.typed?.lastUpdated?.takeIf { it.time > 0 }?.let {
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it.time))
                    } ?: "—",
                    fileOk = fileOk,
                    fileDetail = fileDetail,
                )

            checks =
                listOf(
                    Check("Profil sélectionné", if (profileSelected) "Prêt à démarrer" else "Choisissez un serveur dans l'onglet Serveurs", profileSelected),
                    Check("Fichier de configuration", fileDetail, fileOk),
                    Check("Permission VPN", if (vpnGranted) "Accordée" else "Manquante · acceptez la demande système", vpnGranted),
                    Check("Permission de notification", if (notifGranted) "Accordée" else "Refusée · le tunnel reste actif mais sans notification", notifGranted),
                )
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
                title = "Diagnostic",
                subtitle = "Comprendre pourquoi la connexion échoue, avec les données réelles du moteur.",
                onBack = onBack,
            )
        }

        // Alerte moteur active : traduction + message brut copiable.
        item {
            val (title, advice) = lastAlert?.let { (type, _) -> alertTitle(type) to alertAdvice(type) }
                ?: ("Aucune alerte du moteur" to "Le dernier démarrage n'a pas signalé d'erreur.")
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(A2MShapes.card)
                    .background(A2MColors.CardBrush)
                    .border(1.dp, if (lastAlert != null) A2MColors.Danger.copy(alpha = 0.4f) else A2MColors.CardBorder, A2MShapes.card)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = if (lastAlert != null) A2MColors.Danger else A2MColors.Accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = A2MColors.Text,
                    )
                }
                Text(
                    text = advice,
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                lastAlert?.second?.takeIf { it.isNotBlank() }?.let { raw ->
                    Text(
                        text = raw,
                        style = MaterialTheme.typography.bodySmall,
                        color = A2MColors.Danger,
                        modifier =
                        Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(A2MColors.Tile)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }

        item { SubSectionLabel("Vérifications") }

        item {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(A2MShapes.card)
                    .background(A2MColors.CardBrush)
                    .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                val current = checks
                if (current == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = A2MColors.Accent,
                        )
                    }
                } else {
                    current.forEachIndexed { index, check ->
                        if (index > 0) HorizontalRule()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = if (check.ok) Icons.Filled.CheckCircle else Icons.Filled.Close,
                                contentDescription = null,
                                tint = if (check.ok) A2MColors.Accent else A2MColors.Danger,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = check.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = A2MColors.Text,
                                )
                                Text(
                                    text = check.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = A2MColors.Muted,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Profil sélectionné : informations réelles.
        profileInfo?.let { info ->
            item { SubSectionLabel("Profil actif") }
            item {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(A2MShapes.card)
                        .background(A2MColors.CardBrush)
                        .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    DiagnosticRow("Nom", info.name)
                    DiagnosticRow("Type", info.type)
                    DiagnosticRow("Abonnement", info.url)
                    DiagnosticRow("Dernière mise à jour", info.updated)
                    DiagnosticRow("Fichier", info.fileDetail)
                }
            }
        }

        // Actions réelles.
        item { SubSectionLabel("Actions") }
        item {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(A2MShapes.card)
                    .background(A2MColors.CardBrush)
                    .border(1.dp, A2MColors.CardBorder, A2MShapes.card),
            ) {
                ActionRow("Ouvrir le journal", "Messages du moteur en direct") { onNavigate(io.nekohasekai.sfa.compose.navigation.A2MRoutes.JOURNAL) }
                HorizontalRule()
                ActionRow("Rapports de plantage", "Rapports complets en cas de crash") { onNavigate("tools/crash_report") }
                if (serviceStatus == Status.Stopped) {
                    HorizontalRule()
                    ActionRow("Réessayer la connexion", "Relance le démarrage du tunnel", emphasized = true) { onRetry() }
                }
            }
        }
    }
}

private fun alertTitle(type: String): String =
    when (type) {
        "RequestVPNPermission" -> "Permission VPN manquante"
        "RequestNotificationPermission" -> "Permission de notification refusée"
        "RequestLocationPermission" -> "Permission de localisation requise"
        "EmptyConfiguration" -> "Aucune configuration exploitable"
        "StartCommandServer" -> "Erreur interne : serveur de commandes"
        "CreateService" -> "Erreur interne : création du service"
        "StartService" -> "Échec du démarrage du tunnel"
        else -> "Alerte moteur"
    }

private fun alertAdvice(type: String): String =
    when (type) {
        "RequestVPNPermission" -> "Relancez la connexion et acceptez la demande système."
        "RequestNotificationPermission" -> "Accordez la notification pour garder le tunnel visible."
        "RequestLocationPermission" -> "Nécessaire pour enregistrer les réseaux Wi-Fi de confiance."
        "EmptyConfiguration" -> "Choisissez un profil dans Serveurs ou importez un abonnement."
        "StartCommandServer", "CreateService" -> "Redémarrez l'application ; si l'erreur persiste, consultez les rapports."
        "StartService" -> "Vérifiez le serveur, vos identifiants et l'heure de l'appareil, puis réessayez."
        else -> ""
    }

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = A2MColors.Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = A2MColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HorizontalRule() {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
            .height(1.dp)
            .background(A2MColors.CardBorder),
    )
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
                color = if (emphasized) A2MColors.Accent else A2MColors.Text,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }
    }
}
