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

    /** Préfixe commun, utilisé pour la barre de navigation A2M. */
    const val PREFIX = "a2m/"
}
