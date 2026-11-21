package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Composants de contrôle partagés entre les sous-écrans Réglages A2M
 * (style SniTunnel : cartes radio, puces de raccourci, segments).
 */

@Composable
internal fun A2MRadioCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val border = if (selected) 1.5.dp else 1.dp
    val borderColor = if (selected) A2MColors.Accent else A2MColors.CardBorder
    val selectedBrush = Brush.linearGradient(listOf(A2MColors.Accent.copy(alpha = 0.10f), A2MColors.Accent.copy(alpha = 0.10f)))
    Column(
        modifier =
        modifier
            .clip(A2MShapes.card)
            .background(brush = if (selected) selectedBrush else A2MColors.CardBrush)
            .border(border, borderColor, A2MShapes.card)
            .clickable { A2MClickFeedback.applyConfigured(context); onClick() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) A2MColors.Accent else A2MColors.Text,
                modifier = Modifier.weight(1f),
            )
            RadioButtonRing(selected = selected)
        }
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = A2MColors.Muted,
            )
        }
    }
}

@Composable
private fun RadioButtonRing(selected: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier
            .size(20.dp)
            .border(2.dp, if (selected) A2MColors.Accent else A2MColors.Faint, CircleShape),
    ) {
        if (selected) {
            Box(
                modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(A2MColors.Accent),
            )
        }
    }
}

@Composable
internal fun A2MQuickChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier
            .clip(shape)
            .background(if (selected) A2MColors.Accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) A2MColors.Accent else A2MColors.CardBorder, shape)
            .clickable { A2MClickFeedback.applyConfigured(context); onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) A2MColors.Accent else A2MColors.Text,
        )
    }
}

/** Barre de segments (choix unique) façon SniTunnel. */
@Composable
internal fun A2MSegmented(
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (id, label) ->
            val selected = id == selectedId
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) A2MColors.Accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { A2MClickFeedback.applyConfigured(context); onSelect(id) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) A2MColors.Accent else A2MColors.Muted,
                )
            }
        }
    }
}

/** Badge pill d'état (ex. « GVISOR », « MUX : ON »). */
@Composable
internal fun A2MBadge(text: String, accent: Boolean = false) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (accent) A2MColors.Accent.copy(alpha = 0.18f) else A2MColors.CardBorder.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (accent) A2MColors.Accent else A2MColors.Muted,
        )
    }
}

/** Champ de texte sombre aligné sur le thème A2M. */
@Composable
internal fun A2MOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    number: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = A2MColors.Faint) },
        keyboardOptions =
        if (number) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        colors =
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = A2MColors.Accent,
            unfocusedBorderColor = A2MColors.CardBorder,
            focusedTextColor = A2MColors.Text,
            unfocusedTextColor = A2MColors.Text,
            cursorColor = A2MColors.Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
