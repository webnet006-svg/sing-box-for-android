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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.database.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState

/**
 * Gestionnaire de serveurs A2M TUN : liste réelle des profils enregistrés,
 * sélection branchée sur le moteur, suppression.
 *
 * L'importation (fichier / lien / presse-papiers) est ajoutée à l'étape 3.
 */
@Composable
fun A2MServersScreen(
    dashboardViewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val scaffoldPadding = LocalScaffoldPadding.current

    var pendingDelete by remember { mutableStateOf<Profile?>(null) }
    var protocols by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    LaunchedEffect(uiState.profiles) {
        val profiles = uiState.profiles
        protocols =
            withContext(Dispatchers.IO) {
                profiles.associate { it.id to A2MProfileInfo.protocolLabel(it) }
            }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SERVEURS",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = A2MColors.Text,
                    )
                    Text(
                        text =
                        if (uiState.profiles.isEmpty()) {
                            "Aucune configuration enregistrée"
                        } else {
                            "${uiState.profiles.size} configuration(s) · ${uiState.selectedProfileName ?: "aucune sélectionnée"}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = A2MColors.Muted,
                    )
                }
            }

            if (uiState.profiles.isEmpty()) {
                item {
                    EmptyServersCard()
                }
            } else {
                items(uiState.profiles, key = { it.id }) { profile ->
                    ServerRow(
                        profile = profile,
                        protocol = protocols[profile.id] ?: A2MProfileInfo.typeLabel(profile),
                        selected = profile.id == uiState.selectedProfileId,
                        onClick = {
                            if (profile.id != uiState.selectedProfileId) {
                                dashboardViewModel.selectProfile(profile.id)
                            }
                        },
                        onDelete = { pendingDelete = profile },
                    )
                }
            }
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Supprimer le serveur ?", color = A2MColors.Text) },
            text = {
                Text(
                    "« ${profile.name} » sera supprimé de l'application.",
                    color = A2MColors.Muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dashboardViewModel.deleteProfile(profile)
                        pendingDelete = null
                    },
                ) {
                    Text("Supprimer", color = A2MColors.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Annuler", color = A2MColors.Muted)
                }
            },
        )
    }
}

@Composable
private fun ServerRow(
    profile: Profile,
    protocol: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(25.dp))
            .background(A2MColors.CardBrush)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) A2MColors.CardBorderSelected else A2MColors.CardBorder,
                shape = RoundedCornerShape(25.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(A2MColors.BackgroundMid),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.VpnKey,
                contentDescription = null,
                tint = A2MColors.Cyan,
                modifier = Modifier.size(26.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name.ifBlank { "Sans nom" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
            Text(
                text = "$protocol · ${A2MProfileInfo.typeLabel(profile)}",
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }

        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Sélectionné",
                tint = A2MColors.CardBorderSelected,
                modifier = Modifier.size(22.dp),
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Supprimer",
                tint = A2MColors.Muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyServersCard() {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(25.dp))
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, RoundedCornerShape(25.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.VpnKey,
            contentDescription = null,
            tint = A2MColors.Cyan,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = "Aucun serveur",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Text,
        )
        Text(
            text = "Les configurations importées apparaîtront ici.",
            style = MaterialTheme.typography.bodyMedium,
            color = A2MColors.Muted,
        )
    }
}
