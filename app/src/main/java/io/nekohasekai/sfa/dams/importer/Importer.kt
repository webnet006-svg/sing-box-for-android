package io.nekohasekai.sfa.dams.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

/**
 * Parses share links and full sing-box configurations into profiles.
 *
 * This file is intentionally free of Android dependencies so it can be unit tested
 * on the JVM. Nothing here logs or persists the original links.
 */
object Importer {
    private const val OUTBOUND_TAG = "proxy"
    private const val MAX_SUBSCRIPTION_DEPTH = 2

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val prettyJson = Json {
        prettyPrint = true
    }

    enum class Protocol(val key: String, val label: String) {
        VLESS("vless", "Vless"),
        VMESS("vmess", "VMess"),
        TROJAN("trojan", "Trojan"),
        SHADOWSOCKS("shadowsocks", "SS"),
        HYSTERIA2("hysteria2", "Hysteria2"),
        TUIC("tuic", "TUIC"),
    }

    sealed interface Entry {
        data class Link(val profile: ParsedProfile) : Entry

        data class Config(val name: String, val content: String, val dedupKey: String?) : Entry

        data class Subscription(val url: String) : Entry

        data class Invalid(val reason: String) : Entry
    }

    data class ParsedProfile(
        val displayName: String,
        val protocol: Protocol,
        val host: String,
        val port: Int,
        val outbound: JsonObject,
        val dedupKey: String,
    ) {
        val profileName: String
            get() = "[${protocol.label}] $displayName"
    }

    internal data class Reality(val publicKey: String, val shortId: String)

    private data class ShareUri(
        val userInfo: String,
        val hostPort: String,
        val path: String,
        val query: Map<String, String>,
        val fragment: String,
    )

    private class ImportFailure(message: String) : Exception(message)

    fun parse(text: String): List<Entry> {
        val normalized = normalize(text)
        if (normalized.isBlank()) {
            return listOf(Entry.Invalid("empty content"))
        }
        return parseSupported(normalized, 0)
    }

    fun buildConfig(profile: ParsedProfile): String = buildConfig(profile.outbound)

