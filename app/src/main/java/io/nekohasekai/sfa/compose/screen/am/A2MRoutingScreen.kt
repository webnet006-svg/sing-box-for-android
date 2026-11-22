package io.nekohasekai.sfa.compose.screen.am

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.compose.model.Group
import io.nekohasekai.sfa.compose.model.GroupItem
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.Status

/**
 * Page Routage A2M TUN.
 *
 * La sélection d'une sortie s'applique aux groupes selector/urltest via le client de commandes.
 * Le routage par application et le bypass LAN sont désactivés pour garder un tunnel intégral.
 */
@Composable
fun A2MRoutingScreen(
    serviceStatus: Status,
    dashboardViewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
    groupsViewModel: GroupsViewModel =
        viewModel(
            factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return GroupsViewModel(dashboardViewModel.commandClient) as T
                }
            },
        ),
) {
    val scaffoldPadding = LocalScaffoldPadding.current

    val groupsState by groupsViewModel.uiState.collectAsState()
    val dashboardState by dashboardViewModel.uiState.collectAsState()

    LaunchedEffect(serviceStatus) {
        groupsViewModel.updateServiceStatus(serviceStatus)
    }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Routage",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = A2MColors.Text,
                    )
                    Text(
                        text = "Sortie par ${dashboardState.selectedProfileName ?: "aucun serveur"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = A2MColors.Muted,
                    )
                }
            }

            item {
                SectionCard(title = "Groupe de sortie") {
                    when {
                        serviceStatus != Status.Started ->
                            SectionHint("Démarrez le tunnel pour choisir le serveur de sortie.")
                        groupsState.groups.isEmpty() ->
                            SectionHint("Aucun groupe de sortie disponible pour cette configuration.")
                        else ->
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                groupsState.groups.forEach { group ->
                                    GroupBlock(
                                        group = group,
                                        isTesting = group.tag in groupsState.testingGroups,
                                        onSelect = { itemTag ->
                                            groupsViewModel.selectGroupItem(group.tag, itemTag)
                                        },
                                        onUrlTest = { groupsViewModel.urlTestGroup(group.tag) },
                                    )
                                }
                            }
                    }
                }
            }

            item {
                SectionCard(title = "Tunnel intégral") {
                    Text(
                        text = "Le tunnel VPN capture les applications et les réseaux IPv4/IPv6. " +
                            "Les exclusions d'applications, le bypass LAN et les sorties directes sont neutralisés. " +
                            "Les DNS déclarés dans chaque profil de serveur sont conservés. Un profil sans inbound TUN " +
                            "ne peut pas fournir de tunnel intégral et sera refusé au démarrage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = A2MColors.Muted,
                    )
                }
            }
        }
    }

}

@Composable
private fun SectionCard(
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
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Accent,
        )
        content()
    }
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = A2MColors.Muted,
    )
}

@Composable
private fun GroupBlock(
    group: Group,
    isTesting: Boolean,
    onSelect: (String) -> Unit,
    onUrlTest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.tag,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = group.displayType,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
            // Test de latence réel du groupe (commande libbox urlTestGroup).
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp,
                    color = A2MColors.Accent,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = "Tester la latence du groupe",
                    tint = A2MColors.Accent,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(20.dp)
                        .clickable(onClick = onUrlTest),
                )
            }
            // Sélection du plus rapide : réel (urlTestDelay mesuré), sinon lance le test.
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = "Sélectionner le plus rapide",
                tint = A2MColors.Amber,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp)
                    .clickable {
                        val fastest = group.items
                            .filter { it.urlTestDelay > 0 }
                            .minByOrNull { it.urlTestDelay }
                        if (fastest != null) {
                            onSelect(fastest.tag)
                        } else {
                            onUrlTest()
                        }
                    },
            )
        }
        if (group.selectable) {
            group.items.forEach { item ->
                GroupItemRow(
                    item = item,
                    selected = item.tag == group.selected,
                    onClick = { onSelect(item.tag) },
                )
            }
        } else {
            Text(
                text = "Sélection automatique · ${group.selected}",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }
        HorizontalDivider(color = A2MColors.CardBorder)
    }
}

@Composable
private fun GroupItemRow(
    item: GroupItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(A2MShapes.tile)
            .background(
                if (selected) {
                    A2MColors.BackgroundMid
                } else {
                    A2MColors.BackgroundTop
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) A2MColors.CardBorderSelected else A2MColors.Muted),
        )
        Text(
            text = item.tag,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = A2MColors.Text,
            modifier = Modifier.weight(1f),
        )
        if (item.urlTestDelay > 0) {
            // Latence colorée façon SniTunnel : vert rapide, ambre moyen, rouge lent.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier =
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                item.urlTestDelay <= 120 -> A2MColors.Accent
                                item.urlTestDelay <= 300 -> A2MColors.Amber
                                else -> A2MColors.Danger
                            },
                        ),
                )
                Text(
                    text = "${item.urlTestDelay} ms",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color =
                    when {
                        item.urlTestDelay <= 120 -> A2MColors.Accent
                        item.urlTestDelay <= 300 -> A2MColors.Amber
                        else -> A2MColors.Danger
                    },
                )
            }
        }
    }
}
