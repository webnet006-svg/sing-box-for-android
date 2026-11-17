package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.navigation.A2MRoutes
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardUiState
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val A2M_IP_API_URL = "https://api.ipify.org?format=json"

private val A2M_ORB_BRUSH = Brush.radialGradient(listOf(Color(0xFF0C2E33), Color(0xFF071A22)))
private val A2M_ORB_GLOW = Brush.radialGradient(listOf(Color(0x4D16E0C4), Color(0x0016E0C4)))
private val A2M_SPEED_BRUSH = Brush.linearGradient(listOf(Color(0xFF0C2B2C), Color(0xFF071A20)))

private suspend fun fetchExitIp(): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            HTTPClient().use { client ->
                JSONObject(client.getString(A2M_IP_API_URL)).optString("ip")
            }
        }.getOrNull()
    }

/** 4250 -> "1:10:50" ; 95 -> "1:35" */
private fun formatSession(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A2MTUNHomeScreen(
    serviceStatus: Status,
    dashboardViewModel: DashboardViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val scaffoldPadding = LocalScaffoldPadding.current
    val coroutineScope = rememberCoroutineScope()

    var exitIp by remember { mutableStateOf<String?>(null) }
    var isRefreshingIp by remember { mutableStateOf(false) }
    var selectedProtocol by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Chrono de session : démarre au passage réel à Started (donnée du moteur, pas un ornement).
    var connectedAt by remember { mutableLongStateOf(0L) }
    var sessionSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(serviceStatus) {
        when (serviceStatus) {
            Status.Started -> {
                connectedAt = System.currentTimeMillis()
                isRefreshingIp = true
                exitIp = fetchExitIp()
                isRefreshingIp = false
            }
            Status.Stopped -> {
                // L'IP affichée n'est plus celle de sortie : on ne garde rien de périmé.
                connectedAt = 0L
                sessionSeconds = 0L
                exitIp = null
            }
            else -> Unit
        }
    }

    LaunchedEffect(connectedAt > 0) {
        while (connectedAt > 0) {
            sessionSeconds = (System.currentTimeMillis() - connectedAt) / 1000
            delay(1000)
        }
    }

    LaunchedEffect(uiState.selectedProfileId, uiState.profiles) {
        val profile = uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }
        selectedProtocol =
            profile?.let { selected ->
                withContext(Dispatchers.IO) { A2MProfileInfo.protocolLabel(selected) }
            }
    }

    LaunchedEffect(toastMessage) {
        val message = toastMessage
        if (message != null) {
            delay(2600)
            toastMessage = null
        }
    }

    LaunchedEffect(Unit) {
        GlobalEventBus.events.collect { event ->
            if (event is UiEvent.ErrorMessage) {
                toastMessage = event.message
                connectionError = true
            }
        }
    }

    val isConnected = serviceStatus == Status.Started
    val isBusy = serviceStatus == Status.Starting || serviceStatus == Status.Stopping
    val hasFailed = connectionError && serviceStatus == Status.Stopped

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .align(Alignment.Center),
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .padding(horizontal = 20.dp),
            ) {
                // ── En-tête SniTunnel-like : statut + serveur actif tappable ──
                A2MHomeHeader(
                    status = serviceStatus,
                    hasFailed = hasFailed,
                    serverName = uiState.selectedProfileName,
                    protocol = selectedProtocol,
                    hasServer = uiState.selectedProfileId != -1L,
                    onServerClick = { onNavigate(A2MRoutes.SERVERS) },
                    onMenuClick = { showMenu = true },
                )

                Spacer(modifier = Modifier.weight(1.2f))

                // ── Orbe de connexion avec chrono de session ──
                A2MConnectionOrb(
                    isConnected = isConnected,
                    isBusy = isBusy,
                    hasFailed = hasFailed,
                    sessionSeconds = sessionSeconds,
                    onClick = {
                        connectionError = false
                        when (serviceStatus) {
                            Status.Stopped -> GlobalEventBus.tryEmit(UiEvent.RequestStartService)
                            Status.Starting, Status.Started -> BoxService.stop()
                            else -> Unit
                        }
                    },
                )

                Text(
                    text =
                    when {
                        hasFailed -> "Échec de connexion — vérifiez le serveur puis réessayez"
                        isConnected -> "Tunnel actif — trafic protégé"
                        isBusy -> "Négociation en cours…"
                        else -> "Touchez le bouton pour activer le tunnel"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Faint,
                )

                Spacer(modifier = Modifier.weight(1.4f))

                // ── Débits en direct ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    A2MSpeedCard(
                        label = "Réception",
                        icon = Icons.Filled.ArrowDownward,
                        speed = uiState.downlink,
                        total = uiState.downlinkTotal,
                        accent = A2MColors.Accent,
                        highlight = isConnected,
                        modifier = Modifier.weight(1f),
                    )
                    A2MSpeedCard(
                        label = "Envoi",
                        icon = Icons.Filled.ArrowUpward,
                        speed = uiState.uplink,
                        total = uiState.uplinkTotal,
                        accent = A2MColors.AccentUpload,
                        highlight = isConnected,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Mini-statistiques ──
                A2MMiniStatsRow(
                    totalDown = uiState.downlinkTotal,
                    totalUp = uiState.uplinkTotal,
                    sessionSeconds = sessionSeconds,
                    isConnected = isConnected,
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ── IP de sortie ──
                A2MIPCard(
                    exitIp = exitIp,
                    isRefreshing = isRefreshingIp,
                    isConnected = isConnected,
                    onRefresh = {
                        coroutineScope.launch {
                            isRefreshingIp = true
                            exitIp = fetchExitIp()
                            isRefreshingIp = false
                        }
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = Color(0xFF071520),
            contentColor = A2MColors.Text,
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Navigation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                A2MSheetItem("Accueil", A2MRoutes.HOME, Icons.Filled.Home, onClick = { showMenu = false; onNavigate(A2MRoutes.HOME) })
                A2MSheetItem("Serveurs", A2MRoutes.SERVERS, Icons.Outlined.Language, onClick = { showMenu = false; onNavigate(A2MRoutes.SERVERS) })
                A2MSheetItem("Routage", A2MRoutes.ROUTING, Icons.Filled.SwapVert, onClick = { showMenu = false; onNavigate(A2MRoutes.ROUTING) })
                A2MSheetItem("Journal", A2MRoutes.JOURNAL, Icons.Filled.Description, onClick = { showMenu = false; onNavigate(A2MRoutes.JOURNAL) })
                A2MSheetItem("Réglages", A2MRoutes.SETTINGS, Icons.Filled.Settings, onClick = { showMenu = false; onNavigate(A2MRoutes.SETTINGS) })
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    toastMessage?.let { message ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 96.dp)
                    .clip(A2MShapes.card)
                    .background(Color(0xF00B2231))
                    .border(1.dp, A2MColors.CardBorderSelected, A2MShapes.card)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

// ── En-tête : statut + serveur actif ───────────────────────────────────────

@Composable
private fun A2MHomeHeader(
    status: Status,
    hasFailed: Boolean,
    serverName: String?,
    protocol: String?,
    hasServer: Boolean,
    onServerClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val statusLabel =
        when {
            hasFailed -> "Échec de connexion"
            status == Status.Started -> "Connecté"
            status == Status.Starting -> "Connexion…"
            status == Status.Stopping -> "Déconnexion…"
            else -> "Déconnecté"
        }
    val statusColor =
        when {
            hasFailed -> A2MColors.Danger
            status == Status.Started -> A2MColors.Accent
            status == Status.Starting || status == Status.Stopping -> A2MColors.Amber
            else -> A2MColors.Faint
        }

    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onServerClick),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier =
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 17.dp, top = 2.dp),
            ) {
                Text(
                    text =
                    if (hasServer) {
                        listOfNotNull(serverName ?: "Serveur", protocol).joinToString(" · ")
                    } else {
                        "Aucun serveur — choisir"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasServer) A2MColors.Muted else A2MColors.Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = A2MColors.Faint,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Menu",
                tint = A2MColors.Muted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ── Orbe de connexion ───────────────────────────────────────────────────────

@Composable
private fun A2MConnectionOrb(
    isConnected: Boolean,
    isBusy: Boolean,
    hasFailed: Boolean,
    sessionSeconds: Long,
    onClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse",
    )
    val glowAlpha =
        when {
            isConnected -> pulse
            isBusy -> 0.28f
            else -> 0f
        }
    val ringColor by animateColorAsState(
        targetValue =
        when {
            hasFailed -> A2MColors.Danger
            isConnected || isBusy -> A2MColors.Accent
            else -> Color(0xFF274A52)
        },
        animationSpec = tween(300),
        label = "ring",
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Halo
        Box(
            modifier =
            Modifier
                .size(252.dp)
                .graphicsLayer { this.alpha = glowAlpha }
                .clip(CircleShape)
                .background(A2M_ORB_GLOW),
        )
        // Anneau décoratif
        Box(
            modifier =
            Modifier
                .size(224.dp)
                .clip(CircleShape)
                .border(1.dp, ringColor.copy(alpha = if (isConnected) pulse * 0.5f else 0.22f), CircleShape),
        )
        // Bouton principal : logo + action + chrono (comme SniTunnel)
        Box(
            modifier =
            Modifier
                .size(188.dp)
                .clip(CircleShape)
                .background(A2M_ORB_BRUSH)
                .border(2.dp, ringColor, CircleShape)
                .clickable(enabled = !isBusy, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = A2MColors.Accent,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isConnected) A2MColors.Accent else A2MColors.Muted,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                    when {
                        isConnected -> formatSession(sessionSeconds)
                        isBusy -> "—:—"
                        else -> "Connecter"
                    },
                    style =
                    if (isConnected || isBusy) {
                        MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
                if (isConnected) {
                    Text(
                        text = "Déconnexion",
                        style = MaterialTheme.typography.labelSmall,
                        color = A2MColors.Faint,
                    )
                }
            }
        }
    }
}

// ── Débits ──────────────────────────────────────────────────────────────────

@Composable
private fun A2MSpeedCard(
    label: String,
    icon: ImageVector,
    speed: String,
    total: String,
    accent: Color,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .clip(A2MShapes.card)
            .background(A2M_SPEED_BRUSH)
            .border(
                1.dp,
                if (highlight) A2MColors.CardBorderSelected.copy(alpha = 0.55f) else A2MColors.CardBorder,
                A2MShapes.card,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = A2MColors.Faint,
            )
        }
        Text(
            text = speed,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = total,
            style = MaterialTheme.typography.labelSmall,
            color = A2MColors.Faint,
        )
    }
}

@Composable
private fun A2MMiniStatsRow(
    totalDown: String,
    totalUp: String,
    sessionSeconds: Long,
    isConnected: Boolean,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        A2MMiniStat("↓ Total", totalDown, Modifier.weight(1f))
        A2MStatDivider()
        A2MMiniStat("↑ Total", totalUp, Modifier.weight(1f))
        A2MStatDivider()
        A2MMiniStat("Session", if (isConnected) formatSession(sessionSeconds) else "—", Modifier.weight(1f))
    }
}

@Composable
private fun A2MMiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = A2MColors.Faint,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = A2MColors.Text,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun A2MStatDivider() {
    Box(
        modifier =
        Modifier
            .height(30.dp)
            .width(1.dp)
            .background(A2MColors.CardBorder),
    )
}

// ── IP de sortie ────────────────────────────────────────────────────────────

@Composable
private fun A2MIPCard(
    exitIp: String?,
    isRefreshing: Boolean,
    isConnected: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Language,
            contentDescription = null,
            tint = A2MColors.Faint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "IP de sortie",
            style = MaterialTheme.typography.labelMedium,
            color = A2MColors.Faint,
        )
        Text(
            text =
            when {
                exitIp != null -> exitIp
                isRefreshing -> "…"
                isConnected -> "indisponible"
                else -> "—"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (exitIp != null) A2MColors.Text else A2MColors.Faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = A2MColors.Accent,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Actualiser l'IP",
                tint = A2MColors.Muted,
                modifier =
                Modifier
                    .size(18.dp)
                    .clickable(onClick = onRefresh),
            )
        }
    }
}

// ── Menu latéral (feuille) ──────────────────────────────────────────────────

@Composable
private fun A2MSheetItem(
    label: String,
    route: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val selected = route == A2MRoutes.HOME
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(A2MShapes.tile)
            .background(if (selected) A2MColors.Tile else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) A2MColors.Accent else A2MColors.Muted,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) A2MColors.Text else A2MColors.Muted,
        )
    }
}
