package io.nekohasekai.sfa.compose.screen.am

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private const val A2M_HOME_ROUTE = "a2m/home"
private const val A2M_IP_API_URL = "https://api.ipify.org?format=json"

private val A2M_BG_TOP = Color(0xFF02091A)
private val A2M_BG_MID = Color(0xFF03112A)
private val A2M_CYAN = Color(0xFF12E7FF)
private val A2M_TEXT = Color(0xFFF4F8FF)
private val A2M_MUTED = Color(0xFF9DB8DF)
private val A2M_CARD_BORDER = Color(0xFF1766BD)
private val A2M_SHIELD = Color(0xFF00EDFF)
private val A2M_ACCENT_UP = Color(0xFF9B5CFF)

private val A2M_BG_BRUSH = Brush.linearGradient(listOf(A2M_BG_TOP, A2M_BG_MID, A2M_BG_TOP))
private val A2M_WORLD_BRUSH = Brush.linearGradient(listOf(Color(0xFF02122E), Color(0xFF041736), Color(0xFF02122E)))
private val A2M_WORLD_FADE = Brush.radialGradient(listOf(Color(0x331A2B3C), A2M_BG_TOP))
private val A2M_CONNECT_BRUSH = Brush.radialGradient(listOf(Color(0xFF0B3C78), Color(0xFF061B3E), Color(0xFF020D25)))
private val A2M_CONNECT_GLOW_BRUSH = Brush.radialGradient(listOf(Color(0xFF087DFF), Color(0xFF005DFF)))
private val A2M_CARD_BRUSH = Brush.linearGradient(listOf(Color(0xFF071B5B), Color(0xFF04112A)))

private suspend fun fetchExitIp(): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            HTTPClient().use { client ->
                JSONObject(client.getString(A2M_IP_API_URL)).optString("ip")
            }
        }.getOrNull()
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

    LaunchedEffect(serviceStatus) {
        if (serviceStatus == Status.Started) {
            isRefreshingIp = true
            exitIp = fetchExitIp()
            isRefreshingIp = false
        } else if (serviceStatus == Status.Stopped) {
            // L'IP affichée n'est plus celle de sortie : on ne garde rien de périmé.
            exitIp = null
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
            delay(2200)
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

    val connectLabel =
        when {
            connectionError && serviceStatus == Status.Stopped -> "ÉCHEC DE CONNEXION"
            serviceStatus == Status.Stopped -> "CONNECTER"
            serviceStatus == Status.Starting -> "CONNEXION EN COURS"
            serviceStatus == Status.Started -> "CONNECTÉ"
            else -> "DÉCONNEXION"
        }
    val isConnected = serviceStatus == Status.Started
    val isBusy = serviceStatus == Status.Starting || serviceStatus == Status.Stopping
    val hint =
        when {
            connectionError && serviceStatus == Status.Stopped ->
                "Vérifiez le serveur sélectionné puis réessayez"
            isConnected -> "Appuyez pour arrêter le tunnel"
            else -> "Appuyez pour démarrer le tunnel"
        }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2M_BG_BRUSH),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 20.dp),
            ) {
                A2MTUNHeader(onMenuClick = { showMenu = true })

                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(A2M_WORLD_BRUSH),
                ) {
                    A2MWorldMap()

                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(270.dp, 270.dp),
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .background(A2M_CONNECT_GLOW_BRUSH),
                        )

                        Box(
                            modifier =
                            Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .border(3.dp, Color(0xFF08DFFF), CircleShape),
                        )

                        A2MConnectButton(
                            label = connectLabel,
                            isBusy = isBusy,
                            onClick = {
                                connectionError = false
                                when (serviceStatus) {
                                    Status.Stopped -> GlobalEventBus.tryEmit(UiEvent.RequestStartService)
                                    Status.Starting, Status.Started -> BoxService.stop()
                                    else -> Unit
                                }
                            },
                        )
                    }

                    Box(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .background(A2M_WORLD_FADE),
                    )
                }

                Text(
                    text = hint,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2M_MUTED,
                )

                A2MServerCard(
                    serverName = uiState.selectedProfileName ?: "Aucun serveur sélectionné",
                    protocol = selectedProtocol,
                    isSelected = uiState.selectedProfileId != -1L,
                    onClick = { onNavigate(A2MRoutes.SERVERS) },
                )

                A2MTrafficCard(uiState = uiState)

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
        }
    }

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = Color(0xFF03112A),
            contentColor = A2M_TEXT,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "A2M TUN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = A2M_TEXT,
                )
                Text(
                    text = "VPN Rapide & Sécurisé",
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2M_CYAN,
                )
                Spacer(modifier = Modifier.height(8.dp))
                A2MNavigationSheetItem(
                    label = "Accueil",
                    route = A2MRoutes.HOME,
                    selectedRoute = A2MRoutes.HOME,
                    onClick = { showMenu = false; onNavigate(A2MRoutes.HOME) },
                )
                A2MNavigationSheetItem(
                    label = "Serveurs",
                    route = A2MRoutes.SERVERS,
                    selectedRoute = A2MRoutes.SERVERS,
                    onClick = { showMenu = false; onNavigate(A2MRoutes.SERVERS) },
                )
                A2MNavigationSheetItem(
                    label = "Routage",
                    route = A2MRoutes.ROUTING,
                    selectedRoute = A2MRoutes.ROUTING,
                    onClick = { showMenu = false; onNavigate(A2MRoutes.ROUTING) },
                )
                A2MNavigationSheetItem(
                    label = "Journal",
                    route = A2MRoutes.JOURNAL,
                    selectedRoute = A2MRoutes.JOURNAL,
                    onClick = { showMenu = false; onNavigate(A2MRoutes.JOURNAL) },
                )
                A2MNavigationSheetItem(
                    label = "Réglages",
                    route = A2MRoutes.SETTINGS,
                    selectedRoute = A2MRoutes.SETTINGS,
                    onClick = { showMenu = false; onNavigate(A2MRoutes.SETTINGS) },
                )
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
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFF4F8FF),
                modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 11.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF102B50))
                    .border(1.dp, Color(0xFF168BD5), RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun A2MTUNHeader(
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Menu",
                tint = Color(0xFFDCF1FF),
            )
        }
        A2MLogo(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "A2M TUN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF4F8FF),
            )
            Text(
                text = "VPN Rapide & Sécurisé",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = A2M_CYAN,
            )
        }
    }
}

