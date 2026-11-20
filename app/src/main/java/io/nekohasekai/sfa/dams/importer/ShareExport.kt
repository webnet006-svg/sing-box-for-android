package io.nekohasekai.sfa.dams.importer

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Export d'un serveur vers un format partageable.
 *
 * Trois formes :
 *  - lien de partage standard `vless://`, `vmess://`, `trojan://` ;
 *  - `a2mtun://server/<lien>` : forme « simple », lisible, ouverture directe
 *    dans A2M TUN (le lien de partage est encodé en un seul segment) ;
 *  - `a2mtun://config/<base64url(gzip(json))>` : forme « codée », autonome et
 *    compacte, qui embarque la configuration sing-box complète.
 *
 * La forme « codée » est compressée puis encodée, jamais chiffrée.
 * Les secrets sont nécessaires au fonctionnement des liens : l'affichage
 * destiné à l'écran les masque ([maskSensitive]), jamais les liens exportés.
 */
object ShareExport {
    private const val CODED_PREFIX = "a2mtun://config/"
    private const val SIMPLE_PREFIX = "a2mtun://server/"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    data class Exported(
        val protocolLabel: String,
        val shareUri: String,
        val simpleLink: String,
        val codedLink: String,
    )

    fun export(
        config: String,
        name: String,
    ): Exported? {
        val draft = ServerDrafts.fromConfig(config)
        if (draft != null) {
            val shareUri = shareUri(draft, name)
            return Exported(
                protocolLabel = draft.protocol.label,
                shareUri = shareUri,
                simpleLink = simpleLink(shareUri),
                codedLink = codedLink(config),
            )
        }

        // Protocoles absents de l'éditeur (Shadowsocks, Hysteria2, TUIC) :
        // on relit directement l'outbound de la configuration, sans inventer
        // de format de lien. Shadowsocks utilise le format SIP002 standard.
        val outbound = firstOutbound(config) ?: return null
        val type = outbound.string("type").lowercase()
        val shareUri =
            when (type) {
                "shadowsocks" -> shadowsocksUri(outbound, name)
                "hysteria2" -> hysteria2Uri(outbound, name)
                "tuic" -> tuicUri(outbound, name)
                else -> null
            } ?: return null
        val label =
            when (type) {
                "shadowsocks" -> "SS"
                "hysteria2" -> "Hysteria2"
                "tuic" -> "TUIC"
                else -> type
            }
        return Exported(
            protocolLabel = label,
            shareUri = shareUri,
            simpleLink = simpleLink(shareUri),
            codedLink = codedLink(config),
        )
    }

    /** Lien de partage standard du brouillon (VMess est encodé en base64 JSON). */
    fun shareUri(
        draft: ServerDraft,
        name: String,
    ): String =
        when (draft.protocol) {
            Importer.Protocol.VMESS -> vmessUri(draft, name)
            Importer.Protocol.TROJAN -> trojanUri(draft, name)
            else -> vlessUri(draft, name)
        }

    fun simpleLink(shareUri: String): String = SIMPLE_PREFIX + encode(shareUri)

    fun codedLink(config: String): String = CODED_PREFIX + Base64.UrlSafe.encode(gzip(config))

