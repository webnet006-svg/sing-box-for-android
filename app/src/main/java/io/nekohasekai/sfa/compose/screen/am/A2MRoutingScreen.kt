package io.nekohasekai.sfa.compose.screen.am

import android.content.Context
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.base.rememberApplyServiceChangeNotifier
import io.nekohasekai.sfa.compose.model.Group
import io.nekohasekai.sfa.compose.model.GroupItem
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.vendor.PackageQueryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Page Routage A2M TUN.
 *
 * Toutes les commandes présentes agissent sur le moteur réel :
 * - sélection de la sortie (groupes selector/urltest via le client de commandes)
 * - sortie par application (Settings.perAppProxy*, appliqué au VpnService)
 * Le DNS est affiché tel que déclaré par la configuration (aucune modification factice).
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
    val context = LocalContext.current
    val scaffoldPadding = LocalScaffoldPadding.current
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)

    val groupsState by groupsViewModel.uiState.collectAsState()
    val dashboardState by dashboardViewModel.uiState.collectAsState()

    LaunchedEffect(serviceStatus) {
        groupsViewModel.updateServiceStatus(serviceStatus)
    }

    val queryMode = Settings.perAppProxyPackageQueryMode
    val useRoot = queryMode == Settings.PACKAGE_QUERY_MODE_ROOT
    val shizukuReady by PackageQueryManager.shizukuBinderReady.collectAsState()
    val shizukuGranted by PackageQueryManager.shizukuPermissionGranted.collectAsState()
    val shizukuInstalled by PackageQueryManager.shizukuInstalled.collectAsState()
    val rootAvailable by PackageQueryManager.rootAvailable.collectAsState()
    val privilegeAvailable =
        if (useRoot) {
            rootAvailable == true
        } else {
            PackageQueryManager.showModeSelector && shizukuReady && shizukuGranted
        }

    // Suivi live de l'état Shizuku (même mécanisme que ProfileOverrideScreen).
    DisposableEffect(queryMode, useRoot) {
        if (PackageQueryManager.showModeSelector) {
            PackageQueryManager.registerListeners()
            PackageQueryManager.refreshShizukuState()
        }
        onDispose {
            if (PackageQueryManager.showModeSelector) {
                PackageQueryManager.unregisterListeners()
            }
        }
    }

    // Vérifie la disponibilité root dès l'entrée sur la page (met à jour le flux).
    LaunchedEffect(useRoot) {
        if (useRoot) {
            PackageQueryManager.checkRootAvailable()
        }
    }

    var perAppEnabled by remember { mutableStateOf(Settings.perAppProxyEnabled) }
    var perAppMode by remember { mutableStateOf(Settings.perAppProxyMode) }
    var bypassPrivate by remember { mutableStateOf(Settings.bypassPrivateNetworks) }
    var serviceMode by remember { mutableStateOf(Settings.serviceMode) }
    var showPrivilegeDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedApps by remember { mutableStateOf(Settings.perAppProxyList) }

    var dnsServers by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(dashboardState.selectedProfileId) {
        serviceMode =
            withContext(Dispatchers.IO) {
                Settings.detectServiceMode()
            }
    }

    LaunchedEffect(dashboardState.selectedProfileId, dashboardState.profiles) {
        val profile = dashboardState.profiles.firstOrNull { it.id == dashboardState.selectedProfileId }
        dnsServers =
            profile?.let { selected ->
                withContext(Dispatchers.IO) { A2MProfileInfo.dnsServers(selected) }
            } ?: emptyList()
    }

    LaunchedEffect(showAppPicker) {
        if (!showAppPicker) return@LaunchedEffect
        installedApps =
            withContext(Dispatchers.IO) {
                context.packageManager
                    .getInstalledPackages(0)
                    .asSequence()
                    .filter { it.packageName != context.packageName }
                    .map { info ->
                        info.packageName to
                            runCatching {
                                info.applicationInfo
                                    ?.let { appInfo ->
                                        context.packageManager.getApplicationLabel(appInfo).toString()
                                    } ?: info.packageName
                            }.getOrDefault(info.packageName)
                    }
                    .sortedBy { it.second.lowercase() }
                    .toList()
            }
        selectedApps = Settings.perAppProxyList
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
                        text =
                        if (serviceMode == ServiceMode.VPN) {
                            "Mode VPN (TUN) · sortie par ${dashboardState.selectedProfileName ?: "aucun serveur"}"
                        } else {
                            "Mode proxy local · sortie par ${dashboardState.selectedProfileName ?: "aucun serveur"}"
                        },
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
                SectionCard(title = "Routage par application") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        A2MRadioCard(
                            title = "Toutes les applications",
                            subtitle = "Chaque application passe par le VPN.",
                            selected = !perAppEnabled,
                            onClick = {
                                if (perAppEnabled) {
                                    perAppEnabled = false
                                    Settings.perAppProxyEnabled = false
                                    notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                                }
                            },
                        )
                        A2MRadioCard(
                            title = "VPN pour ces applications",
                            subtitle = "Les autres applications se connectent en direct.",
                            selected = perAppEnabled && perAppMode == Settings.PER_APP_PROXY_INCLUDE,
                            onClick = {
                                if (!privilegeAvailable) {
                                    showPrivilegeDialog = true
                                } else {
                                    perAppEnabled = true
                                    perAppMode = Settings.PER_APP_PROXY_INCLUDE
                                    Settings.perAppProxyEnabled = true
                                    Settings.perAppProxyMode = Settings.PER_APP_PROXY_INCLUDE
                                    notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                                }
                            },
                        )
                        A2MRadioCard(
                            title = "Pas de VPN pour ces applications",
                            subtitle = "Par exemple, les applications bancaires restent hors du tunnel.",
                            selected = perAppEnabled && perAppMode == Settings.PER_APP_PROXY_EXCLUDE,
                            onClick = {
                                if (!privilegeAvailable) {
                                    showPrivilegeDialog = true
                                } else {
                                    perAppEnabled = true
                                    perAppMode = Settings.PER_APP_PROXY_EXCLUDE
                                    Settings.perAppProxyEnabled = true
                                    Settings.perAppProxyMode = Settings.PER_APP_PROXY_EXCLUDE
                                    notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                                }
                            },
                        )
                        if (perAppEnabled) {
                            TextButton(onClick = { showAppPicker = true }) {
                                Text(
                                    text = "Choisir les applications (${Settings.perAppProxyList.size})",
                                    color = A2MColors.Accent,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Réseaux") {
                    SettingSwitch(
                        title = "Contourner les réseaux privés",
                        subtitle = "LAN et lien-local restent hors du tunnel. Requiert un redémarrage du tunnel.",
                        checked = bypassPrivate,
                        onCheckedChange = {
                            bypassPrivate = it
                            Settings.bypassPrivateNetworks = it
                            notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                        },
                    )
                }
            }

            item {
                SectionCard(title = "DNS") {
                    if (dnsServers.isEmpty()) {
                        SectionHint("Aucun serveur DNS déclaré dans la configuration ; le moteur utilise ses valeurs par défaut.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            dnsServers.take(6).forEach { server ->
                                Text(
                                    text = server,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = A2MColors.Text,
                                )
                            }
                            Text(
                                text = "Défini par la configuration du serveur (lecture seule).",
                                style = MaterialTheme.typography.bodySmall,
                                color = A2MColors.Muted,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPrivilegeDialog) {
        AlertDialog(
            onDismissRequest = { showPrivilegeDialog = false },
            title = { Text("Autorisation requise", color = A2MColors.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Le routage par application nécessite Shizuku ou l'accès root.",
                        color = A2MColors.Muted,
                    )
                    if (!useRoot && shizukuInstalled) {
                        Text(
                            if (shizukuReady) {
                                "Shizuku est prêt — demandez l'autorisation puis réessayez."
                            } else {
                                "Shizuku est installé mais non connecté. Ouvrez Shizuku puis réessayez."
                            },
                            color = A2MColors.Muted,
                        )
                    }
                }
            },
            confirmButton = {
                if (!useRoot && shizukuInstalled) {
                    TextButton(
                        onClick = {
                            PackageQueryManager.requestShizukuPermission()
                            PackageQueryManager.refreshShizukuState()
                            showPrivilegeDialog = false
                        },
                    ) {
                        Text("Demander l'autorisation Shizuku", color = A2MColors.Accent, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { showPrivilegeDialog = false }) {
                        Text("Compris", color = A2MColors.Accent, fontWeight = FontWeight.Bold)
                    }
                }
            },
        )
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Applications", color = A2MColors.Text) },
            text = {
                Box(modifier = Modifier.heightIn(max = 420.dp)) {
                    LazyColumn {
                        items(installedApps, key = { it.first }) { (packageName, label) ->
                            val checked = selectedApps.contains(packageName)
                            Row(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedApps =
                                            if (checked) {
                                                selectedApps - packageName
                                            } else {
                                                selectedApps + packageName
                                            }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedApps =
                                            if (isChecked) {
                                                selectedApps + packageName
                                            } else {
                                                selectedApps - packageName
                                            }
                                    },
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = A2MColors.Text,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        Settings.perAppProxyList = selectedApps
                        showAppPicker = false
                        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                    },
                ) {
                    Text("Enregistrer", color = A2MColors.Accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text("Annuler", color = A2MColors.Muted)
                }
            },
        )
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
