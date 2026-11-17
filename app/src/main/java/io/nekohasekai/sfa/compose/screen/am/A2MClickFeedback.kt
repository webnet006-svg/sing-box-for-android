package io.nekohasekai.sfa.compose.screen.am

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Retour tactile des clics dans les réglages, choisi par l'utilisateur :
 * Son / Vibreur / Silence (persisté, aucun effet caché).
 */
object A2MClickFeedback {
    const val SOUND = "sound"
    const val VIBRATE = "vibrate"
    const val SILENT = "silent"

    fun apply(context: Context, mode: String) {
        when (mode) {
            SOUND -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audio?.playSoundEffect(AudioManager.FX_KEY_CLICK)
            }
            VIBRATE -> {
                val vibrator =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                            ?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(18)
                    }
                }
            }
        }
    }
}

/** Libellé stable du mode de retour tactile (testable en JVM pur). */
internal fun clickFeedbackLabel(mode: String): String =
    when (mode) {
        A2MClickFeedback.SOUND -> "Son"
        A2MClickFeedback.VIBRATE -> "Vibreur"
        A2MClickFeedback.SILENT -> "Silence"
        else -> mode
    }
