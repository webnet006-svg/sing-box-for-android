package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.compose.base.rememberApplyServiceChangeNotifier
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Réglages A2M TUN.
 * Uniquement des paramètres réellement appliqués par le service.
 */
@Composable
fun A2MSettingsScreen(
    serviceStatus: Status,
    modifier: Modifier = Modifier,
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)

    var dynamicNotification by remember { mutableStateOf(Settings.dynamicNotification) }
    var allowBypass by remember { mutableStateOf(Settings.allowBypass) }
    var serviceMode by remember { mutableStateOf(Settings.serviceMode) }

    LaunchedEffect(serviceStatus) {
        serviceMode =
            withContext(Dispatchers.IO) {
                Settings.detectServiceMode()
            }
    }
    val coreVersion = remember { runCatching { Libbox.version() }.getOrDefault("—") }

    LazyColumn(
        modifier =
        modifier
            .fillMaxSize()
            .background(A2MColors.BackgroundBrush)
            .padding(scaffoldPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Réglages",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = A2MColors.Text,
                )
                Text(
                    text = "Paramètres de l'application et du moteur",
                    style = MaterialTheme.typography.bodyMedium,
                    color = A2MColors.Muted,
                )
            }
        }

        item {
            SettingsCard(title = "Notifications") {
                SettingSwitch(
                    title = "Notification dynamique",
                    subtitle = "Affiche le débit et l'état du tunnel dans la notification",
                    checked = dynamicNotification,
                    onCheckedChange = {
                        dynamicNotification = it
                        Settings.dynamicNotification = it
                    },
                )
            }
        }

        item {
            SettingsCard(title = "Connexion") {
                SettingSwitch(
                    title = "Autoriser le contournement du VPN",
                    subtitle = "Autorise les applications à ignorer le tunnel (redémarrage requis)",
                    checked = allowBypass,
                    onCheckedChange = {
                        allowBypass = it
                        Settings.allowBypass = it
                        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                    },
                )
            }
        }

        item {
            SettingsCard(title = "Application") {
                SettingInfo("Nom", "A2M TUN")
                HorizontalDivider(color = A2MColors.CardBorder)
                SettingInfo("Version", BuildConfig.VERSION_NAME)
                HorizontalDivider(color = A2MColors.CardBorder)
                SettingInfo("Moteur", coreVersion)
                HorizontalDivider(color = A2MColors.CardBorder)
                SettingInfo(
                    "Mode",
                    if (serviceMode == ServiceMode.VPN) "VPN (TUN)" else "Proxy local",
                )
                HorizontalDivider(color = A2MColors.CardBorder)
                SettingInfo("Thème", "Sombre A2M")
            }
        }
    }
}

@Composable
private fun SettingsCard(
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
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = A2MColors.Text,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingInfo(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = A2MColors.Muted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Text,
        )
    }
}
