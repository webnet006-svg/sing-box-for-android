package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.compose.screen.log.LogLevel
import io.nekohasekai.sfa.compose.screen.log.LogViewModel
import io.nekohasekai.sfa.compose.screen.log.ProcessedLogEntry
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.Status

private val A2M_FILTER_LEVELS =
    listOf(
        LogLevel.Default,
        LogLevel.ERROR,
        LogLevel.WARNING,
        LogLevel.INFO,
        LogLevel.DEBUG,
        LogLevel.TRACE,
    )

/**
 * Journal technique A2M TUN.
 * Réutilise LogViewModel : événements réels du moteur, filtrage par niveau,
 * recherche et effacement. Aucun événement n'est simulé.
 */
@Composable
fun A2MJournalScreen(
    serviceStatus: Status,
    modifier: Modifier = Modifier,
    logViewModel: LogViewModel = viewModel(),
) {
    val uiState by logViewModel.uiState.collectAsState()
    val scaffoldPadding = LocalScaffoldPadding.current
    val listState = rememberLazyListState()

    LaunchedEffect(serviceStatus) {
        logViewModel.updateServiceStatus(serviceStatus)
    }

    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            listState.scrollToItem(uiState.logs.lastIndex)
        }
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding)
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "JOURNAL",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = A2MColors.Text,
                )
                Text(
                    text =
                    when {
                        uiState.serviceStatus == Status.Started -> "Moteur connecté · ${uiState.logs.size} entrée(s)"
                        else -> "Moteur arrêté · flux en attente"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                )
            }
            TextButton(onClick = { logViewModel.clearLogs() }) {
                Text("Effacer", color = A2MColors.Muted)
            }
        }

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { logViewModel.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            singleLine = true,
            placeholder = { Text("Rechercher dans le journal") },
        )

        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            A2M_FILTER_LEVELS.forEach { level ->
                LevelChip(
                    label = level.label,
                    selected = uiState.filterLogLevel == level,
                    onClick = { logViewModel.setLogLevel(level) },
                )
            }
        }

        if (uiState.logs.isEmpty()) {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(A2MColors.CardBrush)
                    .padding(20.dp),
            ) {
                Text(
                    text = "Aucun événement. Démarrez le tunnel pour voir les journaux du moteur.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2MColors.Muted,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(uiState.logs, key = { it.id }) { log ->
                    LogLine(log)
                }
            }
        }
    }
}

@Composable
private fun LevelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier =
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) A2MColors.BackgroundMid else A2MColors.BackgroundTop),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) A2MColors.Cyan else A2MColors.Muted,
        )
    }
}

@Composable
private fun LogLine(log: ProcessedLogEntry) {
    Text(
        text = log.annotatedString,
        style =
        MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        ),
        color = levelColor(log.entry.level),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun levelColor(level: LogLevel): Color =
    when (level) {
        LogLevel.PANIC, LogLevel.FATAL, LogLevel.ERROR -> Color(0xFFFF7A90)
        LogLevel.WARNING -> Color(0xFFFFC46B)
        LogLevel.INFO -> A2MColors.Text
        LogLevel.DEBUG, LogLevel.TRACE -> A2MColors.Muted
        LogLevel.Default -> A2MColors.Text
    }