    /** Décode un lien `a2mtun://config/…` (null si altéré ou non compressé). */
    fun decodeCoded(link: String): String? {
        val payload = link.substringAfter(CODED_PREFIX, "")
        if (payload.isBlank()) return null
        return runCatching {
            val compressed = Base64.UrlSafe.decode(payload)
            GZIPInputStream(compressed.inputStream()).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /** Texte d'affichage : identifiants et charge codée masqués. */
    fun maskSensitive(link: String): String =
        when {
            link.startsWith(CODED_PREFIX) -> "$CODED_PREFIX••••••"
            link.startsWith(SIMPLE_PREFIX) -> "$SIMPLE_PREFIX••••••"
            else -> link.replace(Regex("""://[^@/#]+@"""), "://•••@")
        }

    private fun vlessUri(
        draft: ServerDraft,
        name: String,
    ): String {
        val params = mutableListOf<Pair<String, String>>()
        if (draft.transport != "tcp") params += "type" to draft.transport
        if (draft.security != "none") params += "security" to draft.security
        if (draft.sni.isNotBlank()) params += "sni" to draft.sni
        if (draft.fingerprint.isNotBlank()) params += "fp" to draft.fingerprint
        if (draft.realityPublicKey.isNotBlank()) params += "pbk" to draft.realityPublicKey
        if (draft.realityShortId.isNotBlank()) params += "sid" to draft.realityShortId
        if (draft.flow.isNotBlank()) params += "flow" to draft.flow
        if (draft.path.isNotBlank()) params += "path" to draft.path
        if (draft.hostHeader.isNotBlank()) params += "host" to draft.hostHeader
        if (draft.serviceName.isNotBlank()) params += "serviceName" to draft.serviceName
        if (draft.alpn.isNotBlank()) params += "alpn" to draft.alpn
        if (draft.allowInsecure) params += "allowInsecure" to "1"
        val query = query(params)
        return buildString {
            append("vless://")
            append(encode(draft.identity))
            append("@")
            append(draft.host)
            append(":")
            append(draft.port)
            if (query.isNotEmpty()) {
                append("?")
                append(query)
            }
            append("#")
            append(encode(name))
        }
    }

    private fun trojanUri(
        draft: ServerDraft,
        name: String,
    ): String {
        val params = mutableListOf<Pair<String, String>>()
        if (draft.transport != "tcp") params += "type" to draft.transport
        if (draft.security != "none") params += "security" to draft.security
        if (draft.sni.isNotBlank()) params += "sni" to draft.sni
        if (draft.fingerprint.isNotBlank()) params += "fp" to draft.fingerprint
        if (draft.path.isNotBlank()) params += "path" to draft.path
        if (draft.hostHeader.isNotBlank()) params += "host" to draft.hostHeader
        if (draft.serviceName.isNotBlank()) params += "serviceName" to draft.serviceName
        if (draft.alpn.isNotBlank()) params += "alpn" to draft.alpn
        if (draft.allowInsecure) params += "allowInsecure" to "1"
        val query = query(params)
        return buildString {
            append("trojan://")
            append(encode(draft.identity))
            append("@")
            append(draft.host)
            append(":")
            append(draft.port)
            if (query.isNotEmpty()) {
                append("?")
                append(query)
            }
            append("#")
            append(encode(name))
        }
    }

    private fun vmessUri(
        draft: ServerDraft,
        name: String,
    ): String {
        val payload =
            buildJsonObject {
                put("v", "2")
                put("ps", name)
                put("add", draft.host)
                put("port", draft.port.toString())
                put("id", draft.identity)
                put("aid", draft.alterId.toString())
                put("scy", draft.vmessSecurity.ifBlank { "auto" })
                put("net", draft.transport)
                put("type", "none")
                if (draft.hostHeader.isNotBlank()) put("host", draft.hostHeader)
                if (draft.path.isNotBlank()) put("path", draft.path)
                put("tls", if (draft.security == "none") "" else "tls")
                if (draft.sni.isNotBlank()) put("sni", draft.sni)
                if (draft.alpn.isNotBlank()) put("alpn", draft.alpn)
                if (draft.fingerprint.isNotBlank()) put("fp", draft.fingerprint)
            }.toString()
        return "vmess://" + Base64.Default.encode(payload.toByteArray(Charsets.UTF_8))
    }

    /**
     * Lien Shadowsocks SIP002 : `ss://base64url(method:password)@host:port`.
     * Le plugin éventuel est transmis dans la query, comme dans le format
     * standard, et non dans un champ propriétaire.
     */
    private fun shadowsocksUri(
        outbound: JsonObject,
        name: String,
    ): String? {
        val host = outbound.string("server")
        val port = outbound.int("server_port")
        val method = outbound.string("method")
        val password = outbound.string("password")
        if (host.isBlank() || port !in 1..65535 || method.isBlank() || password.isBlank()) {
            return null
        }
        val credentials =
            Base64.UrlSafe.encode("$method:$password".toByteArray(Charsets.UTF_8))
        val params = mutableListOf<Pair<String, String>>()
        val plugin = outbound.string("plugin")
        if (plugin.isNotBlank()) {
            val options = outbound.string("plugin_opts")
            params += "plugin" to (plugin + if (options.isNotBlank()) ";$options" else "")
        }
        val query = query(params)
        return buildString {
            append("ss://")
            append(credentials)
            append("@")
            append(authority(host, port))
            if (query.isNotEmpty()) {
                append("?")
                append(query)
            }
            append("#")
            append(encode(name))
        }
    }

    private fun hysteria2Uri(
        outbound: JsonObject,
        name: String,
    ): String? {
        val host = outbound.string("server")
        val port = outbound.int("server_port")
        val password = outbound.string("password")
        if (host.isBlank() || port !in 1..65535 || password.isBlank()) return null
        val params = tlsParams(outbound["tls"] as? JsonObject).toMutableList()
        val obfs = outbound["obfs"] as? JsonObject
        val obfsType = obfs?.string("type").orEmpty()
        if (obfsType.isNotBlank()) {
            params += "obfs" to obfsType
            val obfsPassword = obfs?.string("password").orEmpty()
            if (obfsPassword.isNotBlank()) params += "obfs-password" to obfsPassword
        }
        val query = query(params)
        return buildString {
            append("hysteria2://")
            append(encode(password))
            append("@")
            append(authority(host, port))
            if (query.isNotEmpty()) {
                append("?")
                append(query)
            }
            append("#")
            append(encode(name))
        }
    }

    private fun tuicUri(
        outbound: JsonObject,
        name: String,
    ): String? {
        val host = outbound.string("server")
        val port = outbound.int("server_port")
        val uuid = outbound.string("uuid")
        val password = outbound.string("password")
        if (host.isBlank() || port !in 1..65535 || uuid.isBlank() || password.isBlank()) {
            return null
        }
        val params = mutableListOf<Pair<String, String>>()
        val congestion = outbound.string("congestion_control")
        if (congestion.isNotBlank()) params += "congestion_control" to congestion
        val relayMode = outbound.string("udp_relay_mode")
        if (relayMode.isNotBlank()) params += "udp_relay_mode" to relayMode
        val tls = outbound["tls"] as? JsonObject
        val sni = tls?.string("server_name").orEmpty()
        if (sni.isNotBlank()) params += "sni" to sni
        if (tls?.bool("insecure") == true) params += "allow_insecure" to "1"
        val alpn = tls?.stringList("alpn").orEmpty()
        if (alpn.isNotEmpty()) params += "alpn" to alpn.joinToString(",")
        val query = query(params)
        return buildString {
            append("tuic://")
            append(encode("$uuid:$password"))
            append("@")
            append(authority(host, port))
            if (query.isNotEmpty()) {
                append("?")
                append(query)
            }
            append("#")
            append(encode(name))
        }
    }

    private fun tlsParams(tls: JsonObject?): List<Pair<String, String>> {
        val params = mutableListOf<Pair<String, String>>()
        val sni = tls?.string("server_name").orEmpty()
        if (sni.isNotBlank()) params += "sni" to sni
        if (tls?.bool("insecure") == true) params += "insecure" to "1"
        val alpn = tls?.stringList("alpn").orEmpty()
        if (alpn.isNotEmpty()) params += "alpn" to alpn.joinToString(",")
        return params
    }

    private fun firstOutbound(config: String): JsonObject? {
        val root = runCatching { json.parseToJsonElement(config).jsonObject }.getOrNull() ?: return null
        return (root["outbounds"] as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.firstOrNull { outbound ->
                val type = outbound.string("type").lowercase()
                type.isNotBlank() && type != "direct" && type != "block" && type != "dns" &&
                    outbound.string("server").isNotBlank()
            }
    }

    private fun authority(host: String, port: Int): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]:$port" else "$host:$port"

    private fun query(params: List<Pair<String, String>>): String =
        params
            .filter { it.second.isNotBlank() }
            .joinToString("&") { "${it.first}=${encode(it.second)}" }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun gzip(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return output.toByteArray()
    }
}
