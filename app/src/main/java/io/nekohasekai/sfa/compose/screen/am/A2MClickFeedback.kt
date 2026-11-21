package io.nekohasekai.sfa.compose.screen.am

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.nekohasekai.sfa.database.Settings

/**
 * Retour tactile des clics dans les réglages, choisi par l'utilisateur :
 * Son / Vibreur / Silence (persisté, aucun effet caché).
 */
object A2MClickFeedback {
    const val SOUND = "sound"
    const val VIBRATE = "vibrate"
    const val SILENT = "silent"

    data class ClickSound(val id: String, val label: String, val tone: Int)

    val sounds = listOf(
        ClickSound("1", "Bip doux", ToneGenerator.TONE_PROP_BEEP),
        ClickSound("2", "Bip clair", ToneGenerator.TONE_PROP_BEEP2),
        ClickSound("3", "Note 2", ToneGenerator.TONE_DTMF_2),
        ClickSound("4", "Validation", ToneGenerator.TONE_PROP_ACK),
        ClickSound("5", "Alerte", ToneGenerator.TONE_PROP_NACK),
        ClickSound("6", "Rappel", ToneGenerator.TONE_PROP_PROMPT),
        ClickSound("7", "Note 1", ToneGenerator.TONE_DTMF_1),
        ClickSound("8", "Note 3", ToneGenerator.TONE_DTMF_3),
        ClickSound("9", "Note 6", ToneGenerator.TONE_DTMF_6),
        ClickSound("10", "Note 9", ToneGenerator.TONE_DTMF_9),
    )

    private var activeTone: ToneGenerator? = null

    fun applyConfigured(context: Context) {
        if (Settings.a2mSoundsEnabled && !Settings.a2mSilenceMode) {
            playSound(context, Settings.a2mClickSoundId)
        }
        if (Settings.a2mVibrationsEnabled) vibrate(context)
    }

    fun previewSound(context: Context, id: String) = playSound(context, id)

    private fun playSound(context: Context, id: String) {
        val sound = sounds.firstOrNull { it.id == id } ?: sounds.first()
        runCatching {
            synchronized(this) {
                activeTone?.release()
                activeTone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).also { generator ->
                    generator.startTone(sound.tone, 120)
                    Handler(Looper.getMainLooper()).postDelayed({
                        synchronized(this) {
                            if (activeTone === generator) {
                                generator.release()
                                activeTone = null
                            }
                        }
                    }, 180)
                }
            }
        }
    }

    private fun vibrate(context: Context) {
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
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

    fun apply(context: Context, mode: String) {
        when (mode) {
            SOUND -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audio?.playSoundEffect(AudioManager.FX_KEY_CLICK)
            }
            VIBRATE -> {
                vibrate(context)
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
