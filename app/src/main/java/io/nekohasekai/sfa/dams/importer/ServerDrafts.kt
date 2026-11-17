package io.nekohasekai.sfa.dams.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Éditeur de serveur « en mode texte » : un brouillon de champs lisibles
 * (nom, adresse, port, UUID, TLS…) converti en configuration sing-box.
 *
 * Toutes les fonctions sont pures (testables sur JVM) et ne journalisent
 * jamais les secrets.
 */
data class ServerDraft(
    val name: String = "",
    val protocol: Importer.Protocol = Importer.Protocol.VLESS,
    val host: String = "",
    val port: Int = 443,
    val identity: String = "",
    val security: String = "none",
    val sni: String = "",
    val alpn: String = "",
    val fingerprint: String = "",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val flow: String = "",
    val vmessSecurity: String = "auto",
    val alterId: Int = 0,
    val transport: String = "tcp",
    val path: String = "",
    val hostHeader: String = "",
    val serviceName: String = "",
    val allowInsecure: Boolean = false,
) {
    val identityLabel: String
        get() = if (protocol == Importer.Protocol.TROJAN) "Mot de passe" else "UUID"
}

object ServerDrafts {
    private const val MAX_NAME_LENGTH = 80

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val VMESS_SECURITIES =
        setOf("auto", "none", "zero", "aes-128-gcm", "chacha20-poly1305")

    private val SUPPORTED_TRANSPORTS =
        setOf("tcp", "ws", "grpc", "httpupgrade", "http")

    val examples: List<Pair<String, ServerDraft>> =
        listOf(
            "VLESS + REALITY (TCP)" to
                ServerDraft(
                    name = "Exemple REALITY",
                    protocol = Importer.Protocol.VLESS,
                    host = "exemple.com",
                    port = 443,
                    identity = "00000000-0000-0000-0000-000000000000",
                    security = "reality",
                    sni = "exemple.com",
                    fingerprint = "chrome",
                    realityPublicKey = "REMPLACER_PAR_LA_CLE_PUBLIQUE",
                    realityShortId = "0123abcd",
                    flow = "xtls-rprx-vision",
                ),
            "VLESS + TLS + WebSocket" to
                ServerDraft(
                    name = "Exemple WebSocket",
                    protocol = Importer.Protocol.VLESS,
                    host = "exemple.com",
                    port = 443,
                    identity = "00000000-0000-0000-0000-000000000000",
                    security = "tls",
                    sni = "exemple.com",
                    transport = "ws",
                    path = "/ws",
                    hostHeader = "exemple.com",
                ),
            "VMess + TLS + WebSocket" to
                ServerDraft(
                    name = "Exemple VMess",
                    protocol = Importer.Protocol.VMESS,
                    host = "exemple.com",
                    port = 443,
                    identity = "00000000-0000-0000-0000-000000000000",
                    security = "tls",
                    sni = "exemple.com",
                    transport = "ws",
                    path = "/vmess",
                    hostHeader = "exemple.com",
                ),
            "Trojan + TLS (TCP)" to
                ServerDraft(
                    name = "Exemple Trojan",
                    protocol = Importer.Protocol.TROJAN,
                    host = "exemple.com",
                    port = 443,
                    identity = "mot-de-passe",
                    security = "tls",
                    sni = "exemple.com",
                ),
        )