    fun buildConfig(outbound: JsonObject): String {
        val root = buildJsonObject {
            put(
                "log",
                buildJsonObject {
                    put("level", "info")
                    put("timestamp", true)
                },
            )
            put(
                "dns",
                buildJsonObject {
                    put(
                        "servers",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("tag", "dns-local")
                                    put("type", "local")
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("tag", "dns-remote")
                                    put("type", "https")
                                    put("server", "1.1.1.1")
                                    put("detour", OUTBOUND_TAG)
                                },
                            )
                        },
                    )
                    put("final", "dns-remote")
                },
            )
            put(
                "inbounds",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "tun")
                            put("tag", "tun-in")
                            put(
                                "address",
                                buildJsonArray {
                                    add(JsonPrimitive("172.19.0.1/30"))
                                    add(JsonPrimitive("fdfe:dcba:9876::1/126"))
                                },
                            )
                            put("mtu", 9000)
                            put("auto_route", true)
                            put("strict_route", true)
                            put("stack", "mixed")
                        },
                    )
                },
            )
            put(
                "outbounds",
                buildJsonArray {
                    add(outbound)
                    add(
                        buildJsonObject {
                            put("type", "direct")
                            put("tag", "direct")
                        },
                    )
                },
            )
            put(
                "route",
                buildJsonObject {
                    put(
                        "rules",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("action", "sniff")
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("protocol", "dns")
                                    put("action", "hijack-dns")
                                },
                            )
                        },
                    )
                    put("final", OUTBOUND_TAG)
                    put("auto_detect_interface", true)
                    put("default_domain_resolver", "dns-local")
                },
            )
        }
        return prettyJson.encodeToString(JsonElement.serializer(), root)
    }

    fun dedupKeyOfConfig(configText: String): String? {
        val root = runCatching { json.parseToJsonElement(configText.trim()).jsonObject }.getOrNull()
            ?: return null
        val outbound = firstRemoteOutbound(root) ?: return null
        return dedupKeyOfOutbound(outbound)
    }

    private fun parseSupported(text: String, depth: Int): List<Entry> {
        val trimmed = text.trim()
        val lines = trimmed.split('\n')
            .map { stripQuotes(it.trim()) }
            .filter { it.isNotEmpty() }

        if (lines.size == 1 && isHttpUrl(lines[0])) {
            return listOf(Entry.Subscription(lines[0]))
        }

        if (trimmed.startsWith("{")) {
            val config = parseFullConfig(trimmed)
            if (config != null) {
                return listOf(config)
            }
            return listOf(Entry.Invalid("invalid sing-box JSON"))
        }

        if (lines.any { schemeOf(it) != null }) {
            val entries = mutableListOf<Entry>()
            for (line in lines) {
                if (schemeOf(line) != null) {
                    entries.add(parseLinkLine(line))
                } else if (isHttpUrl(line)) {
                    entries.add(Entry.Subscription(line))
                } else {
                    val decoded = decodeBase64Text(line)
                    if (decoded != null && looksLikeConfigText(decoded)) {
                        entries.addAll(parseSupported(decoded, depth + 1))
                    } else {
                        entries.add(Entry.Invalid("unrecognized format"))
                    }
                }
            }
            return entries
        }

        if (depth < MAX_SUBSCRIPTION_DEPTH) {
            val compact = lines.joinToString("\n").filterNot { it.isWhitespace() }
            if (compact.length >= 16) {
                val decoded = decodeBase64Text(compact)
                if (decoded != null && decoded.isNotBlank() && !decoded.contains('\uFFFD')) {
                    val nested = parseSupported(decoded, depth + 1)
                    if (nested.any { it !is Entry.Invalid }) {
                        return nested
                    }
                }
            }
        }

        return listOf(Entry.Invalid("unrecognized format"))
    }

    private fun parseLinkLine(line: String): Entry {
        return try {
            when (schemeOf(line)) {
                "vless" -> Entry.Link(parseVless(line))
                "vmess" -> Entry.Link(parseVMess(line))
                "trojan" -> Entry.Link(parseTrojan(line))
                "ss" -> Entry.Link(parseShadowsocks(line))
                "hysteria2", "hy2" -> Entry.Link(parseHysteria2(line))
                "tuic" -> Entry.Link(parseTUIC(line))
                else -> Entry.Invalid("unsupported protocol")
            }
        } catch (e: ImportFailure) {
            Entry.Invalid(e.message ?: "invalid link")
        } catch (e: Exception) {
            Entry.Invalid("invalid link")
        }
    }

    private fun parseVless(line: String): ParsedProfile {
        val uri = parseShareUri(line)
        val uuid = uri.userInfo.trim()
        if (uuid.isBlank()) throw ImportFailure("missing UUID")
        val query = uri.query
        val security = query["security"].orEmpty().lowercase().ifBlank { "none" }
        val tlsEnabled = security == "tls" || security == "xtls" || security == "reality"
        val reality = if (security == "reality") {
            val publicKey = query.firstValue("pbk", "publicKey", "public_key")
            if (publicKey.isBlank()) throw ImportFailure("Reality: missing public key")
            Reality(publicKey, query.firstValue("sid", "shortId", "short_id"))
        } else {
            null
        }

        val network = query.firstValue("type", "network").ifBlank { "tcp" }.lowercase()
        val headerType = query.firstValue("headerType", "header_type").lowercase()
        if (network == "tcp" && headerType == "http") {
            throw ImportFailure("TCP/HTTP transport is not supported")
        }
        val transport = buildTransport(
            network = network,
            host = query["host"].orEmpty(),
            path = query["path"].orEmpty(),
            serviceName = query.firstValue("serviceName", "service_name"),
            tls = tlsEnabled,
        )

        val sni = query.firstValue("sni", "serverName", "server_name").ifBlank { query["host"].orEmpty() }
            .ifBlank { uriHost(uri) }
        val alpn = parseAlpn(query["alpn"])
        val fingerprint = query.firstValue("fp", "fingerprint")
        val insecure = query.isTrue("allowInsecure", "insecure", "skip-cert-verify", "allow_insecure")
        val flow = query["flow"].orEmpty().let { if (network == "tcp" || network == "raw") it else "" }
        val port = resolvePort(uri, if (tlsEnabled) 443 else 80)

        val outbound = outbound(Protocol.VLESS.key, uriHost(uri), port) {
            put("uuid", uuid)
            if (flow.isNotBlank()) put("flow", flow)
            buildTls(tlsEnabled, sni, insecure, alpn, fingerprint, reality)?.let { put("tls", it) }
            transport?.let { put("transport", it) }
        }

        return finish(
            protocol = Protocol.VLESS,
            displayName = uri.fragment,
            host = uriHost(uri),
            port = port,
            outbound = outbound,
            identity = uuid,
        )
    }

    private fun parseVMess(line: String): ParsedProfile {
        val payload = line.substringAfter("://").substringBefore('#')
        val decoded = decodeBase64Text(payload) ?: throw ImportFailure("invalid VMess base64")
        val root = runCatching { json.parseToJsonElement(decoded).jsonObject }.getOrNull()
            ?: throw ImportFailure("invalid VMess JSON")

        val server = root.string("add").trim()
        if (server.isBlank()) throw ImportFailure("missing server")
        val port = root.int("port")
        if (port <= 0 || port > 65535) throw ImportFailure("invalid port")
        val uuid = root.string("id").trim()
        if (uuid.isBlank()) throw ImportFailure("missing UUID")
        val security = root.string("scy").ifBlank { "auto" }.lowercase()
        if (security !in VMESS_SECURITY) throw ImportFailure("unsupported VMess cipher")

        val network = root.string("net").ifBlank { "tcp" }.lowercase()
        val headerType = root.string("type").lowercase()
        if (network == "tcp" && headerType == "http") {
            throw ImportFailure("TCP/HTTP transport is not supported")
        }
        val tlsEnabled = root.string("tls").lowercase().let { it == "tls" || it == "1" || it == "true" }
        val transport = buildTransport(
            network = network,
            host = root.string("host"),
            path = root.string("path"),
            serviceName = root.string("path").trimStart('/'),
            tls = tlsEnabled,
        )
        val sni = root.string("sni").ifBlank { root.string("host") }.ifBlank { server }
        val alpn = parseAlpn(root["alpn"])
        val fingerprint = root.string("fp")
        val insecure = root.isTrue("allowInsecure", "insecure", "skip-cert-verify")
        val alterId = root.int("aid")

        val outbound = outbound(Protocol.VMESS.key, server, port) {
            put("uuid", uuid)
            put("security", security)
            if (alterId > 0) put("alter_id", alterId)
            buildTls(tlsEnabled, sni, insecure, alpn, fingerprint, null)?.let { put("tls", it) }
            transport?.let { put("transport", it) }
        }

        return finish(
            protocol = Protocol.VMESS,
            displayName = root.string("ps").ifBlank { line.substringAfter('#', "") },
            host = server,
            port = port,
            outbound = outbound,
            identity = uuid,
        )
    }

    private fun parseTrojan(line: String): ParsedProfile {
        val uri = parseShareUri(line)
        val password = uri.userInfo.trim()
        if (password.isBlank()) throw ImportFailure("missing password")
        val query = uri.query
        val security = query["security"].orEmpty().lowercase().ifBlank { "tls" }
        val tlsEnabled = security != "none"
        val reality = if (security == "reality") {
            val publicKey = query.firstValue("pbk", "publicKey", "public_key")
            if (publicKey.isBlank()) throw ImportFailure("Reality: missing public key")
            Reality(publicKey, query.firstValue("sid", "shortId", "short_id"))
        } else {
            null
        }
        val network = query.firstValue("type", "network").ifBlank { "tcp" }.lowercase()
        val transport = buildTransport(
            network = network,
            host = query["host"].orEmpty(),
            path = query["path"].orEmpty(),
            serviceName = query.firstValue("serviceName", "service_name"),
            tls = tlsEnabled,
        )
        val sni = query.firstValue("sni", "peer").ifBlank { query["host"].orEmpty() }
            .ifBlank { uriHost(uri) }
        val alpn = parseAlpn(query["alpn"])
        val fingerprint = query.firstValue("fp", "fingerprint")
        val insecure = query.isTrue("allowInsecure", "insecure", "skip-cert-verify", "allow_insecure")
        val port = resolvePort(uri, 443)

        val outbound = outbound(Protocol.TROJAN.key, uriHost(uri), port) {
            put("password", password)
            buildTls(tlsEnabled, sni, insecure, alpn, fingerprint, reality)?.let { put("tls", it) }
            transport?.let { put("transport", it) }
        }

        return finish(
            protocol = Protocol.TROJAN,
            displayName = uri.fragment,
            host = uriHost(uri),
            port = port,
            outbound = outbound,
            identity = password,
        )
    }

    private fun parseShadowsocks(line: String): ParsedProfile {
        var rest = line.substringAfter("://")
        val hashIndex = rest.indexOf('#')
        val fragment = if (hashIndex >= 0) percentDecode(rest.substring(hashIndex + 1)) else ""
        if (hashIndex >= 0) rest = rest.substring(0, hashIndex)

        var query = emptyMap<String, String>()
        val queryIndex = rest.indexOf('?')
        if (queryIndex >= 0) {
            query = parseQuery(rest.substring(queryIndex + 1))
            rest = rest.substring(0, queryIndex)
        }

        val method: String
        val password: String
        val host: String
        val port: Int

        val atIndex = rest.lastIndexOf('@')
        if (atIndex >= 0) {
            val userInfoRaw = rest.substring(0, atIndex)
            val hostPort = rest.substring(atIndex + 1).substringBefore('/')
            val userInfo = percentDecode(userInfoRaw)
            val credentials = if (userInfo.contains(':')) userInfo else decodeBase64Text(userInfo)
            if (credentials.isNullOrBlank()) throw ImportFailure("invalid SS credentials")
            val colon = credentials.indexOf(':')
            if (colon <= 0) throw ImportFailure("invalid SS credentials")
            method = credentials.substring(0, colon).trim()
            password = credentials.substring(colon + 1)
            val resolved = resolveHostPort(hostPort, -1)
            host = resolved.first
            port = resolved.second
        } else {
            val decoded = decodeBase64Text(rest) ?: throw ImportFailure("invalid SS base64")
            val decodedAtIndex = decoded.lastIndexOf('@')
            if (decodedAtIndex <= 0) throw ImportFailure("invalid SS payload")
            val credentials = decoded.substring(0, decodedAtIndex)
            val colon = credentials.indexOf(':')
            if (colon <= 0) throw ImportFailure("invalid SS credentials")
            method = credentials.substring(0, colon).trim()
            password = credentials.substring(colon + 1)
            val resolved = resolveHostPort(decoded.substring(decodedAtIndex + 1), -1)
            host = resolved.first
            port = resolved.second
        }

        if (method.isBlank()) throw ImportFailure("missing SS method")
        if (password.isBlank()) throw ImportFailure("missing SS password")

        var plugin = query["plugin"].orEmpty()
        var pluginOptions = ""
        if (plugin.isNotBlank()) {
            val separator = plugin.indexOf(';')
            if (separator >= 0) {
                pluginOptions = plugin.substring(separator + 1)
                plugin = plugin.substring(0, separator)
            }
            plugin = when (plugin.lowercase()) {
                "simple-obfs", "obfs-local" -> "obfs-local"
                "v2ray-plugin" -> "v2ray-plugin"
                else -> throw ImportFailure("unsupported SS plugin")
            }
        }

        val outbound = outbound(Protocol.SHADOWSOCKS.key, host, port) {
            put("method", method)
            put("password", password)
            if (plugin.isNotBlank()) {
                put("plugin", plugin)
                if (pluginOptions.isNotBlank()) put("plugin_opts", pluginOptions)
            }
        }

        return finish(
            protocol = Protocol.SHADOWSOCKS,
            displayName = fragment,
            host = host,
            port = port,
            outbound = outbound,
            identity = "$method:$password",
        )
    }

    private fun parseHysteria2(line: String): ParsedProfile {
        val uri = parseShareUri(line)
        val password = uri.userInfo.trim()
        if (password.isBlank()) throw ImportFailure("missing password")
        val query = uri.query
        val host = uriHost(uri)
        val port = resolvePort(uri, 443)
        val sni = query.firstValue("sni", "peer").ifBlank { host }
        val insecure = query.isTrue("insecure", "allowInsecure", "allow_insecure", "skip-cert-verify")
        val alpn = parseAlpn(query["alpn"])

        val obfsType = query["obfs"].orEmpty().lowercase()
        if (obfsType.isNotBlank() && obfsType != "salamander" && obfsType != "gecko") {
            throw ImportFailure("unsupported Hysteria2 obfuscation")
        }
        val obfsPassword = query.firstValue("obfs-password", "obfs_password")
        if (obfsType.isNotBlank() && obfsPassword.isBlank()) {
            throw ImportFailure("missing Hysteria2 obfuscation password")
        }

        val outbound = outbound(Protocol.HYSTERIA2.key, host, port) {
            put("password", password)
            if (obfsType.isNotBlank()) {
                put(
                    "obfs",
                    buildJsonObject {
                        put("type", obfsType)
                        put("password", obfsPassword)
                    },
                )
            }
            buildTls(true, sni, insecure, alpn, "", null)?.let { put("tls", it) }
        }

        return finish(
            protocol = Protocol.HYSTERIA2,
            displayName = uri.fragment,
            host = host,
            port = port,
            outbound = outbound,
            identity = password,
        )
    }

    private fun parseTUIC(line: String): ParsedProfile {
        val uri = parseShareUri(line)
        val separator = uri.userInfo.indexOf(':')
        if (separator <= 0) throw ImportFailure("missing TUIC UUID or password")
        val uuid = uri.userInfo.substring(0, separator).trim()
        val password = uri.userInfo.substring(separator + 1)
        if (uuid.isBlank()) throw ImportFailure("missing TUIC UUID")
        if (password.isBlank()) throw ImportFailure("missing TUIC password")

        val query = uri.query
        val congestion = query["congestion_control"].orEmpty().lowercase()
        if (congestion.isNotBlank() && congestion !in TUIC_CONGESTION) {
            throw ImportFailure("unsupported TUIC congestion control")
        }
        val relayMode = query["udp_relay_mode"].orEmpty().lowercase()
        if (relayMode.isNotBlank() && relayMode != "native" && relayMode != "quic") {
            throw ImportFailure("unsupported TUIC relay mode")
        }
        val host = uriHost(uri)
        val port = resolvePort(uri, 443)
        val sni = query.firstValue("sni", "peer").ifBlank { host }
        val insecure = query.isTrue("allow_insecure", "insecure", "allowInsecure", "skip-cert-verify")
        val alpn = parseAlpn(query["alpn"]).ifEmpty { listOf("h3") }

        val outbound = outbound(Protocol.TUIC.key, host, port) {
            put("uuid", uuid)
            put("password", password)
            if (congestion.isNotBlank()) put("congestion_control", congestion)
            if (relayMode.isNotBlank()) put("udp_relay_mode", relayMode)
            buildTls(true, sni, insecure, alpn, "", null)?.let { put("tls", it) }
        }

        return finish(
            protocol = Protocol.TUIC,
            displayName = uri.fragment,
            host = host,
            port = port,
            outbound = outbound,
            identity = "$uuid:$password",
        )
    }

    private fun finish(
        protocol: Protocol,
        displayName: String,
        host: String,
        port: Int,
        outbound: JsonObject,
        identity: String,
    ): ParsedProfile {
        val name = displayName.trim().ifBlank { host }
        val dedupKey = "${protocol.key}|${host.lowercase()}|$port|$identity"
        return ParsedProfile(
            displayName = name,
            protocol = protocol,
            host = host,
            port = port,
            outbound = outbound,
            dedupKey = dedupKey,
        )
    }

    private fun parseShareUri(line: String): ShareUri {
        var rest = line.substringAfter("://")
        val hashIndex = rest.indexOf('#')
        val fragment = if (hashIndex >= 0) percentDecode(rest.substring(hashIndex + 1)) else ""
        if (hashIndex >= 0) rest = rest.substring(0, hashIndex)

        var query = emptyMap<String, String>()
        val queryIndex = rest.indexOf('?')
        if (queryIndex >= 0) {
            query = parseQuery(rest.substring(queryIndex + 1))
            rest = rest.substring(0, queryIndex)
        }

        val slashIndex = rest.indexOf('/')
        val authority = if (slashIndex >= 0) rest.substring(0, slashIndex) else rest
        val path = if (slashIndex >= 0) rest.substring(slashIndex) else ""
        val atIndex = authority.lastIndexOf('@')
        val userInfo = if (atIndex >= 0) percentDecode(authority.substring(0, atIndex)) else ""
        val hostPort = if (atIndex >= 0) authority.substring(atIndex + 1) else authority

        if (hostPort.isBlank()) throw ImportFailure("missing server")
        return ShareUri(userInfo, hostPort, path, query, fragment)
    }

    private fun uriHost(uri: ShareUri): String {
        return resolveHostPort(uri.hostPort, -1).first
    }

    private fun resolvePort(uri: ShareUri, defaultPort: Int): Int {
        return resolveHostPort(uri.hostPort, defaultPort).second
    }

    private fun resolveHostPort(hostPort: String, defaultPort: Int): Pair<String, Int> {
        val value = hostPort.trim()
        if (value.isBlank()) throw ImportFailure("missing server")
        if (value.startsWith("[")) {
            val end = value.indexOf(']')
            if (end <= 1) throw ImportFailure("invalid server")
            val host = value.substring(1, end)
            val remainder = value.substring(end + 1)
            val port = if (remainder.startsWith(":")) parsePort(remainder.substring(1)) else defaultPort
            if (port <= 0) throw ImportFailure("missing port")
            return host to port
        }
        val colon = value.lastIndexOf(':')
        if (colon > 0 && value.indexOf(':') == colon) {
            val host = value.substring(0, colon)
            val port = parsePort(value.substring(colon + 1))
            if (host.isBlank()) throw ImportFailure("missing server")
            return host to port
        }
        if (value.contains(':') && colon != value.indexOf(':')) {
            // Bare IPv6 address without brackets. A port cannot be inferred safely.
            if (defaultPort <= 0) throw ImportFailure("missing port")
            return value to defaultPort
        }
        if (defaultPort <= 0) throw ImportFailure("missing port")
        return value to defaultPort
    }

    private fun parsePort(raw: String): Int {
        val port = raw.toIntOrNull() ?: throw ImportFailure("invalid port")
        if (port <= 0 || port > 65535) throw ImportFailure("invalid port")
        return port
    }

    internal fun buildTransport(
        network: String,
        host: String,
        path: String,
        serviceName: String,
        tls: Boolean,
    ): JsonObject? {
        return when (network) {
            "", "tcp", "none", "raw" -> null
            "ws", "websocket" -> buildJsonObject {
                put("type", "ws")
                if (path.isNotBlank()) put("path", path)
                if (host.isNotBlank()) {
                    put(
                        "headers",
                        buildJsonObject {
                            put("Host", host)
                        },
                    )
                }
            }
            "grpc" -> buildJsonObject {
                put("type", "grpc")
                val name = serviceName.ifBlank { path.trimStart('/') }
                if (name.isNotBlank()) put("service_name", name)
            }
            "httpupgrade" -> buildJsonObject {
                put("type", "httpupgrade")
                if (path.isNotBlank()) put("path", path)
                if (host.isNotBlank()) put("host", host)
            }
            "http", "h2" -> {
                if (!tls) throw ImportFailure("HTTP/2 requires TLS")
                buildJsonObject {
                    put("type", "http")
                    if (path.isNotBlank()) put("path", path)
                    if (host.isNotBlank()) {
                        put(
                            "host",
                            buildJsonArray {
                                add(JsonPrimitive(host))
                            },
                        )
                    }
                }
            }
            "xhttp", "split-http" -> throw ImportFailure("transport xhttp is not supported by this core")
            else -> throw ImportFailure("unsupported transport")
        }
    }

    internal fun buildTls(
        enabled: Boolean,
        serverName: String,
        insecure: Boolean,
        alpn: List<String>,
        fingerprint: String,
        reality: Reality?,
    ): JsonObject? {
        if (!enabled) return null
        return buildJsonObject {
            put("enabled", true)
            if (serverName.isNotBlank()) put("server_name", serverName)
            if (insecure) put("insecure", true)
            if (alpn.isNotEmpty()) {
                put(
                    "alpn",
                    buildJsonArray {
                        alpn.forEach { add(JsonPrimitive(it)) }
                    },
                )
            }
            if (fingerprint.isNotBlank() && !fingerprint.equals("none", true)) {
                put(
                    "utls",
                    buildJsonObject {
                        put("enabled", true)
                        put("fingerprint", fingerprint)
                    },
                )
            }
            if (reality != null) {
                put(
                    "reality",
                    buildJsonObject {
                        put("enabled", true)
                        put("public_key", reality.publicKey)
                        if (reality.shortId.isNotBlank()) put("short_id", reality.shortId)
                    },
                )
            }
        }
    }

    internal fun outbound(
        type: String,
        host: String,
        port: Int,
        block: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        return buildJsonObject {
            put("type", type)
            put("tag", OUTBOUND_TAG)
            put("server", host)
            put("server_port", port)
            block()
        }
    }

    private fun parseFullConfig(content: String): Entry.Config? {
        val root = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull() ?: return null
        val hasOutbounds = root["outbounds"] is JsonArray
        val hasEndpoints = root["endpoints"] is JsonArray
        if (!hasOutbounds && !hasEndpoints) {
            return null
        }
        val outbound = firstRemoteOutbound(root)
        val host = outbound?.string("server").orEmpty()
        val name = if (host.isNotBlank()) "[Config] $host" else "[Config]"
        return Entry.Config(
            name = name,
            content = content,
            dedupKey = outbound?.let(::dedupKeyOfOutbound),
        )
    }

    private fun firstRemoteOutbound(root: JsonObject): JsonObject? {
        val outbounds = root["outbounds"] as? JsonArray ?: return null
        return outbounds.filterIsInstance<JsonObject>().firstOrNull { outbound ->
            val type = outbound.string("type").lowercase()
            type.isNotBlank() && type != "direct" && type != "block" && type != "dns" &&
                outbound.string("server").isNotBlank()
        }
    }

    private fun dedupKeyOfOutbound(outbound: JsonObject): String? {
        val type = outbound.string("type").lowercase()
        val host = outbound.string("server").lowercase()
        val port = outbound.int("server_port")
        if (type.isBlank() || host.isBlank() || port <= 0) return null
        val identity = when (type) {
            "vless", "vmess" -> outbound.string("uuid")
            "trojan", "hysteria2" -> outbound.string("password")
            "shadowsocks" -> "${outbound.string("method")}:${outbound.string("password")}"
            "tuic" -> "${outbound.string("uuid")}:${outbound.string("password")}"
            else -> outbound.string("tag")
        }
        return "$type|$host|$port|$identity"
    }

    private fun JsonObject.string(key: String): String {
        return (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    private fun JsonObject.int(key: String): Int {
        return (this[key] as? JsonPrimitive)?.intOrNull ?: 0
    }

    private fun JsonObject.isTrue(vararg keys: String): Boolean {
        for (key in keys) {
            val primitive = this[key] as? JsonPrimitive ?: continue
            primitive.booleanOrNull?.let { return it }
            when (primitive.contentOrNull?.lowercase()) {
                "1", "true", "yes" -> return true
            }
        }
        return false
    }

    private fun Map<String, String>.firstValue(vararg keys: String): String {
        for (key in keys) {
            val value = this[key]
            if (!value.isNullOrBlank()) return value
        }
        return ""
    }

    private fun Map<String, String>.isTrue(vararg keys: String): Boolean {
        for (key in keys) {
            when (this[key]?.lowercase()) {
                "1", "true", "yes" -> return true
            }
        }
        return false
    }

    private fun parseAlpn(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseAlpn(element: JsonElement?): List<String> {
        val primitive = element as? JsonPrimitive
        if (primitive != null) {
            return primitive.contentOrNull.orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseQuery(raw: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (part in raw.split('&')) {
            if (part.isEmpty()) continue
            val separator = part.indexOf('=')
            if (separator < 0) {
                result[percentDecode(part).lowercase()] = ""
            } else {
                val key = percentDecode(part.substring(0, separator)).lowercase()
                result[key] = percentDecode(part.substring(separator + 1))
            }
        }
        return result
    }

    private fun schemeOf(line: String): String? {
        val index = line.indexOf("://")
        if (index <= 0) return null
        return when (val scheme = line.substring(0, index).lowercase()) {
            "vless", "vmess", "trojan", "ss", "hysteria2", "hy2", "tuic" -> scheme
            else -> null
        }
    }

    private fun isHttpUrl(line: String): Boolean {
        val lower = line.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun looksLikeConfigText(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.contains("://") || trimmed.startsWith("{")
    }

    private fun normalize(text: String): String {
        return text
            .replace("\uFEFF", "")
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u200B', ' ')
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\u2060", "")
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace('\u201E', '"')
            .replace('\u201F', '"')
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
    }

    private fun stripQuotes(line: String): String {
        if (line.length < 2) return line
        val first = line.first()
        val last = line.last()
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return line.substring(1, line.length - 1).trim()
        }
        return line
    }

    private fun percentDecode(value: String): String {
        val output = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val hex = value.substring(index + 1, index + 3)
                val decoded = hex.toIntOrNull(16)
                if (decoded != null) {
                    output.write(decoded)
                    index += 3
                    continue
                }
            }
            val bytes = char.toString().toByteArray(Charsets.UTF_8)
            output.write(bytes, 0, bytes.size)
            index++
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun decodeBase64Text(value: String): String? {
        val bytes = decodeBase64(value) ?: return null
        return bytes.toString(Charsets.UTF_8)
    }

    private fun decodeBase64(value: String): ByteArray? {
        var input = value.trim().filterNot { it.isWhitespace() }
        if (input.isEmpty()) return null
        input = input.replace('-', '+').replace('_', '/')
        val output = ByteArrayOutputStream(input.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (char in input) {
            if (char == '=') continue
            val digit = BASE64_ALPHABET.indexOf(char)
            if (digit < 0) return null
            buffer = (buffer shl 6) or digit
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output.write((buffer shr bits) and 0xFF)
            }
        }
        return output.toByteArray()
    }

    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val VMESS_SECURITY = setOf(
        "auto",
        "none",
        "zero",
        "aes-128-cfb",
        "aes-128-gcm",
        "chacha20-poly1305",
        "aes-128-ctr",
        "aes-192-ctr",
        "aes-256-ctr",
    )

    private val TUIC_CONGESTION = setOf("cubic", "new_reno", "bbr")
}
