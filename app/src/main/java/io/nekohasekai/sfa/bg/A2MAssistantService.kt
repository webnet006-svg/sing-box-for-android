package io.nekohasekai.sfa.bg

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Services « assistant » : A2M TUN peut être choisi comme assistant Android
 * (Paramètres > Applications par défaut > Application d'assistance).
 * Ce rôle est demandé uniquement pour permettre le pilotage du mode avion
 * dans l'IP Finder — l'application ne lit jamais l'écran, ne capture aucun
 * audio et ne traite aucune dictée. Toutes les méthodes sont vides.
 */
class A2MVoiceInteractionService : VoiceInteractionService()

class A2MVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = A2MVoiceInteractionSession(this)
}

class A2MVoiceInteractionSession(context: Context) : VoiceInteractionSession(context)
