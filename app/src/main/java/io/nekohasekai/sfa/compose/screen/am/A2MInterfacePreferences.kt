package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.database.Settings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun A2MInterfacePreferences() {
    val context = LocalContext.current
    var soundsEnabled by remember { mutableStateOf(Settings.a2mSoundsEnabled) }
    var silenceMode by remember { mutableStateOf(Settings.a2mSilenceMode) }
    var vibrationsEnabled by remember { mutableStateOf(Settings.a2mVibrationsEnabled) }
    var selectedSound by remember { mutableStateOf(Settings.a2mClickSoundId) }

    A2MSettingsGroup(title = "Polices d'écriture", icon = Icons.Filled.FontDownload) {
        A2MFontCatalog.choices.forEachIndexed { index, choice ->
            if (index > 0) A2MDividerLine(inset = 14.dp)
            val selected = A2MThemeState.fontId == choice.id
            Row(
                modifier = Modifier.fillMaxWidth().clickable { A2MThemeState.setFont(choice.id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = choice.label,
                        fontFamily = A2MFontCatalog.family(choice.id),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) A2MColors.Accent else A2MColors.Text,
                    )
                    Text(
                        text = "A2M TUN · Connexion · Réglages",
                        fontFamily = A2MFontCatalog.family(choice.id),
                        style = MaterialTheme.typography.bodySmall,
                        color = A2MColors.Muted,
                    )
                }
                if (selected) Icon(Icons.Filled.Check, "Police sélectionnée", tint = A2MColors.Accent)
            }
        }
    }

    A2MSettingsGroup(title = "Animation du bouton connecté", icon = Icons.Filled.Palette) {
        A2MOrbAnimations.choices.forEachIndexed { index, choice ->
            if (index > 0) A2MDividerLine(inset = 14.dp)
            val selected = A2MThemeState.orbAnimationId == choice.id
            Row(
                modifier = Modifier.fillMaxWidth().clickable { A2MThemeState.setOrbAnimation(choice.id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(choice.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = if (selected) A2MColors.Accent else A2MColors.Text)
                    Text(choice.description, style = MaterialTheme.typography.bodySmall, color = A2MColors.Muted)
                }
                A2MOrbAnimationPreview(choice.id, selected)
                if (selected) Icon(Icons.Filled.Check, "Animation sélectionnée", tint = A2MColors.Accent)
            }
        }
    }

    A2MSettingsGroup(title = "Sons, silence et vibrations", icon = Icons.Filled.Vibration) {
        A2MSettingSwitchRow(
            icon = Icons.Filled.MusicNote,
            title = "Sons",
            subtitle = "Jouer le son choisi lors des interactions",
            checked = soundsEnabled,
            onCheckedChange = { soundsEnabled = it; Settings.a2mSoundsEnabled = it },
        )
        A2MDividerLine(inset = 14.dp)
        A2MSettingSwitchRow(
            icon = Icons.Filled.MusicNote,
            title = "Silence",
            subtitle = "Couper les sons sans désactiver les vibrations",
            checked = silenceMode,
            onCheckedChange = { silenceMode = it; Settings.a2mSilenceMode = it },
        )
        A2MDividerLine(inset = 14.dp)
        A2MSettingSwitchRow(
            icon = Icons.Filled.Vibration,
            title = "Vibrations",
            subtitle = "Retour tactile indépendant des sons",
            checked = vibrationsEnabled,
            onCheckedChange = { vibrationsEnabled = it; Settings.a2mVibrationsEnabled = it },
        )
        A2MDividerLine(inset = 14.dp)
        Text(
            text = "Choisir et écouter un son de clic",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = A2MColors.Muted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        A2MClickFeedback.sounds.forEachIndexed { index, sound ->
            if (index > 0) A2MDividerLine(inset = 14.dp)
            val selected = selectedSound == sound.id
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    selectedSound = sound.id
                    Settings.a2mClickSoundId = sound.id
                }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(sound.label, style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) A2MColors.Accent else A2MColors.Text,
                    modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Filled.Check, "Son sélectionné", tint = A2MColors.Accent)
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .clickable { A2MClickFeedback.previewSound(context, sound.id) }
                        .background(A2MColors.Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, "Écouter ${sound.label}", tint = A2MColors.Accent)
                }
            }
        }
    }
}

@Composable
private fun A2MOrbAnimationPreview(id: String, selected: Boolean) {
    var alpha = 0.65f
    var rotation = 0f
    var scaleX = 1f
    var scaleY = 1f
    if (selected && id != "steady") {
        val transition = rememberInfiniteTransition(label = "orb-preview-$id")
        when (id) {
            "pulse" -> {
                val phase by transition.animateFloat(0.28f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "preview-pulse")
                alpha = phase
            }
            "orbit" -> {
                val rotate by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(7200, easing = LinearEasing), RepeatMode.Restart), label = "preview-orbit")
                rotation = rotate
            }
            "wave" -> {
                val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart), label = "preview-wave")
                val angle = phase * 2f * PI.toFloat()
                scaleX = 1f + 0.12f * sin(angle)
                scaleY = 1f + 0.12f * cos(angle)
            }
            "spark" -> {
                val flash by transition.animateFloat(
                    0.24f,
                    0.24f,
                    infiniteRepeatable(
                        keyframes {
                            durationMillis = 1100
                            0.24f at 0
                            1f at 90
                            0.3f at 180
                            0.92f at 270
                            0.24f at 390
                            0.24f at 1100
                        },
                        RepeatMode.Restart,
                    ),
                    label = "preview-spark",
                )
                alpha = flash
            }
            "breathe" -> {
                val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "preview-breathe")
                scaleX = 0.88f + phase * 0.24f
                scaleY = scaleX
                alpha = 0.55f + phase * 0.3f
            }
            "scan" -> {
                val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "preview-scan")
                rotation = progress * 360f
            }
        }
    }
    Box(
        modifier = Modifier.size(38.dp).graphicsLayer {
            rotationZ = rotation
            this.alpha = alpha
            this.scaleX = scaleX
            this.scaleY = scaleY
        }.border(2.dp, if (selected) A2MColors.Accent else A2MColors.CardBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(A2MColors.Accent))
    }
}
