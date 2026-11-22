package io.nekohasekai.sfa.compose.screen.am

import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.bg.VPNService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Lecture d'informations réelles sur une configuration enregistrée.
 * Aucune valeur n'est inventée : si le fichier est illisible, un libellé neutre est retourné.
 */
internal object A2MProfileInfo {
    data class Summary(
        val protocol: String,
        val code: String,
        val address: String?,
        val port: Int?,
    )
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val NON_PROXY_TYPES = setOf("direct", "block", "dns", "selector", "urltest")

    private val PROTOCOL_LABELS =
        mapOf(
            "vless" to "VLESS",
            "vmess" to "VMess",
            "trojan" to "Trojan",
            "shadowsocks" to "Shadowsocks",
            "hysteria" to "Hysteria",
            "hysteria2" to "Hysteria2",
            "tuic" to "TUIC",
            "wireguard" to "WireGuard",
            "ssh" to "SSH",
            "http" to "HTTP",
            "socks" to "SOCKS",
            "anytls" to "AnyTLS",
            "tor" to "Tor",
            "shadowtls" to "ShadowTLS",
        )

    fun protocolLabel(profile: Profile): String =
        summary(profile).protocol

    fun typeLabel(profile: Profile): String =
        when (profile.typed.type) {
            TypedProfile.Type.Remote -> "Abonnement"
            TypedProfile.Type.Local -> "Configuration"
        }

    /** Code court du protocole (VM, VL, TR…) pour les badges, ou "" si inconnu. */
    fun protocolCode(profile: Profile): String =
        summary(profile).code

    /** Adresse du premier serveur sortant réel déclaré dans la configuration, ou null. */
    fun serverAddress(profile: Profile): String? =
        summary(profile).address

    /** Lit le JSON une seule fois pour alimenter les cartes de serveur. */
    fun summary(profile: Profile): Summary =
        runCatching {
            val root = json.parseToJsonElement(File(profile.typed.path).readText()).jsonObject
            val proxy = root["outbounds"]?.jsonArray
                ?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { outbound ->
                    val type = outbound["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    type.isNotBlank() && type !in NON_PROXY_TYPES
                }
            val type = proxy?.get("type")?.jsonPrimitive?.contentOrNull
            val label = if (type.isNullOrBlank()) fallback(profile) else PROTOCOL_LABELS[type] ?: type.uppercase()
            val code = when (type) {
                "vmess" -> "VM"; "vless" -> "VL"; "trojan" -> "TR"; "shadowsocks" -> "SS"
                "hysteria" -> "HY"; "hysteria2" -> "H2"; "tuic" -> "TU"; "wireguard" -> "WG"
                "shadowtls" -> "ST"; "anytls" -> "AT"; "ssh" -> "SH"
                null -> ""
                else -> type.take(2).uppercase()
            }
            Summary(
                protocol = label,
                code = code,
                address = proxy?.get("server")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
                port = proxy?.get("server_port")?.jsonPrimitive?.intOrNull ?: proxy?.get("port")?.jsonPrimitive?.intOrNull,
            )
        }.getOrElse { Summary(fallback(profile), "", null, null) }

    /**
     * Adresse + port du premier serveur sortant réel, pour le test TCP réel
     * (« Tester » de l'écran Serveurs), ou null si absent de la configuration.
     */
    fun serverAddressAndPort(profile: Profile): Pair<String, Int>? =
        runCatching {
            val text = File(profile.typed.path).readText()
            val root = json.parseToJsonElement(text).jsonObject
            val outbounds = root["outbounds"]?.jsonArray ?: return@runCatching null
            val proxy =
                outbounds
                    .mapNotNull { it as? JsonObject }
                    .firstOrNull { outbound ->
                        val type = outbound["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        type.isNotBlank() && type !in NON_PROXY_TYPES
                    }
            val server = proxy?.get("server")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            val port = proxy["server_port"]?.jsonPrimitive?.intOrNull ?: proxy["port"]?.jsonPrimitive?.intOrNull ?: 443
            server to port
        }.getOrNull()

    /** A plain TCP connect cannot test protocols whose only transport is UDP/QUIC. */
    fun tcpProbeUnsupportedReason(profile: Profile): String? =
        runCatching {
            val root = json.parseToJsonElement(File(profile.typed.path).readText()).jsonObject
            val proxy = root["outbounds"]?.jsonArray
                ?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { outbound ->
                    val type = outbound["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    type.isNotBlank() && type !in NON_PROXY_TYPES
                } ?: return@runCatching null
            val type = proxy["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val transport = proxy["transport"] as? JsonObject
            val transportType = transport?.get("type")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (type in setOf("hysteria", "hysteria2", "tuic", "wireguard") || transportType in setOf("quic", "udp")) {
                "UDP/QUIC : le test TCP n'est pas applicable"
            } else {
                null
            }
        }.getOrNull()

    /**
     * Test TCP réel : prend deux échantillons indépendants vers host:port et
     * conserve la médiane des réponses réussies. Une seule mesure peut être
     * artificiellement basse ou haute à cause du réseau mobile ; la médiane
     * rend le classement plus stable sans masquer un échec complet.
     */
    suspend fun tcpPing(
        host: String,
        port: Int,
        timeoutMs: Int = Settings.connectionTimeoutSec.coerceIn(1, 120) * 1000,
    ): Int? =
        withContext(Dispatchers.IO) {
            val samples = buildList {
                repeat(2) {
                    val sample = runCatching {
                        val start = System.nanoTime()
                        java.net.Socket().use { socket ->
                            check(VPNService.protectProbeSocket(socket)) {
                                "Impossible d'isoler le test du tunnel VPN actif"
                            }
                            socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
                        }
                        ((System.nanoTime() - start) / 1_000_000L).toInt()
                    }.getOrNull()
                    if (sample != null) add(sample)
                }
            }
            samples.sorted().getOrNull(samples.size / 2)?.toInt()
        }

    /** Serveurs DNS réellement déclarés dans la configuration (lecture seule). */
    fun dnsServers(profile: Profile): List<String> =
        runCatching {
            val root = json.parseToJsonElement(File(profile.typed.path).readText()).jsonObject
            val servers = root["dns"]?.jsonObject?.get("servers")?.jsonArray ?: return@runCatching emptyList()
            servers.mapNotNull { element ->
                val obj = element as? JsonObject
                (obj?.get("server") as? JsonPrimitive)?.contentOrNull
                    ?: (obj?.get("address") as? JsonPrimitive)?.contentOrNull
                    ?: (element as? JsonPrimitive)?.contentOrNull
            }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())

    private fun fallback(profile: Profile): String = typeLabel(profile)
}
