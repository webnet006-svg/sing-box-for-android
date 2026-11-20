package io.nekohasekai.sfa.bg

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.compose.navigation.A2MRoutes

/**
 * Services « assistant » : A2M TUN peut être choisi comme assistant Android
 * (Paramètres > Applications par défaut > Application d'assistance).
 *
 * Effet réel : le geste d'assistant ouvre directement l'IP Finder. Le rôle
 * Assistant ne donne pas WRITE_SECURE_SETTINGS ; la bascule automatique du
 * mode avion reste donc soumise à l'autorisation ADB.
 *
 * Aucun audio n'est capturé, aucun écran n'est lu, aucune dictée n'est traitée :
 * le service de reconnaissance est un no-op exigé par la déclaration Android.
 */
class A2MVoiceInteractionService : VoiceInteractionService()

class A2MVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = A2MVoiceInteractionSession(this)
}

class A2MVoiceInteractionSession(private val appContext: Context) : VoiceInteractionSession(appContext) {
    override fun onShow(args: Bundle?) {
        super.onShow(args)
        runCatching {
            appContext.startActivity(
                Intent(appContext, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                    putExtra("a2m_route", A2MRoutes.IP_FINDER)
                },
            )
        }
        hide()
    }
}

/**
 * Reconnaissance vocale factice : Android exige un service de reconnaissance
 * dans les métadonnées d'un assistant. Aucune méthode n'écoute ni ne renvoie
 * de résultat.
 */
class A2MRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: RecognitionService.Callback?) = Unit

    override fun onStopListening(listener: RecognitionService.Callback?) = Unit

    override fun onCancel() = Unit
}
