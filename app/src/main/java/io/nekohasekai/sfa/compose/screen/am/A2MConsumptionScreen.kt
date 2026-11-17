package io.nekohasekai.sfa.compose.screen.am

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.bg.A2MTrafficRecorder
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.dams.traffic.DailyTraffic
import io.nekohasekai.sfa.dams.traffic.DailyTrafficLog
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Consommation journalière du tunnel A2M TUN.
 *
 * Source : A2MTrafficRecorder, qui enregistre les deltas réels des compteurs
 * du moteur (voir bg/ServiceNotification.kt). Conservation : 90 jours.
 */
@Composable
fun A2MConsumptionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<DailyTraffic>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh) {
        entries =
            withContext(Dispatchers.IO) {
                A2MTrafficRecorder.flush()
                DailyTrafficLog.parse(Settings.dailyTraffic)
            }
        loaded = true
    }

    val csvLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text =
                    withContext(Dispatchers.IO) {
                        DailyTrafficLog.csv(DailyTrafficLog.parse(Settings.dailyTraffic))
                    }
                exportMessage =
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use {
                                it.write(text.toByteArray())
                            } ?: error("flux indisponible")
                        }
                        "Export CSV enregistré."
                    } catch (e: Exception) {
                        "Export impossible : ${e.message ?: "erreur inconnue"}"
                    }
            }
        }

    val today = DailyTrafficLog.today()
    val todayEntry = DailyTrafficLog.series(entries, today, 1).first()
    val yesterdayEntry = DailyTrafficLog.series(entries, today, 2).first()
    val week = DailyTrafficLog.series(entries, today, 7)
    val month = DailyTrafficLog.series(entries, today, 30)
    val weekSum = DailyTrafficLog.sum(week)
    val monthSum = DailyTrafficLog.sum(month)
    val allSum = DailyTrafficLog.sum(entries)
    val lastDays = DailyTrafficLog.series(entries, today, 14).reversed()

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
        A2MConsumptionHeader(
            onBack = onBack,
            onRefresh = { refresh++ },
        )

        // ── Aujourd'hui / hier ──
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            A2MTrafficSummaryCard(
                title = "Aujourd'hui",
                entry = todayEntry,
                modifier = Modifier.weight(1f),
            )
            A2MTrafficSummaryCard(
                title = "Hier",
                entry = yesterdayEntry,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Cumuls ──
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            A2MTrafficTotalCard("7 jours", weekSum, Modifier.weight(1f))
            A2MTrafficTotalCard("30 jours", monthSum, Modifier.weight(1f))
            A2MTrafficTotalCard("Total", allSum, Modifier.weight(1f))
        }

        // ── Graphique 7 jours ──
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
                text = "7 derniers jours",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
            val maxDay = week.maxOfOrNull { it.total } ?: 0L
            week.forEach { day ->
                A2MDayBar(
                    label = shortLabel(day.date, today),
                    day = day,
                    maxTotal = maxDay,
                )
            }
        }

        // ── Détail 14 jours ──
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
                text = "Détail (14 jours)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
            if (loaded && lastDays.all { it.total == 0L }) {
                Text(
                    text =
                        "Aucune consommation enregistrée. Le suivi démarre dès que " +
                            "le tunnel est actif : les volumes réels du moteur sont " +
                            "cumulés par jour, même écran éteint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                )
            } else {
                lastDays.forEach { day ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = day.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = A2MColors.Muted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "↓ ${Libbox.formatBytes(day.download)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = A2MColors.Accent,
                        )
                        Text(
                            text = "  ↑ ${Libbox.formatBytes(day.upload)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = A2MColors.AccentUpload,
                        )
                        Text(
                            text = "  ${Libbox.formatBytes(day.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = A2MColors.Text,
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { csvLauncher.launch("a2m-consommation-$today.csv") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.IosShare,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = "  Exporter le suivi (CSV)")
        }
        exportMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }
        Text(
            text =
                "Les volumes sont ceux du moteur (tunnel). Conservation 90 jours, " +
                    "remise à zéro automatique des jours les plus anciens.",
            style = MaterialTheme.typography.bodySmall,
            color = A2MColors.Faint,
        )
    }
}

@Composable
private fun A2MConsumptionHeader(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Consommation",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
                Text(
                    text = "Téléchargements et téléversements quotidiens",
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2MColors.Muted,
                )
            }
            Text(
                text = "Actualiser",
                style = MaterialTheme.typography.labelLarge,
                color = A2MColors.Accent,
                modifier = Modifier.clickable(onClick = onRefresh),
            )
        }
    }
}

@Composable
private fun A2MTrafficSummaryCard(
    title: String,
    entry: DailyTraffic,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = A2MColors.Muted,
        )
        Text(
            text = Libbox.formatBytes(entry.total),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Text,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = A2MColors.Accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = " ${Libbox.formatBytes(entry.download)}",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
            Box(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Filled.Upload,
                contentDescription = null,
                tint = A2MColors.AccentUpload,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = " ${Libbox.formatBytes(entry.upload)}",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }
    }
}

@Composable
private fun A2MTrafficTotalCard(
    title: String,
    sum: Pair<Long, Long>,
    modifier: Modifier = Modifier,
) {
    val total = sum.first + sum.second
    Column(
        modifier =
        modifier
            .clip(A2MShapes.card)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, A2MShapes.card)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = Libbox.formatBytes(total),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = A2MColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun A2MDayBar(
    label: String,
    day: DailyTraffic,
    maxTotal: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = A2MColors.Muted,
            modifier = Modifier.width(52.dp),
        )
        val fraction = if (maxTotal <= 0L) 0f else (day.total.toFloat() / maxTotal).coerceIn(0f, 1f)
        Box(
            modifier =
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(A2MColors.Tile),
        ) {
            if (day.total > 0L) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(10.dp),
                ) {
                    val downFraction =
                        if (day.total <= 0L) 0f else day.download.toFloat() / day.total
                    Box(
                        Modifier
                            .weight(downFraction.coerceAtLeast(0.001f))
                            .height(10.dp)
                            .background(A2MColors.Accent),
                    )
                    Box(
                        Modifier
                            .weight((1f - downFraction).coerceAtLeast(0.001f))
                            .height(10.dp)
                            .background(A2MColors.AccentUpload),
                    )
                }
            }
        }
        Text(
            text = Libbox.formatBytes(day.total),
            style = MaterialTheme.typography.labelSmall,
            color = A2MColors.Text,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End,
        )
    }
}

/** Libellé court d'un jour : « Auj. », « Hier », sinon « JJ/MM ». */
private fun shortLabel(
    date: String,
    today: String,
): String =
    when (date) {
        today -> "Auj."
        else -> {
            val parts = date.split('-')
            if (parts.size == 3) "${parts[2]}/${parts[1]}" else date
        }
    }
