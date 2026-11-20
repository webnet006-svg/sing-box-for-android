package io.nekohasekai.sfa.compose.screen.am

/**
 * Retourne l'identifiant du serveur dont la latence TCP est la plus faible.
 * La map ne contient que des tests réussis : les échecs et les adresses
 * inconnues sont retirés par l'écran avant l'appel.
 */
fun fastestProfileId(latencies: Map<Long, Long>): Long? =
    latencies.minByOrNull { it.value }?.key
