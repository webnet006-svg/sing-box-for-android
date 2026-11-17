package io.nekohasekai.sfa.dams.importer

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.buildJsonObject
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
 * Les secrets sont nécessaires au fonctionnement des liens : l'affichage
 * destiné à l'écran les masque ([maskSensitive]), jamais les liens exportés.
 */
object ShareExport {
    private const val CODED_PREFIX = "a2mtun://config/"
    private const val SIMPLE_PREFIX = "a2mtun://server/"

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
        val draft = ServerDrafts.fromConfig(config) ?: return null
        val shareUri = shareUri(draft, name)
        return Exported(
            protocolLabel = draft.protocol.label,
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

    private fun query(params: List<Pair<String, String>>): String =
        params
            .filter { it.second.isNotBlank() }
            .joinToString("&") { "${it.first}=${encode(it.second)}" }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun gzip(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return output.toByteArray()
    }
}