    /** Construit le profil à partir du brouillon (léve IllegalArgumentException si invalide). */
    fun build(draft: ServerDraft): Importer.ParsedProfile {
        val host = draft.host.trim()
        if (host.isBlank()) throw IllegalArgumentException("Adresse du serveur requise.")
        if (draft.port !in 1..65535) throw IllegalArgumentException("Port invalide (1 à 65535).")
        val identity = draft.identity.trim()
        if (identity.isBlank()) {
            throw IllegalArgumentException(
                if (draft.protocol == Importer.Protocol.TROJAN) "Mot de passe requis." else "UUID requis.",
            )
        }
        val transport = draft.transport.trim().lowercase().ifBlank { "tcp" }
        if (transport !in SUPPORTED_TRANSPORTS) {
            throw IllegalArgumentException("Transport inconnu : ${draft.transport}.")
        }
        val security = draft.security.trim().lowercase().ifBlank { "none" }
        if (security !in setOf("none", "tls", "reality")) {
            throw IllegalArgumentException("Sécurité inconnue : ${draft.security}.")
        }
        if (security == "reality" && draft.realityPublicKey.trim().isBlank()) {
            throw IllegalArgumentException("Clé publique REALITY requise.")
        }

        val tlsEnabled = security != "none"
        val reality =
            if (security == "reality") {
                Importer.Reality(draft.realityPublicKey.trim(), draft.realityShortId.trim())
            } else {
                null
            }
        val sni =
            draft.sni.trim().ifBlank { draft.hostHeader.trim() }.ifBlank { host }
        val alpn = draft.alpn.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        val flow =
            draft.flow.trim().let { value ->
                if (transport == "tcp" && draft.protocol == Importer.Protocol.VLESS) value else ""
            }
        val vmessSecurity =
            draft.vmessSecurity.trim().lowercase().ifBlank { "auto" }.let {
                if (it in VMESS_SECURITIES) it else "auto"
            }

        val outbound =
            try {
                Importer.outbound(draft.protocol.key, host, draft.port) {
                    when (draft.protocol) {
                        Importer.Protocol.VLESS -> {
                            put("uuid", identity)
                            if (flow.isNotBlank()) put("flow", flow)
                        }
                        Importer.Protocol.VMESS -> {
                            put("uuid", identity)
                            put("security", vmessSecurity)
                            if (draft.alterId > 0) put("alter_id", draft.alterId)
                        }
                        Importer.Protocol.TROJAN -> {
                            put("password", identity)
                        }
                        else -> throw IllegalArgumentException("Protocole non pris en charge par l'éditeur.")
                    }
                    Importer.buildTls(
                        enabled = tlsEnabled,
                        serverName = sni,
                        insecure = draft.allowInsecure,
                        alpn = alpn,
                        fingerprint = draft.fingerprint.trim(),
                        reality = reality,
                    )?.let { put("tls", it) }
                    Importer.buildTransport(
                        network = transport,
                        host = draft.hostHeader.trim(),
                        path = draft.path.trim(),
                        serviceName = draft.serviceName.trim(),
                        tls = tlsEnabled,
                    )?.let { put("transport", it) }
                }
            } catch (e: Exception) {
                throw IllegalArgumentException(e.message ?: "Configuration invalide.")
            }

        val name = draft.name.trim().take(MAX_NAME_LENGTH).ifBlank { host }
        return Importer.ParsedProfile(
            displayName = name,
            protocol = draft.protocol,
            host = host,
            port = draft.port,
            outbound = outbound,
            dedupKey = "${draft.protocol.key}|${host.lowercase()}|${draft.port}|$identity",
        )
    }

    /** Configuration sing-box complète prête à être enregistrée comme profil local. */
    fun buildConfig(draft: ServerDraft): String = Importer.buildConfig(build(draft).outbound)

    /** Relit une configuration existante et reconstruit le brouillon (null si non prise en charge). */
    fun fromConfig(content: String): ServerDraft? {
        val root = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull() ?: return null
        val outbound =
            (root["outbounds"] as? JsonArray)
                ?.filterIsInstance<JsonObject>()
                ?.firstOrNull { candidate ->
                    val type = candidate.string("type").lowercase()
                    type in setOf("vless", "vmess", "trojan") &&
                        candidate.string("server").isNotBlank()
                } ?: return null

        val protocol =
            when (outbound.string("type").lowercase()) {
                "vless" -> Importer.Protocol.VLESS
                "vmess" -> Importer.Protocol.VMESS
                else -> Importer.Protocol.TROJAN
            }
        val tls = outbound["tls"] as? JsonObject
        val tlsEnabled = tls?.bool("enabled") == true
        val reality = tls?.get("reality") as? JsonObject
        val realityEnabled = reality?.bool("enabled") == true
        val utls = tls?.get("utls") as? JsonObject
        val transport = outbound["transport"] as? JsonObject
        val transportType = transport?.string("type").orEmpty().ifBlank { "tcp" }
        val headers = transport?.get("headers") as? JsonObject

        return ServerDraft(
            name = "",
            protocol = protocol,
            host = outbound.string("server"),
            port = outbound.int("server_port"),
            identity =
                when (protocol) {
                    Importer.Protocol.TROJAN -> outbound.string("password")
                    else -> outbound.string("uuid")
                },
            security =
                when {
                    realityEnabled -> "reality"
                    tlsEnabled -> "tls"
                    else -> "none"
                },
            sni = tls?.string("server_name").orEmpty(),
            alpn =
                (tls?.get("alpn") as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?.joinToString(",")
                    .orEmpty(),
            fingerprint = utls?.string("fingerprint").orEmpty(),
            realityPublicKey = reality?.string("public_key").orEmpty(),
            realityShortId = reality?.string("short_id").orEmpty(),
            flow = outbound.string("flow"),
            vmessSecurity = outbound.string("security").ifBlank { "auto" },
            alterId = outbound.int("alter_id"),
            transport = if (transportType == "raw") "tcp" else transportType,
            path = transport?.string("path").orEmpty(),
            hostHeader =
                headers?.string("Host").orEmpty().ifBlank {
                    (transport?.get("host") as? JsonArray)
                        ?.firstOrNull()
                        ?.let { (it as? JsonPrimitive)?.contentOrNull }
                        .orEmpty()
                        .ifBlank { transport?.string("host").orEmpty() }
                },
            serviceName = transport?.string("service_name").orEmpty(),
            allowInsecure = tls?.bool("insecure") == true,
        )
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull == true
}
