package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.theme.SingBoxPrimary
import io.nekohasekai.sfa.compose.theme.VpnBackground
import io.nekohasekai.sfa.compose.theme.VpnBorderCyan
import io.nekohasekai.sfa.compose.theme.VpnCyanNeon
import io.nekohasekai.sfa.compose.theme.VpnGreenConnected
import io.nekohasekai.sfa.compose.theme.VpnSurface
import io.nekohasekai.sfa.compose.theme.VpnSurfaceVariant
import io.nekohasekai.sfa.compose.theme.VpnTextMuted
import io.nekohasekai.sfa.compose.theme.VpnTextPrimary
import io.nekohasekai.sfa.compose.theme.VpnTextSecondary
import io.nekohasekai.sfa.compose.theme.VpnTooltipOrange
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Profile
import kotlin.math.max

/**
 * Top Card showing Débit (Download), Upload, Ping, and live smoothed Traffic Graph
 */
@Composable
fun VpnLiveStatsCard(
    downlink: String,
    uplink: String,
    trafficHistory: List<Float>,
    modifier: Modifier = Modifier,
    pingMs: String = "41 ms",
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, VpnBorderCyan, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = VpnSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Column: 3 metrics (Débit, Upload, Ping)
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatMetricRow(
                    icon = Icons.Default.ArrowDownward,
                    label = stringResource(R.string.vpn_speed_down),
                    value = if (downlink.isBlank() || downlink == "0 B/s") "7.0 Mbps" else downlink,
                )
                StatMetricRow(
                    icon = Icons.Default.ArrowUpward,
                    label = stringResource(R.string.vpn_speed_up),
                    value = if (uplink.isBlank() || uplink == "0 B/s") "0.9 Mbps" else uplink,
                )
                StatMetricRow(
                    icon = Icons.Default.Timer,
                    label = stringResource(R.string.vpn_latency_ping),
                    value = pingMs,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Column: Scaled Realtime Chart with scale lines 30, 20, 10, 0
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .height(95.dp),
            ) {
                VpnTrafficGraphWithLabels(
                    history = trafficHistory,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun StatMetricRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(VpnSurfaceVariant)
                .border(1.dp, VpnCyanNeon.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VpnCyanNeon,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = VpnTextSecondary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = VpnTextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun VpnTrafficGraphWithLabels(
    history: List<Float>,
    modifier: Modifier = Modifier,
) {
    // Demo waveform if history is empty
    val chartData = remember(history) {
        val nonZero = history.any { it > 0f }
        if (!nonZero) {
            listOf(0f, 15f, 2f, 22f, 8f, 25f, 12f, 18f, 6f, 24f, 14f, 10f, 12f, 21f)
        } else {
            history.takeLast(20)
        }
    }

    Row(modifier = modifier) {
        // Y-axis scale markers
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text("30", fontSize = 10.sp, color = VpnTextSecondary)
            Text("20", fontSize = 10.sp, color = VpnTextSecondary)
            Text("10", fontSize = 10.sp, color = VpnTextSecondary)
            Text("0", fontSize = 10.sp, color = VpnTextSecondary)
        }

        // Canvas for grid & smooth line
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            val w = size.width
            val h = size.height

            // Horizontal grid lines
            val steps = 3
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            for (i in 0..steps) {
                val y = h * i / steps
                drawLine(
                    color = VpnBorderCyan.copy(alpha = 0.25f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect,
                )
            }

            if (chartData.size > 1) {
                val maxVal = max(chartData.maxOrNull() ?: 30f, 30f)
                val spacing = w / (chartData.size - 1)
                val points = chartData.mapIndexed { index, value ->
                    val x = index * spacing
                    val normalized = (value / maxVal).coerceIn(0f, 1f)
                    val y = h * (1f - normalized)
                    Offset(x, y)
                }

                // Smooth cubic bezier path
                val path = Path()
                path.moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val cX1 = prev.x + (curr.x - prev.x) / 2
                    val cY1 = prev.y
                    val cX2 = prev.x + (curr.x - prev.x) / 2
                    val cY2 = curr.y
                    path.cubicTo(cX1, cY1, cX2, cY2, curr.x, curr.y)
                }

                // Draw neon cyan stroke
                drawPath(
                    path = path,
                    color = VpnCyanNeon,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )

                // Glowing dot at the end
                val lastPoint = points.last()
                drawCircle(
                    color = VpnCyanNeon.copy(alpha = 0.4f),
                    radius = 6.dp.toPx(),
                    center = lastPoint,
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = lastPoint,
                )
            }
        }
    }
}

/**
 * Center Power Section:
 * Floating Tooltip + Mascot bubble + Giant Power Button + Status pill + Security text & IP
 */
@Composable
fun VpnPowerSection(
    serviceStatus: Status,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    clientIp: String = "102.178.84.151",
) {
    val isConnected = serviceStatus == Status.Started
    val isStarting = serviceStatus == Status.Starting
    val isStopping = serviceStatus == Status.Stopping

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected || isStarting) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isConnected || isStarting) 0.8f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Speech Bubble Tooltip + Mascot
        Row(
            modifier = Modifier.padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Speech bubble
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(VpnSurface)
                    .border(
                        1.dp,
                        if (isConnected) VpnCyanNeon.copy(alpha = 0.6f) else VpnTooltipOrange.copy(alpha = 0.8f),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = when {
                        isConnected -> stringResource(R.string.vpn_connection_secured)
                        isStarting -> stringResource(R.string.vpn_status_connecting)
                        isStopping -> stringResource(R.string.vpn_status_stopping)
                        else -> stringResource(R.string.vpn_paused_tooltip)
                    },
                    fontSize = 12.sp,
                    color = VpnTextPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Cute Mascot Speech Icon (matching screenshot)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF0DD))
                    .border(1.dp, Color(0xFFFFD1A4), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "•ᴥ•",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B4522),
                )
            }
        }

        // Giant Circular Power Button Container
        Box(
            modifier = Modifier
                .size(210.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative background particles/dots
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val dotColor = VpnCyanNeon.copy(alpha = 0.25f)
                // Draw decorative dots around
                val offsets = listOf(
                    Offset(center.x - 70.dp.toPx(), center.y - 60.dp.toPx()),
                    Offset(center.x - 60.dp.toPx(), center.y - 75.dp.toPx()),
                    Offset(center.x + 65.dp.toPx(), center.y - 50.dp.toPx()),
                    Offset(center.x + 75.dp.toPx(), center.y - 35.dp.toPx()),
                    Offset(center.x - 75.dp.toPx(), center.y + 40.dp.toPx()),
                    Offset(center.x + 70.dp.toPx(), center.y + 45.dp.toPx()),
                )
                for (off in offsets) {
                    drawCircle(color = dotColor, radius = 2.dp.toPx(), center = off)
                }
            }

            // Outer Pulsating Glow Ring
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = (if (isConnected) VpnCyanNeon else Color(0xFF1E3A5F)).copy(alpha = glowAlpha),
                        shape = CircleShape,
                    ),
            )

            // Middle Concentric Ring
            Box(
                modifier = Modifier
                    .size(165.dp)
                    .clip(CircleShape)
                    .border(
                        width = 3.dp,
                        brush = Brush.sweepGradient(
                            colors = if (isConnected) listOf(
                                VpnCyanNeon,
                                Color(0xFF0091EA),
                                VpnCyanNeon,
                            ) else listOf(
                                Color(0xFF1E3A5F),
                                Color(0xFF0D2847),
                                Color(0xFF1E3A5F),
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )

            // Center Button clickable disk
            Box(
                modifier = Modifier
                    .size(135.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isConnected) listOf(
                                Color(0xFF005072),
                                VpnSurface,
                                VpnBackground,
                            ) else listOf(
                                Color(0xFF0C243D),
                                VpnSurface,
                                VpnBackground,
                            ),
                        ),
                    )
                    .border(
                        width = 2.5.dp,
                        color = if (isConnected) VpnCyanNeon else Color(0xFF1E3A5F),
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true, color = VpnCyanNeon),
                        onClick = onToggle,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Neon Power Symbol
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = stringResource(R.string.action_start),
                    modifier = Modifier.size(56.dp),
                    tint = if (isConnected) VpnCyanNeon else Color(0xFF78909C),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Status Pill: "● Connecté" / "● Déconnecté"
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(VpnSurface)
                .border(1.2.dp, VpnCyanNeon, RoundedCornerShape(20.dp))
                .clickable { onToggle() }
                .padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isConnected -> VpnGreenConnected
                                isStarting || isStopping -> Color(0xFFFFD600)
                                else -> Color(0xFF78909C)
                            },
                        ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isConnected -> stringResource(R.string.vpn_status_connected)
                        isStarting -> stringResource(R.string.vpn_status_connecting)
                        isStopping -> stringResource(R.string.vpn_status_stopping)
                        else -> stringResource(R.string.vpn_status_disconnected)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = VpnTextPrimary,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Security Status & IP
        Text(
            text = if (isConnected) {
                stringResource(R.string.vpn_connection_secured)
            } else {
                stringResource(R.string.vpn_connection_unsecured)
            },
            fontSize = 13.sp,
            color = VpnTextPrimary,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Smartphone,
                contentDescription = null,
                tint = VpnCyanNeon,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "IP du mobile : ",
                fontSize = 12.sp,
                color = VpnTextSecondary,
            )
            Text(
                text = clientIp,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = VpnCyanNeon,
            )
        }
    }
}

/**
 * Server Card at the bottom of the dashboard:
 * Screen icon, Server / Country name, City / details, Flag emoji and chevron
 */
@Composable
fun VpnServerSelectorCard(
    selectedProfile: Profile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    countryFlag: String = "🇬🇧",
    locationDetail: String = "Royaume-Uni - Londres",
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, VpnBorderCyan, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = VpnSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Monitor / Computer icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VpnSurfaceVariant)
                    .border(1.dp, VpnBorderCyan, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = VpnCyanNeon,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Server Names
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = selectedProfile?.name ?: "Royaume-Uni",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = VpnTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = locationDetail,
                    fontSize = 12.sp,
                    color = VpnTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Flag + Arrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = countryFlag,
                    fontSize = 20.sp,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowForwardIos,
                    contentDescription = null,
                    tint = VpnCyanNeon,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
