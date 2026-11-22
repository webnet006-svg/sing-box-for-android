package io.nekohasekai.sfa.compose.screen.am

import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.SoundPool
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.Settings

/**
 * Retour tactile des clics dans les réglages, choisi par l'utilisateur :
 * Son / Vibreur / Silence (persisté, aucun effet caché).
 */
object A2MClickFeedback {
    const val SOUND = "sound"
    const val VIBRATE = "vibrate"
    const val SILENT = "silent"

    data class ClickSound(val id: String, val label: String, val resourceId: Int)

    val sounds = listOf(
        ClickSound("1", "Bip doux montant", R.raw.a2m_click_01),
        ClickSound("2", "Bip clair", R.raw.a2m_click_02),
        ClickSound("3", "Double clic", R.raw.a2m_click_03),
        ClickSound("4", "Validation ascendante", R.raw.a2m_click_04),
        ClickSound("5", "Alerte descendante", R.raw.a2m_click_05),
        ClickSound("6", "Rappel en trois notes", R.raw.a2m_click_06),
        ClickSound("7", "Bip grave", R.raw.a2m_click_07),
        ClickSound("8", "Accord cristallin", R.raw.a2m_click_08),
        ClickSound("9", "Balayage montant", R.raw.a2m_click_09),
        ClickSound("10", "Balayage descendant", R.raw.a2m_click_10),
    )

    private var soundPool: SoundPool? = null
    private val sampleIds = mutableMapOf<String, Int>()
    private val loadedSamples = mutableSetOf<Int>()
    private var pendingSampleId: Int? = null
    private var activeStreamId = 0

    fun applyConfigured(context: Context) {
        if (Settings.a2mSoundsEnabled && !Settings.a2mSilenceMode) {
            playSound(context, Settings.a2mClickSoundId)
        }
        if (Settings.a2mVibrationsEnabled) vibrate(context)
    }

    fun previewSound(context: Context, id: String) = playSound(context, id)

    private fun playSound(context: Context, id: String) {
        runCatching {
            synchronized(this) {
                val appContext = context.applicationContext
                val pool = soundPool ?: createSoundPool(appContext).also { soundPool = it }
                val soundId = sounds.firstOrNull { it.id == id }?.id ?: sounds.first().id
                val sampleId = sampleIds[soundId] ?: return
                if (sampleId !in loadedSamples) {
                    pendingSampleId = sampleId
                    return
                }
                pendingSampleId = null
                if (activeStreamId != 0) pool.stop(activeStreamId)
                activeStreamId = pool.play(sampleId, 0.72f, 0.72f, 1, 0, 1f)
            }
        }
    }

    private fun createSoundPool(context: Context): SoundPool =
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
            .also { pool ->
                pool.setOnLoadCompleteListener { completedPool, sampleId, status ->
                    synchronized(this) {
                        if (status == 0) {
                            loadedSamples += sampleId
                            if (pendingSampleId == sampleId) {
                                pendingSampleId = null
                                if (activeStreamId != 0) completedPool.stop(activeStreamId)
                                activeStreamId = completedPool.play(sampleId, 0.72f, 0.72f, 1, 0, 1f)
                            }
                        }
                    }
                }
                sounds.forEach { sound -> sampleIds[sound.id] = pool.load(context, sound.resourceId, 1) }
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