@Composable
private fun A2MLogo(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(64.dp)) {
        val shield =
            Path().apply {
                moveTo(40f, 3f)
                lineTo(73f, 19f)
                lineTo(70f, 55f)
                lineTo(40f, 84f)
                lineTo(10f, 55f)
                lineTo(7f, 19f)
                close()
            }
        drawPath(path = shield, color = A2M_SHIELD, style = Stroke(width = 8f))

        val inner =
            Path().apply {
                moveTo(40f, 15f)
                lineTo(62f, 26f)
                lineTo(60f, 51f)
                lineTo(40f, 70f)
                lineTo(20f, 51f)
                lineTo(18f, 26f)
                close()
            }
        drawPath(path = inner, color = Color(0xFF08234D), style = Stroke(width = 2f))

        val check =
            Path().apply {
                moveTo(31f, 28f)
                lineTo(45f, 28f)
                cubicTo(55f, 28f, 55f, 35f, 60f, 35f)
                cubicTo(65f, 35f, 44f, 52f, 28f, 52f)
                lineTo(31f, 52f)
                lineTo(31f, 28f)
                close()
                moveTo(40f, 36f)
                lineTo(41f, 54f)
                lineTo(45f, 54f)
                cubicTo(52f, 54f, 52f, 45f, 49f, 45f)
                cubicTo(51f, 45f, 47f, 45f, 42f, 36f)
                lineTo(38f, 36f)
                close()
            }
        drawPath(
            path = check,
            color = A2M_SHIELD,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun A2MWorldMap() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val mapWidth = size.width
        val mapHeight = size.height

        var x = 0.0f
        while (x < mapWidth) {
            var y = 0.0f
            while (y < mapHeight) {
                drawCircle(
                    center = Offset(x + 2.2f, y + 2.2f),
                    radius = 1.6f,
                    color = Color(0xFF08CFFF).copy(alpha = 0.55f),
                )
                y += 7f
            }
            x += 7f
        }

        val route1 = Path().apply {
            moveTo(65f, 119f)
            cubicTo(190f, 5f, 302f, 132f, 520f, 110f)
        }
        drawPath(path = route1, color = Color(0xFF00BFFF), style = Stroke(width = 1.2f))

        val route2 = Path().apply {
            moveTo(80f, 220f)
            cubicTo(270f, 25f, 400f, 120f, 470f, 225f)
        }
        drawPath(path = route2, color = Color(0xFF00BFFF), style = Stroke(width = 1.2f))

        val route3 = Path().apply {
            moveTo(150f, 68f)
            cubicTo(260f, 270f, 340f, 175f, 420f, 80f)
        }
        drawPath(path = route3, color = Color(0xFF00BFFF), style = Stroke(width = 1.2f))

        val route4 = Path().apply {
            moveTo(40f, 155f)
            cubicTo(270f, 300f, 400f, 222f, 560f, 145f)
        }
        drawPath(path = route4, color = Color(0xFF00BFFF), style = Stroke(width = 1.2f))
    }
}

