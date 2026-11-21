package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun A2MGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = A2MShapes.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(A2MColors.CardBrush)
            .border(1.dp, A2MColors.CardBorder, shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun A2MPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    danger: Boolean = false,
) {
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> A2MColors.Tile.copy(alpha = 0.55f)
            danger -> A2MColors.Danger.copy(alpha = 0.16f)
            selected -> A2MColors.Accent.copy(alpha = 0.18f)
            else -> A2MColors.Tile.copy(alpha = 0.82f)
        },
        animationSpec = tween(180),
        label = "pillBackground",
    )
    val foreground = when {
        !enabled -> A2MColors.Faint
        danger -> A2MColors.Danger
        selected -> A2MColors.Accent
        else -> A2MColors.Text
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(A2MShapes.chip)
            .background(background)
            .border(1.dp, foreground.copy(alpha = if (selected || danger) 0.42f else 0.2f), A2MShapes.chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, style = A2MType.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun A2MFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill by animateColorAsState(
        if (selected) A2MColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
        tween(180),
        label = "filterFill",
    )
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(A2MShapes.chip)
            .background(fill)
            .border(1.dp, if (selected) A2MColors.Accent.copy(alpha = 0.6f) else A2MColors.CardBorder, A2MShapes.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) A2MColors.Accent else A2MColors.Muted, style = A2MType.secondary, maxLines = 1)
    }
}

enum class A2MBadgeTone { ACCENT, SUCCESS, WARNING, ERROR, NEUTRAL }

@Composable
fun A2MStatusBadge(label: String, tone: A2MBadgeTone = A2MBadgeTone.ACCENT, modifier: Modifier = Modifier) {
    val color = when (tone) {
        A2MBadgeTone.ACCENT -> A2MColors.Accent
        A2MBadgeTone.SUCCESS -> A2MDesignTokens.success
        A2MBadgeTone.WARNING -> A2MColors.Amber
        A2MBadgeTone.ERROR -> A2MColors.Danger
        A2MBadgeTone.NEUTRAL -> A2MColors.Muted
    }
    Box(
        modifier = modifier
            .clip(A2MShapes.chip)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), A2MShapes.chip)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun A2MStyledSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, label: String) {
    val trackColor by animateColorAsState(
        if (checked) A2MColors.Accent.copy(alpha = 0.7f) else A2MColors.Tile,
        tween(180),
        label = "switchTrack",
    )
    val knobOffset by animateDpAsState(if (checked) 22.dp else 3.dp, tween(180), label = "switchKnob")
    Box(
        modifier = modifier
            .size(width = 56.dp, height = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            }
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(width = 48.dp, height = 28.dp)
                .clip(CircleShape)
                .background(trackColor)
                .border(1.dp, if (checked) A2MColors.Accent else A2MColors.CardBorder, CircleShape),
        )
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) A2MColors.Text else A2MColors.Muted),
        )
    }
}

@Composable
fun A2MScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable BoxScope.() -> Unit)? = null,
    action: (@Composable BoxScope.() -> Unit)? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        leading?.let {
            Box(contentAlignment = Alignment.Center, content = it)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = A2MColors.Text, style = A2MType.screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, color = A2MColors.Muted, style = A2MType.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        action?.let {
            Box(contentAlignment = Alignment.Center, content = it)
        }
    }
}

@Composable
fun A2MPingQuality(latencyMs: Long?, timedOut: Boolean = false, modifier: Modifier = Modifier) {
    val barCount = when {
        timedOut -> 0
        latencyMs == null -> 0
        latencyMs < 80 -> 4
        latencyMs < 150 -> 3
        latencyMs < 250 -> 2
        else -> 1
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(4) { index ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((5 + index * 3).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < barCount) A2MDesignTokens.success else A2MColors.Disabled),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = when {
                timedOut -> "✕ timeout"
                latencyMs == null -> "—"
                else -> "$latencyMs ms"
            },
            color = if (timedOut) A2MColors.Danger else if (latencyMs == null) A2MColors.Muted else A2MColors.Text,
            style = A2MType.secondary,
            maxLines = 1,
        )
    }
}
