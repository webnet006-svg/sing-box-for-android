package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Composants de réglages façon ONFAA, adaptés au design system A2M.
 * Utilisés par l'écran Paramètres et ses sous-écrans.
 */

/** Groupe de réglages : entête (tuile icône + titre en accent) + carte pleine largeur. */
@Composable
fun A2MSettingsGroup(
    title: String,
    icon: ImageVector?,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier =
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(A2MColors.Accent.copy(alpha = 0.14f))
                        .border(1.dp, A2MColors.Accent.copy(alpha = 0.28f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = A2MColors.Accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = A2MColors.Accent,
            )
        }
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .clip(A2MShapes.card)
                .background(A2MColors.CardBrush)
                .border(1.dp, A2MColors.CardBorder, A2MShapes.card),
        ) {
            content()
        }
    }
}

/** Ligne de réglage standard : icône teintée, titre, sous-titre, valeur, chevron optionnel. */
@Composable
fun A2MSettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    iconTint: Color = A2MColors.Accent,
    value: String? = null,
    valueColor: Color = A2MColors.Text,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                modifier =
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconTint.copy(alpha = 0.14f))
                    .border(1.dp, iconTint.copy(alpha = 0.28f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = A2MColors.Text,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = A2MColors.Faint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Ligne à interrupteur (option réelle ou mémorisée selon le contexte). */
@Composable
fun A2MSettingSwitchRow(
    icon: ImageVector?,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    iconTint: Color = A2MColors.Accent,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable {
                onCheckedChange(!checked)
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                modifier =
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconTint.copy(alpha = 0.14f))
                    .border(1.dp, iconTint.copy(alpha = 0.28f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = A2MColors.Text,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = A2MColors.Muted,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
            SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = A2MColors.Accent,
                uncheckedThumbColor = A2MColors.Muted,
                uncheckedTrackColor = A2MColors.Tile,
                uncheckedBorderColor = A2MColors.CardBorder,
            ),
        )
    }
}

/** Séparateur horizontal discret, avec retrait optionnel. */
@Composable
fun A2MDividerLine(inset: androidx.compose.ui.unit.Dp = 0.dp) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(1.dp)
            .background(A2MColors.CardBorder.copy(alpha = 0.45f)),
    )
}

/** Bandeau d'information (« honnêteté d'état », notices ONFAA). */
@Composable
fun A2MSettingsNotice(text: String) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(A2MColors.Accent.copy(alpha = 0.08f))
            .border(1.dp, A2MColors.Accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "i",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = A2MColors.Accent,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = A2MColors.Muted,
        )
    }
}

/** Carte d'information avec pastille ronde et message. */
@Composable
fun A2MInfoCard(
    title: String,
    message: String,
    icon: ImageVector? = null,
) {
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(A2MColors.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = A2MColors.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = "i",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = A2MColors.Accent,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = A2MColors.Muted,
        )
    }
}

/** Boîte de dialogue de choix unique (options radios). */
data class A2MChoiceOption(val value: String, val label: String, val description: String? = null)

@Composable
fun A2MChoiceDialog(
    title: String,
    options: List<A2MChoiceOption>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = A2MColors.Text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { option ->
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(option.value)
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option.value == selected,
                            onClick = null,
                            colors =
                            RadioButtonDefaults.colors(
                                selectedColor = A2MColors.Accent,
                                unselectedColor = A2MColors.Faint,
                            ),
                        )
                        Column {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight =
                                if (option.value == selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (option.value == selected) A2MColors.Accent else A2MColors.Text,
                            )
                            if (!option.description.isNullOrBlank()) {
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = A2MColors.Muted,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = A2MColors.Accent)
            }
        },
    )
}

/** Sélecteur de mode d'affichage : Système / Clair / Sombre. */
@Composable
fun A2MThemeModeSelector(
    selected: A2MThemeMode,
    onSelect: (A2MThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        A2MThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (isSelected) A2MColors.Accent else A2MColors.Tile)
                    .border(
                        1.dp,
                        if (isSelected) A2MColors.Accent else A2MColors.CardBorder,
                        RoundedCornerShape(99.dp),
                    )
                    .clickable(
                        role = Role.RadioButton,
                        onClickLabel = mode.label,
                    ) { onSelect(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else A2MColors.Muted,
                )
            }
        }
    }
}

/** Carte d'aperçu d'une palette (artwork décoratif + nom + coche si sélectionnée). */
@Composable
fun A2MThemePreviewCard(
    palette: A2MPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
        Modifier
            .aspectRatio(0.98f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(palette.tokens.backgroundTop, palette.tokens.backgroundMid),
                ),
            )
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) A2MColors.Accent else A2MColors.CardBorder.copy(alpha = 0.6f),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick),
    ) {
        // Pastilles décoratives aux couleurs de la palette.
        Row(
            modifier =
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(palette.tokens.accent),
            )
            Box(
                modifier =
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(palette.tokens.accentUpload),
            )
        }
        Box(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = palette.label + if (palette.isDark) " · sombre" else " · clair",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        if (selected) {
            Box(
                modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(23.dp)
                    .clip(CircleShape)
                    .background(A2MColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Sélectionnée",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Libellés d'état partagés entre l'écran Paramètres et les sous-écrans. */
internal fun backgroundBehaviorLabel(key: String): String =
    when (key) {
        "always" -> "Toujours actif"
        "optimized" -> "Optimisé"
        "stop" -> "Arrêt en arrière-plan"
        else -> key
    }

internal fun timeoutChoices(): List<A2MChoiceOption> =
    listOf(
        A2MChoiceOption("15", "15 s"),
        A2MChoiceOption("30", "30 s"),
        A2MChoiceOption("60", "60 s"),
        A2MChoiceOption("120", "120 s"),
    )

internal fun reconnectDelayChoices(): List<A2MChoiceOption> =
    listOf(
        A2MChoiceOption("3", "3 s"),
        A2MChoiceOption("5", "5 s"),
        A2MChoiceOption("10", "10 s"),
        A2MChoiceOption("30", "30 s"),
    )

internal fun backgroundBehaviorChoices(): List<A2MChoiceOption> =
    listOf(
        A2MChoiceOption("always", "Toujours actif", "Le tunnel reste actif"),
        A2MChoiceOption("optimized", "Optimisé", "Économise la batterie"),
        A2MChoiceOption("stop", "Arrêt", "Ferme le tunnel en arrière-plan"),
    )
