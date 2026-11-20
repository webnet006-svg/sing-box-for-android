package io.nekohasekai.sfa.compose.screen.am

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lectures réseau réelles pour l'IP Finder et l'accueil : l'IP du réseau
 * mobile est lue sur les interfaces de l'appareil (aucune requête internet),
 * et le mode avion n'est piloté que si la permission WRITE_SECURE_SETTINGS a
 * été accordée (via ADB). Aucune donnée inventée : en cas d'indisponibilité,
 * les fonctions retournent null / false et l'interface l'affiche.
 */
object A2MNetworkInfo {

    private val cellularNames = listOf("rmnet", "ccmni", "ccemni", "wwan")
    private val excludedNames = listOf("wlan", "swlan", "tun", "tap", "lo", "dummy", "usb")

    /**
     * IP IPv4 réelle du réseau mobile : énumère les interfaces réseau de
     * l'appareil. Priorité aux interfaces cellulaires nommées (rmnet, ccmni,
     * ccemni, wwan…), sinon première interface non exclue (wlan/tun/usb
     * ignorés). Retourne null quand aucune interface n'a d'adresse.
     */
    fun readCellularIp(): String? =
        runCatching {
            val interfaces =
                java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                    .filter { it.isUp && !it.isLoopback }
                    .toList()
            val firstV4: (java.net.NetworkInterface) -> String? = { iface ->
                iface.inetAddresses.asSequence()
                    .filter { it is java.net.Inet4Address && !it.isLoopbackAddress && !it.isAnyLocalAddress }
                    .firstOrNull()?.hostAddress
            }
            interfaces
                .filter { iface -> cellularNames.any { iface.name.lowercase().contains(it) } }
                .firstNotNullOfOrNull(firstV4)
                ?: interfaces
                    .filter { iface -> excludedNames.none { iface.name.lowercase().contains(it) } }
                    .firstNotNullOfOrNull(firstV4)
        }.getOrNull()

    /**
     * Vrai si le Wi-Fi est actif (une interface wlan a une adresse IPv4) —
     * lecture réelle, comme pour l'IP cellulaire.
     */
    fun isWifiActive(): Boolean =
        runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback && it.name.lowercase().contains("wlan") }
                .any { iface ->
                    iface.inetAddresses.asSequence().any { it is java.net.Inet4Address }
                }
        }.getOrDefault(false)

    /**
     * Vrai si l'app peut basculer le mode avion elle-même : permission
     * WRITE_SECURE_SETTINGS accordée (typiquement via ADB).
     */
    fun canToggleAirplaneMode(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Bascule le mode avion si la permission le permet. Retourne false quand
     * la bascule n'a pas pu être appliquée (l'interface repasse alors en
     * mode manuel : l'utilisateur bascule, l'app détecte le changement d'IP).
     */
    suspend fun setAirplaneMode(context: Context, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                android.provider.Settings.Global.putInt(
                    context.contentResolver,
                    android.provider.Settings.Global.AIRPLANE_MODE_ON,
                    if (enabled) 1 else 0,
                )
                context.sendBroadcast(
                    Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", enabled),
                )
                true
            }.getOrDefault(false)
        }
}

/**
 * Règles de ciblage d'IP, façon IP Finder : « ^préfixe » (commence par),
 * « $suffixe » (finit par), « =exacte », et une valeur sans symbole signifie
 * « contient n'importe où dans l'adresse ». Séparateurs acceptés : « ; »
 * (simple ou double), « , » et retour à la ligne. Une règle vide est ignorée.
 */
internal fun parseIpRules(raw: String): List<Pair<Char, String>> =
    raw.split(";;", ";", ",", "\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { rule ->
            when (rule.firstOrNull()) {
                '^', '$', '=' -> rule.first() to rule.drop(1).trim()
                else -> 'c' to rule
            }
        }
        .filter { (_, value) -> value.isNotEmpty() }

/**
 * Liste d'adresses IP valides intégrée par défaut (préfixes à deux octets,
 * format opérateur « 10.xx »). Elle reste utilisée tant que l'utilisateur
 * n'a pas saisi sa propre liste.
 */
internal val DEFAULT_IP_RULES: String =
    "10.41;10.43;10.46;10.63;10.67;10.74;10.75;10.77;10.102;10.104;10.143;" +
        "10.161;10.165;10.193;10.194;10.195;10.196;10.197;10.198;10.199;10.204;" +
        "10.205;10.206;10.207;10.208;10.209;10.210;10.212;10.213;10.214;10.215;" +
        "10.216;10.217;10.218;10.219;10.220;10.221;10.222;10.223;10.224;10.225;" +
        "10.226;10.227;10.228;10.229;10.230;10.231;10.232;10.233;10.234;10.235;" +
        "10.236;10.237;10.238;10.244;10.245;10.246"

/**
 * Règles effectives : liste personnalisée si l'utilisateur en a saisi une,
 * sinon liste par défaut embarquée.
 */
internal fun effectiveIpRules(raw: String): List<Pair<Char, String>> {
    val custom = parseIpRules(raw)
    return if (custom.isNotEmpty()) custom else parseIpRules(DEFAULT_IP_RULES)
}

/** Vrai si l'IP respecte au moins une règle (aucune règle ⇒ faux). */
internal fun ipMatchesRules(ip: String, rules: List<Pair<Char, String>>): Boolean =
    rules.any { (op, value) ->
        when (op) {
            '^' -> ip.startsWith(value)
            '$' -> ip.endsWith(value)
            '=' -> ip == value
            else -> ip.contains(value)
        }
    }

/**
 * Contrôle de saisie des règles d'IP. Retourne la liste des problèmes lisibles ;
 * une liste vide signifie que la recherche peut démarrer. Les règles vides
 * utilisent la liste par défaut intégrée, qui est toujours valide.
 */
internal fun validateIpRules(raw: String): List<String> {
    val rules = effectiveIpRules(raw)
    if (rules.isEmpty()) return listOf("Aucune règle d'IP exploitable.")
    val problems = mutableListOf<String>()
    rules.forEach { (op, value) ->
        val label =
            when (op) {
                '^' -> "préfixe « $value »"
                '$' -> "suffixe « $value »"
                '=' -> "adresse exacte « $value »"
                else -> "contenu « $value »"
            }
        if (value.contains(':')) {
            problems += "Règle $label : IPv6 non pris en charge par l'IP Finder."
        } else if (!value.matches(Regex("""\d{1,3}(\.\d{1,3}){0,3}"""))) {
            problems += "Règle $label : format attendu 10.21 ou 10.21.3.4."
        } else if (value.split('.').any { octet -> octet.toIntOrNull()?.let { it !in 0..255 } ?: true }) {
            problems += "Règle $label : chaque bloc doit être compris entre 0 et 255."
        }
    }
    return problems.distinct()
}

