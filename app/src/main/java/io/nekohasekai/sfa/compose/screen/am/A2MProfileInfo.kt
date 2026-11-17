package io.nekohasekai.sfa.compose.screen.am

import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Lecture d'informations réelles sur une configuration enregistrée.
 * Aucune valeur n'est inventée : si le fichier est illisible, un libellé neutre est retourné.
 */
internal object A2MProfileInfo {
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
        runCatching {
            val text = File(profile.typed.path).readText()
            val root = json.parseToJsonElement(text).jsonObject
            val outbounds = root["outbounds"]?.jsonArray ?: return@runCatching fallback(profile)
            val proxy =
                outbounds
                    .mapNotNull { it as? JsonObject }
                    .firstOrNull { outbound ->
                        val type = outbound["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        type.isNotBlank() && type !in NON_PROXY_TYPES
                    }
            val type = proxy?.get("type")?.jsonPrimitive?.contentOrNull
            if (type.isNullOrBlank()) fallback(profile) else PROTOCOL_LABELS[type] ?: type.uppercase()
        }.getOrDefault(fallback(profile))

    fun typeLabel(profile: Profile): String =
        when (profile.typed.type) {
            TypedProfile.Type.Remote -> "Abonnement"
            TypedProfile.Type.Local -> "Configuration"
        }

    /** Code court du protocole (VM, VL, TR…) pour les badges, ou "" si inconnu. */
    fun protocolCode(profile: Profile): String =
        runCatching {
            val text = File(profile.typed.path).readText()
            val root = json.parseToJsonElement(text).jsonObject
            val outbounds = root["outbounds"]?.jsonArray ?: return@runCatching ""
            val type =
                outbounds
                    .mapNotNull { it as? JsonObject }
                    .firstOrNull { outbound ->
                        val outboundType = outbound["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        outboundType.isNotBlank() && outboundType !in NON_PROXY_TYPES
                    }
                    ?.get("type")?.jsonPrimitive?.contentOrNull
            when (type) {
                "vmess" -> "VM"
                "vless" -> "VL"
                "trojan" -> "TR"
                "shadowsocks" -> "SS"
                "hysteria" -> "HY"
                "hysteria2" -> "H2"
                "tuic" -> "TU"
                "wireguard" -> "WG"
                "shadowtls" -> "ST"
                "anytls" -> "AT"
                "ssh" -> "SH"
                null -> ""
                else -> type.take(2).uppercase()
            }
        }.getOrDefault("")

    /** Adresse du premier serveur sortant réel déclaré dans la configuration, ou null. */
    fun serverAddress(profile: Profile): String? =
        runCatching {
            val text = File(profile.typed.path).readText()
            val root = json.parseToJsonElement(text).jsonObject
            val outbounds = root["outbounds"]?.jsonArray ?: return@runCatching null
            val server =
                outbounds
                    .mapNotNull { it as? JsonObject }
                    .firstOrNull { outbound ->
                        val type = outbound["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        type.isNotBlank() && type !in NON_PROXY_TYPES
                    }
                    ?.get("server")?.jsonPrimitive?.contentOrNull
            server?.takeIf { it.isNotBlank() }
        }.getOrNull()

    /** Serveurs DNS réellement déclarés dans la configuration (lecture seule). */
    fun dnsServers(profile: Profile): List<String> =
        runCatching {
            val root = json.parseToJsonElement(File(profile.typed.path).readText()).jsonObject
            val servers = root["dns"]?.jsonObject?.get("servers")?.jsonArray ?: return@runCatching emptyList()
            servers.mapNotNull { element ->
                (element as? JsonPrimitive)?.contentOrNull
                    ?: (element as? JsonObject)?.get("server")?.jsonPrimitive?.contentOrNull
            }
        }.getOrDefault(emptyList())

    private fun fallback(profile: Profile): String = typeLabel(profile)
}