@Composable
private fun A2MConnectButton(
    label: String,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(A2M_CONNECT_BRUSH)
            .border(3.dp, Color(0xFF08DFFF), CircleShape)
            .clickable(enabled = !isBusy) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(75.dp),
                    color = A2M_CYAN,
                    strokeWidth = 4.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = null,
                    tint = A2M_CYAN,
                    modifier = Modifier.size(75.dp),
                )
            }
            Spacer(modifier = Modifier.height(13.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF4F8FF),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun A2MServerCard(
    serverName: String,
    protocol: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(A2M_CARD_BRUSH)
            .border(1.dp, A2M_CARD_BORDER, RoundedCornerShape(25.dp)),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            Box(
                modifier =
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF08285A)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = null,
                    tint = Color(0xFF08DFFF),
                    modifier = Modifier.size(66.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = serverName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF4F8FF),
                )
                Text(
                    text =
                    if (isSelected) {
                        protocol?.let { "$it · Serveur sélectionné" } ?: "Serveur sélectionné"
                    } else {
                        "Touchez pour choisir un serveur"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2M_MUTED,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = A2M_MUTED,
            )
        }
    }
}

@Composable
private fun A2MTrafficCard(
    uiState: DashboardUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        shape = RoundedCornerShape(25.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = Color(0xFF071B5B),
        ),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            A2MStat(
                imageVector = Icons.Filled.ArrowDownward,
                value = uiState.downlink,
                total = uiState.downlinkTotal,
                accent = A2M_CYAN,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                Modifier
                    .padding(start = 12.dp)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color(0xFF1A5DA5)),
            )
            A2MStat(
                imageVector = Icons.Filled.ArrowUpward,
                value = uiState.uplink,
                total = uiState.uplinkTotal,
                accent = A2M_ACCENT_UP,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun A2MStat(
    imageVector: ImageVector,
    value: String,
    total: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF4F8FF),
        )
        Text(
            text = total,
            style = MaterialTheme.typography.bodyMedium,
            color = A2M_MUTED,
        )
    }
}

@Composable
private fun A2MIPCard(
    exitIp: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(A2M_CARD_BRUSH)
            .border(1.dp, A2M_CARD_BORDER, RoundedCornerShape(25.dp)),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 19.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = A2M_CYAN,
                modifier = Modifier.size(42.dp),
            )
            Text(
                text = "IP de sortie : ",
                style = MaterialTheme.typography.bodyLarge,
                color = A2M_MUTED,
            )
            Text(
                text = exitIp ?: "---",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = A2M_TEXT,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "↻",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = A2M_CYAN,
                modifier = Modifier.clickable { onRefresh() },
            )
        }
    }
}

@Composable
private fun A2MNavigationSheetItem(
    label: String,
    route: String,
    selectedRoute: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (route == selectedRoute) Color(0xFF0B3C78) else Color(0xFF071B5B))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        color = A2M_TEXT,
        style = MaterialTheme.typography.bodyLarge,
    )
}
