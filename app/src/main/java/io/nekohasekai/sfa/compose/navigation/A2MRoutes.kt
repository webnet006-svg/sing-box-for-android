package io.nekohasekai.sfa.compose.navigation

/**
 * Routes de l'interface A2M TUN.
 *
 * Ces routes remplacent l'accès utilisateur aux écrans Sing-box. Le moteur
 * (BoxService / VPNService) reste inchangé et alimente ces écrans.
 */
object A2MRoutes {
    const val HOME = "a2m/home"
    const val SERVERS = "a2m/servers"
    const val ROUTING = "a2m/routing"
    const val SETTINGS = "a2m/settings"
    const val JOURNAL = "a2m/journal"

    /** Sous-écrans de Réglages (façon SniTunnel : Connexion, DNS). */
    const val SETTINGS_CONNECTION = "a2m/settings/connection"
    const val SETTINGS_ENGINE = "a2m/settings/engine"
    const val SETTINGS_HOTSPOT = "a2m/settings/hotspot"
    const val SETTINGS_AUTOMATION = "a2m/settings/automation"
    const val SETTINGS_DIAGNOSTICS = "a2m/settings/diagnostics"
    const val SETTINGS_ANTI_CENSORSHIP = "a2m/settings/anti-censorship"
    const val SETTINGS_ABOUT = "a2m/settings/about"
    const val IP_FINDER = "a2m/tools/ipfinder"
    const val CONSUMPTION = "a2m/settings/consumption"
    const val TRANSFERS = "a2m/settings/transfers"

    /** Éditeur de serveur « mode texte » : {profileId} = 0 pour une création. */
    const val SERVER_EDITOR_BASE = "a2m/servers/editor"
    const val SERVER_EDITOR = "$SERVER_EDITOR_BASE/{profileId}"

    fun serverEditor(profileId: Long): String = "$SERVER_EDITOR_BASE/$profileId"
    const val APPEARANCE = "a2m/appearance"

    /** Préfixe commun, utilisé pour la barre de navigation A2M. */
    const val PREFIX = "a2m/"
}
