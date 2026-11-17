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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.navigation.A2MRoutes
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardUiState
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val A2M_ORB_BRUSH = Brush.radialGradient(listOf(Color(0xFF116D7C), Color(0xFF0A4B5A)))
private val A2M_ORB_GLOW = Brush.radialGradient(listOf(Color(0x3D0FA98F), Color(0x000FA98F)))

/** État « froid » de l'orbe : halo menthe, comme la référence. */
private val A2M_IDLE_RING = Color(0xFF7ED8CC)

/** 4250 -> "1:10:50" ; 95 -> "1:35" */
private fun formatSession(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun A2MTUNHomeScreen(
    serviceStatus: Status,
    dashboardViewModel: DashboardViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val scaffoldPadding = LocalScaffoldPadding.current

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf(false) }

    // IP réelle du réseau mobile : relue depuis le système toutes les 3 s tant que
    // l'écran est visible (fonctionne même sans data active).
    var cellularIp by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            cellularIp = withContext(Dispatchers.IO) { A2MNetworkInfo.readCellularIp() }
            delay(3000)
        }
    }

    // Chrono de session : démarre au passage réel à Started (donnée du moteur, pas un ornement).
    var connectedAt by remember { mutableLongStateOf(0L) }
    var sessionSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(serviceStatus) {
        when (serviceStatus) {
            Status.Started -> {
                connectedAt = System.currentTimeMillis()
            }
            Status.Stopped -> {
                connectedAt = 0L
                sessionSeconds = 0L
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
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
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

            // ── Orbe sous l'en-tête, façon référence ──
            Spacer(modifier = Modifier.height(4.dp))
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

            // ── Carte de statut centrée (carte blanche, façon référence) ──
            A2MStatusCard(
                hasFailed = hasFailed,
                isConnected = isConnected,
                isBusy = isBusy,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Trafic réseau : vitesses, graphique et compteurs (données réelles du moteur) ──
            A2MTrafficCard(
                downlink = uiState.downlink,
                uplink = uiState.uplink,
                downTotal = uiState.downlinkTotal,
                upTotal = uiState.uplinkTotal,
                downHistory = uiState.downlinkHistory,
                upHistory = uiState.uplinkHistory,
                connections = uiState.connectionsCount,
                onClick = { onNavigate(A2MRoutes.CONSUMPTION) },
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Configuration actuelle : nom du serveur + IP du réseau mobile ──
            A2MConfigNameCard(
                hasServer = uiState.selectedProfileId != -1L,
                name = uiState.selectedProfileName,
                cellularIp = cellularIp,
                onClick = { onNavigate(A2MRoutes.SERVERS) },
            )

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
                    .background(Color(0xF2FFFFFF))
                    .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
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
    val glowAlpha = if (isConnected) pulse else if (isBusy) 0.25f else 0.35f
    val stateColor by animateColorAsState(
        targetValue =
        when {
            hasFailed -> A2MColors.Danger
            isConnected -> Color(0xFF2BD4BC)
            isBusy -> A2MColors.Amber
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
                .size(240.dp)
                .graphicsLayer { this.alpha = glowAlpha }
                .clip(CircleShape)
                .background(A2M_ORB_GLOW),
        )
        // Graduations radiales (cadran)
        Canvas(modifier = Modifier.size(224.dp)) {
            val strokeWidth = 2.dp.toPx()
            val radius = size.minDimension / 2f
            val inner = radius - 12.dp.toPx()
            val outer = radius - 4.dp.toPx()
            val ticks = 48
            repeat(ticks) { index ->
                rotate(degrees = index * (360f / ticks)) {
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
                .size(156.dp)
                .clip(CircleShape)
                .background(A2M_ORB_BRUSH)
                .border(2.dp, stateColor, CircleShape)
                .clickable(enabled = !isBusy, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(38.dp),
                        color = Color.White,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(modifier = Modifier.height(7.dp))
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
                    color = Color.White,
                )
                if (isConnected) {
                    Text(
                        text = "DÉCONNECTER",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                        color = Color(0x9BD9F2EC),
                    )
                }
            }
        }
    }
}

// ── Carte de statut (carte blanche centrée, façon référence) ────────────────

@Composable
private fun A2MStatusCard(
    hasFailed: Boolean,
    isConnected: Boolean,
    isBusy: Boolean,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, RoundedCornerShape(26.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp),
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
                hasFailed -> "Échec de connexion · touchez l'orbe pour réessayer"
                isConnected -> "Prêt · tunnel actif"
                isBusy -> "Négociation en cours…"
                else -> "Prêt · touchez pour connecter"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = A2MColors.Muted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ── Carte trafic réseau : vitesses, graphique réel et compteurs ─────────────

@Composable
private fun A2MTrafficCard(
    downlink: String,
    uplink: String,
    downTotal: String,
    upTotal: String,
    downHistory: List<Float>,
    upHistory: List<Float>,
    connections: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // En-tête : titre + badge LIVE
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = null,
                tint = A2MColors.Accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Trafic réseau",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(A2MColors.Accent),
            )
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Accent,
            )
        }

        // Vitesses Download / Upload
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = A2MColors.Accent,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "Download",
                        style = MaterialTheme.typography.labelMedium,
                        color = A2MColors.Muted,
                    )
                }
                Text(
                    text = downlink,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = A2MColors.AccentUpload,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "Upload",
                        style = MaterialTheme.typography.labelMedium,
                        color = A2MColors.Muted,
                    )
                }
                Text(
                    text = uplink,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Graphique : historiques réels du moteur (30 échantillons)
        Canvas(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .padding(top = 10.dp),
        ) {
            val maxValue = (downHistory.maxOrNull() ?: 0f).coerceAtLeast((upHistory.maxOrNull() ?: 0f)).coerceAtLeast(1f)
            val stepX = if (downHistory.size > 1) size.width / (downHistory.size - 1) else size.width
            val usableHeight = size.height * 0.92f

            fun yOf(value: Float): Float = size.height - (value / maxValue) * usableHeight

            fun buildPath(history: List<Float>): Path {
                val path = Path()
                history.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = yOf(value)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                return path
            }

            // Aire teal remplie (download) + ligne fine
            if (downHistory.isNotEmpty()) {
                val downPath = buildPath(downHistory)
                val areaPath = Path().apply {
                    addPath(downPath)
                    lineTo((downHistory.size - 1) * stepX, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(listOf(Color(0x330FA98F), Color(0x000FA98F))),
                )
                drawPath(
                    path = downPath,
                    color = A2MColors.Accent,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            // Ligne upload (violet)
            if (upHistory.isNotEmpty()) {
                drawPath(
                    path = buildPath(upHistory),
                    color = A2MColors.AccentUpload,
                    style = Stroke(width = 1.6f.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        // Compteurs réels : Downloadé / Uploadé / Connexions
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrafficCounter("Downloadé", downTotal)
            VerticalRule()
            TrafficCounter("Uploadé", upTotal)
            VerticalRule()
            TrafficCounter("Connexions", connections.toString())
        }
    }
}

@Composable
private fun RowScope.TrafficCounter(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = A2MColors.Muted,
        )
    }
}

@Composable
private fun RowScope.VerticalRule() {
    Box(
        modifier =
        Modifier
            .width(1.dp)
            .height(30.dp)
            .background(A2MColors.CardBorder),
    )
}

// ── Carte configuration actuelle : nom du serveur sélectionné seulement ────

@Composable
private fun A2MConfigNameCard(
    hasServer: Boolean,
    name: String?,
    cellularIp: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Configuration actuelle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (hasServer) "Vérifiée" else "Aucune",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (hasServer) Color.White else A2MColors.Muted,
                modifier =
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (hasServer) A2MColors.Accent else A2MColors.Tile)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }

        Text(
            text = if (hasServer) (name?.takeIf { it.isNotBlank() } ?: "Sans nom") else "Choisir un serveur",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (hasServer) A2MColors.Text else A2MColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )

        // IP réelle du réseau mobile : lue sur l'interface cellulaire de l'appareil
        // (rmnet/ccmni/wwan…), présente dès l'enregistrement au réseau, même sans data.
        Text(
            text = "IP du réseau mobile",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = A2MColors.Muted,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = cellularIp ?: "—",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (cellularIp != null) A2MColors.Accent else A2MColors.Faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
