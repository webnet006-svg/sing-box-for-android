package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.navigation.A2MRoutes
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardUiState
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val A2M_IP_API_URL = "https://api.ipify.org?format=json"

private val A2M_ORB_BRUSH = Brush.radialGradient(listOf(Color(0xFF12283C), Color(0xFF081523)))
private val A2M_ORB_GLOW = Brush.radialGradient(listOf(Color(0x4D16E0C4), Color(0x0016E0C4)))
private val A2M_SPEED_BRUSH = Brush.linearGradient(listOf(Color(0xFF0C2B2C), Color(0xFF071A20)))

/** État « froid » de l'orbe : bleu désaturé, comme la référence. */
private val A2M_IDLE_RING = Color(0xFF3E6FA6)

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

private suspend fun fetchExitIp(): String? =

@Composable
fun A2MTUNHomeScreen(
    serviceStatus: Status,
    dashboardViewModel: DashboardViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    // Factory obligatoire : GroupsViewModel n'a pas de constructeur vide.
    val groupsViewModel: GroupsViewModel =
        viewModel(
            factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return GroupsViewModel(dashboardViewModel.commandClient) as T
                }
            },
        )
    val groupsState by groupsViewModel.uiState.collectAsState()
    val scaffoldPadding = LocalScaffoldPadding.current
    val coroutineScope = rememberCoroutineScope()

    var exitIp by remember { mutableStateOf<String?>(null) }
    var isRefreshingIp by remember { mutableStateOf(false) }
    var selectedProtocol by remember { mutableStateOf<String?>(null) }
    var selectedCode by remember { mutableStateOf("") }
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf(false) }

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
        selectedProtocol = null
        selectedCode = ""
        selectedAddress = null
        profile?.let { selected ->
            withContext(Dispatchers.IO) {
                selectedProtocol = A2MProfileInfo.protocolLabel(selected)
                selectedCode = A2MProfileInfo.protocolCode(selected)
                selectedAddress = A2MProfileInfo.serverAddress(selected)
            }
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

    // Latence réelle de sortie : élément sélectionné du premier groupe sélectionnable de la config active.
    val testableGroup = groupsState.groups.firstOrNull { it.selectable && it.items.isNotEmpty() }
    val exitDelay = testableGroup?.items?.firstOrNull { it.tag == testableGroup.selected }?.urlTestDelay ?: 0

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(horizontal = 20.dp),
        ) {
            // ── En-tête : marque + bouton journal ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(A2MColors.Tile)
                        .border(1.dp, A2MColors.CardBorderSelected.copy(alpha = 0.55f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = A2MColors.Accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = "A2M TUN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                    modifier = Modifier.padding(start = 10.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier =
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, A2MColors.CardBorder, RoundedCornerShape(12.dp))
                        .clickable { onNavigate(A2MRoutes.JOURNAL) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = "Journal",
                        tint = A2MColors.Muted,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            // ── Statut centré ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        modifier =
                        Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    hasFailed -> A2MColors.Danger
                                    isConnected -> A2MColors.Accent
                                    isBusy -> A2MColors.Amber
                                    else -> A2MColors.Faint
                                },
                            ),
                    )
                    Text(
                        text =
                        when {
                            hasFailed -> "Échec"
                            isConnected -> "Connecté"
                            isBusy -> "Connexion…"
                            else -> "Déconnecté"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = A2MColors.Text,
                    )
                }
                Text(
                    text =
                    when {
                        hasFailed -> "Échec de connexion · touchez pour réessayer"
                        isConnected -> "Prêt · tunnel actif"
                        isBusy -> "Négociation en cours…"
                        else -> "Prêt · touchez pour connecter"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2MColors.Muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(modifier = Modifier.weight(0.9f))

            // ── Orbe à graduations avec chrono de session ──
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

            Spacer(modifier = Modifier.weight(1.1f))

            // ── Carte config active façon SniTunnel : badge protocole + PING réel ──
            A2MConfigCard(
                hasServer = uiState.selectedProfileId != -1L,
                name = uiState.selectedProfileName,
                protocol = selectedProtocol,
                code = selectedCode,
                address = selectedAddress,
                exitDelay = exitDelay,
                isTesting = testableGroup != null && testableGroup.tag in groupsState.testingGroups,
                onClick = { onNavigate(A2MRoutes.SERVERS) },
                onPing = {
                    if (serviceStatus != Status.Started) {
                        toastMessage = "Démarrez le tunnel pour mesurer la latence"
                    } else {
                        testableGroup?.let { groupsViewModel.urlTestGroup(it.tag) }
                    }
                },
            )

            // ── Débits et stats : uniquement quand le tunnel est actif (comme la référence) ──
            if (isConnected) {
                Spacer(modifier = Modifier.height(10.dp))
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
                        modifier = Modifier.weight(1f),
                    )
                    A2MSpeedCard(
                        label = "Envoi",
                        icon = Icons.Filled.ArrowUpward,
                        speed = uiState.uplink,
                        total = uiState.uplinkTotal,
                        accent = A2MColors.AccentUpload,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                A2MIPCard(
                    exitIp = exitIp,
                    isRefreshing = isRefreshingIp,
                    onRefresh = {
                        coroutineScope.launch {
                            isRefreshingIp = true
                            exitIp = fetchExitIp()
                            isRefreshingIp = false
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
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
                    .padding(bottom = 110.dp)
                    .clip(A2MShapes.card)
                    .background(Color(0xF00B2231))
                    .border(1.dp, A2MColors.CardBorderSelected, A2MShapes.card)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

// ── Orbe de connexion à graduations radiales ───────────────────────────────

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
    val glowAlpha = if (isConnected) pulse else if (isBusy) 0.25f else 0f
    val stateColor by animateColorAsState(
        targetValue =
        when {
            hasFailed -> A2MColors.Danger
            isConnected || isBusy -> A2MColors.Accent
            else -> A2M_IDLE_RING
        },
        animationSpec = tween(300),
        label = "ring",
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Halo
        Box(
            modifier =
            Modifier
                .size(276.dp)
                .graphicsLayer { this.alpha = glowAlpha }
                .clip(CircleShape)
                .background(A2M_ORB_GLOW),
        )
        // Graduations radiales (cadran)
        Canvas(modifier = Modifier.size(260.dp)) {
            val strokeWidth = 2.dp.toPx()
            val radius = size.minDimension / 2f
            val inner = radius - 12.dp.toPx()
            val outer = radius - 4.dp.toPx()
            val ticks = 48
            repeat(ticks) { index ->
                rotate(angle = index * (360f / ticks)) {
                    drawLine(
                        color = stateColor.copy(alpha = if (index % 6 == 0) 0.85f else 0.38f),
                        start = Offset(center.x, center.y - inner),
                        end = Offset(center.x, center.y - outer),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        // Bouton principal : power + action + chrono (comme la référence)
        Box(
            modifier =
            Modifier
                .size(188.dp)
                .clip(CircleShape)
                .background(A2M_ORB_BRUSH)
                .border(2.dp, stateColor, CircleShape)
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
                        tint = if (isConnected) A2MColors.Accent else A2MColors.Text,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    text =
                    when {
                        isConnected -> formatSession(sessionSeconds)
                        isBusy -> "—:—"
                        else -> "CONNECTER"
                    },
                    style =
                    if (isConnected || isBusy) {
                        MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp)
                    },
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
                if (isConnected) {
                    Text(
                        text = "DÉCONNECTER",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                        color = A2MColors.Faint,
                    )
                }
            }
        }
    }
}

// ── Carte config active ─────────────────────────────────────────────────────

@Composable
private fun A2MConfigCard(
    hasServer: Boolean,
    name: String?,
    protocol: String?,
    code: String,
    address: String?,
    exitDelay: Int,
    isTesting: Boolean,
    onClick: () -> Unit,
    onPing: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Badge protocole coloré (code court réel)
        Box(
            modifier =
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (hasServer) protocolBadgeColor(code) else A2MColors.Tile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (hasServer && code.isNotBlank()) code else "—",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (hasServer) "Config prête" else "Aucune config",
                style = MaterialTheme.typography.labelSmall,
                color = A2MColors.Faint,
            )
            Text(
                text = name ?: "Choisir un serveur",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 3.dp),
            ) {
                if (protocol != null) {
                    Text(
                        text = protocol,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = A2MColors.Accent,
                        modifier =
                        Modifier
                            .clip(A2MShapes.chip)
                            .border(1.dp, A2MColors.Accent.copy(alpha = 0.45f), A2MShapes.chip)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = address ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Latence réelle de sortie si connue
        if (exitDelay > 0) {
            Text(
                text = "$exitDelay ms",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                when {
                    exitDelay <= 120 -> A2MColors.Accent
                    exitDelay <= 300 -> A2MColors.Amber
                    else -> A2MColors.Danger
                },
            )
        }
        // Bouton PING réel (test du groupe de sortie actif)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clickable(onClick = onPing),
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = A2MColors.Accent,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    tint = A2MColors.Accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text = "PING",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Accent,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = A2MColors.Faint,
            modifier = Modifier.size(18.dp),
        )
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .clip(A2MShapes.card)
            .background(A2M_SPEED_BRUSH)
            .border(1.dp, A2MColors.CardBorderSelected.copy(alpha = 0.55f), A2MShapes.card)
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

// ── IP de sortie ────────────────────────────────────────────────────────────

@Composable
private fun A2MIPCard(
    exitIp: String?,
    isRefreshing: Boolean,
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
                else -> "indisponible"
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
