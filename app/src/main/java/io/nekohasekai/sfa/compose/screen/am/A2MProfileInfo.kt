package io.nekohasekai.sfa.compose.screen.am

import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

    private fun fallback(profile: Profile): String = typeLabel(profile)
}
