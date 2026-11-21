package io.nekohasekai.sfa.bg

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Décorations de configuration appliquées par A2M TUN au chargement du tunnel.
 *
 * Aucune règle inventée : on injecte des objets de configuration sing-box standards
 * (format DNS nouveau, obligatoire depuis sing-box 1.14). En cas d'échec du parse
 * (config exotique), la configuration originale est rendue telle quelle.
 */
object A2MConfigDecorations {

    /** Remplacement DNS choisi par l'utilisateur (types sing-box : https/tls/quic/udp). */
    data class DnsOverride(
        val type: String,
        val remoteServer: String,
        val bootstrapServer: String,
    )

    /** Réglages moteur : MTU, pile TUN, multiplexage, niveau de journal. */
    data class EngineOptions(
        val mtuMode: String,
        val mtuCustom: Int,
        val stackMode: String,
        val muxEnabled: Boolean,
        val logLevel: String,
    )

    /** Options TLS appliquées aux sortants : fragmentation et envoi du SNI. */
    data class TlsOptions(
        val fragment: Boolean,
        val sniEnabled: Boolean,
    )

    /**
     * Chaîne complète des décorations, pilotée par des options explicites
     * (lues dans Settings par BoxService.effectiveContent) : chaque étape
     * échoue indépendamment sans casser les suivantes.
     */
    fun decorate(
        raw: String,
        bypassPrivate: Boolean,
        dns: DnsOverride?,
        engine: EngineOptions?,
        adBlock: Boolean,
        hotspotPort: Int?,
        tls: TlsOptions? = null,
        gaming: Boolean = false,
    ): String {
        // Les profils importés peuvent encore utiliser l'ancien format DNS,
        // supprimé par sing-box 1.14. On les normalise avant toute autre
        // décoration, même lorsque le DNS personnalisé est désactivé.
        var content = runCatching { normalizeDns(raw) }.getOrDefault(raw)
        if (bypassPrivate) content = runCatching { withBypassPrivateNetworks(content) }.getOrDefault(content)
        if (dns != null) content = runCatching { withDnsOverride(content, dns) }.getOrDefault(content)
        if (engine != null) content = runCatching { withEngine(content, engine) }.getOrDefault(content)
        if (tls != null) content = runCatching { withTlsOptions(content, tls) }.getOrDefault(content)
        if (gaming) content = runCatching { withGamingMode(content) }.getOrDefault(content)
        if (hotspotPort != null) content = runCatching { withHotspotProxy(content, hotspotPort) }.getOrDefault(content)
        if (adBlock) content = runCatching { withAdBlock(content) }.getOrDefault(content)
        return content
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private const val ADS_RULE_SET_TAG = "geosite-category-ads-all"
    private const val ADS_RULE_SET_URL =
        "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ads-all.srs"

    /** Canonical host names keep DoH/DoT certificate validation working when a
     * provider is selected through its well-known IP address. */
    private fun canonicalDnsHost(value: String): String =
        when (value.trim().removePrefix("[").removeSuffix("]")) {
            "1.1.1.1", "1.0.0.1" -> "cloudflare-dns.com"
            "8.8.8.8", "8.8.4.4" -> "dns.google"
            "9.9.9.9", "149.112.112.112" -> "dns.quad9.net"
            "94.140.14.14", "94.140.15.15" -> "dns.adguard-dns.com"
            else -> value.trim()
        }

    // ── Helpers partagés ─────────────────────────────────────────────────────

    private fun parseRoot(content: String): MutableMap<String, JsonElement> =
        json.parseToJsonElement(content).jsonObject.toMutableMap()

    private fun outboundListOf(root: MutableMap<String, JsonElement>): MutableList<JsonElement> =
        (root["outbounds"] as? JsonArray)?.toMutableList() ?: mutableListOf()

    private fun outboundType(element: JsonElement): String? =
        (element as? JsonObject)?.get("type")?.let { t -> (t as? JsonPrimitive)?.content }

    private fun outboundTag(element: JsonElement): String? =
        (element as? JsonObject)?.get("tag")?.let { t -> (t as? JsonPrimitive)?.content }

    /** Réutilise un sortant du type demandé s'il existe, sinon l'ajoute avec le tag canonique. */
    private fun ensureOutboundOfType(outbounds: MutableList<JsonElement>, type: String): String {
        val existing = outbounds.firstOrNull { outboundType(it) == type }?.let { outboundTag(it) }
        if (existing != null) return existing
        val tag = type
        if (outbounds.none { outboundTag(it) == tag }) {
            outbounds.add(
                buildJsonObject {
                    put("type", type)
                    put("tag", tag)
                },
            )
        }
        return tag
    }

    private fun routeMap(root: MutableMap<String, JsonElement>): MutableMap<String, JsonElement> =
        (root["route"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()

    private fun encode(root: MutableMap<String, JsonElement>): String =
        json.encodeToString(JsonObject.serializer(), JsonObject(root))

    /** Ajoute (ou remplace) une règle en tête de route.rules. */
    private fun prependRule(route: MutableMap<String, JsonElement>, rule: JsonObject) {
        val rules = (route["rules"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        rules.add(0, rule)
        route["rules"] = JsonArray(rules)
    }

    private val nonProxyOutboundTypes = setOf("direct", "block", "dns")

    /** Serveurs DNS qui ne dialent pas eux-mêmes : un detour n'y a pas de sens. */
    private val nonDialingDnsTypes = setOf(
        "local",
        "hosts",
        "dhcp",
        "mdns",
        "fakeip",
        "tailscale",
        "openconnect",
        "openvpn",
        "resolved",
    )

    private data class ParsedDnsAddress(
        val type: String,
        val server: String? = null,
        val port: Int? = null,
        val path: String? = null,
        val interfaceName: String? = null,
    )

    /**
     * Tag d'un sortant qui peut réellement porter du trafic (proxy, selector,
     * urltest…). On refuse route.final s'il pointe vers direct/block/dns :
     * un detour DNS vers un direct vide fait échouer sing-box 1.12+.
     */
    private fun proxyTagOf(
        root: Map<String, JsonElement>,
        outbounds: List<JsonElement>,
    ): String? {
        val finalTag = ((root["route"] as? JsonObject)?.get("final") as? JsonPrimitive)?.content
        if (finalTag != null) {
            val finalType = outbounds.firstOrNull { outboundTag(it) == finalTag }?.let(::outboundType)
            if (finalType != null && finalType !in nonProxyOutboundTypes) return finalTag
        }
        return outbounds.firstOrNull {
            val type = outboundType(it)
            type != null && type !in nonProxyOutboundTypes
        }?.let(::outboundTag)
    }

    // ── Normalisation DNS (sing-box 1.12+) ───────────────────────────────────

    /**
     * Convertit les serveurs DNS legacy (`address`) vers le format `type`/`server`
     * de sing-box 1.12+, migre `address_resolver` vers `domain_resolver` et
     * supprime les detours directs invalides.
     *
     * Depuis sing-box 1.12, l'absence de detour signifie « direct par défaut » et
     * est valide. Un detour explicite vers un sortant direct vide est refusé ;
     * c'est la cause du crash `detour to an empty direct outbound makes no sense`.
     */
    fun normalizeDns(content: String): String =
        runCatching {
            val root = parseRoot(content)
            val dns = root["dns"] as? JsonObject ?: return@runCatching content
            val servers = dns["servers"] as? JsonArray ?: return@runCatching content
            if (servers.isEmpty()) return@runCatching content

            val outbounds = outboundListOf(root)
            val outboundTypes =
                outbounds.mapNotNull { element ->
                    val tag = outboundTag(element) ?: return@mapNotNull null
                    tag to outboundType(element)
                }.toMap()

            val resolverTags = resolverTagsOf(root, dns)
            val dnsMutable = dns.toMutableMap()
            dnsMutable["servers"] = JsonArray(
                servers.map { normalizeDnsServer(it, outboundTypes, resolverTags, dnsMutable) },
            )
            // `dns.fakeip` n'existe plus dans le nouveau format : les plages
            // ont été recopiées dans le serveur de type fakeip.
            if ((dnsMutable["servers"] as? JsonArray)?.any {
                    (it as? JsonObject)?.get("type")?.let { type -> (type as? JsonPrimitive)?.content } == "fakeip"
                } == true
            ) {
                dnsMutable.remove("fakeip")
            }
            root["dns"] = JsonObject(dnsMutable)
            encode(root)
        }.getOrDefault(content)

    /**
     * Tags DNS utilisés pour résoudre des noms de serveurs (bootstrap/local).
     * Ils ne doivent jamais passer par le proxy, sous peine de dépendance
     * circulaire : le proxy a besoin d'eux pour être joint.
     */
    private fun resolverTagsOf(
        root: Map<String, JsonElement>,
        dns: JsonObject,
    ): Set<String> {
        val tags = mutableSetOf<String>()

        fun addResolver(value: JsonElement?) {
            when (value) {
                is JsonPrimitive -> value.content.takeIf { it.isNotBlank() }?.let(tags::add)
                is JsonObject ->
                    (value["server"] as? JsonPrimitive)?.content
                        ?.takeIf { it.isNotBlank() }
                        ?.let(tags::add)
                else -> Unit
            }
        }

        val route = root["route"] as? JsonObject
        addResolver(route?.get("default_domain_resolver"))
        (root["outbounds"] as? JsonArray)?.forEach {
            addResolver((it as? JsonObject)?.get("domain_resolver"))
        }
        (root["inbounds"] as? JsonArray)?.forEach {
            addResolver((it as? JsonObject)?.get("domain_resolver"))
        }
        (dns["servers"] as? JsonArray)?.forEach {
            val server = it as? JsonObject
            addResolver(server?.get("domain_resolver"))
            addResolver(server?.get("address_resolver"))
        }
        (route?.get("rules") as? JsonArray)?.forEach {
            addResolver((it as? JsonObject)?.get("domain_resolver"))
        }
        return tags
    }

    private fun normalizeDnsServer(
        element: JsonElement,
        outboundTypes: Map<String, String?>,
        resolverTags: Set<String>,
        dns: MutableMap<String, JsonElement>,
    ): JsonElement {
        val obj = element as? JsonObject ?: return element
        val mutable = obj.toMutableMap()

        // 1. Ancien champ address -> type/server/server_port/path.
        val legacyAddress = (mutable["address"] as? JsonPrimitive)?.content
        val existingType = (mutable["type"] as? JsonPrimitive)?.content
        if (existingType.isNullOrBlank() && !legacyAddress.isNullOrBlank()) {
            val parsed = parseDnsAddress(legacyAddress)
            if (parsed != null) {
                mutable["type"] = JsonPrimitive(parsed.type)
                parsed.server?.let { mutable["server"] = JsonPrimitive(it) }
                parsed.port?.let { mutable["server_port"] = JsonPrimitive(it) }
                parsed.path?.let { mutable["path"] = JsonPrimitive(it) }
                parsed.interfaceName?.let { mutable["interface"] = JsonPrimitive(it) }
                if (parsed.type == "fakeip") {
                    (dns["fakeip"] as? JsonObject)?.let { fakeip ->
                        fakeip["inet4_range"]?.let {
                            if (!mutable.containsKey("inet4_range")) mutable["inet4_range"] = it
                        }
                        fakeip["inet6_range"]?.let {
                            if (!mutable.containsKey("inet6_range")) mutable["inet6_range"] = it
                        }
                    }
                }
                mutable.remove("address")
            }
        }

        // 2. Migration des champs de résolution legacy.
        (mutable.remove("address_resolver") as? JsonPrimitive)?.content
            ?.takeIf { it.isNotBlank() }
            ?.let { resolver ->
                if (!mutable.containsKey("domain_resolver")) {
                    mutable["domain_resolver"] = JsonPrimitive(resolver)
                }
            }
        (mutable.remove("address_strategy") as? JsonPrimitive)?.content
            ?.takeIf { it.isNotBlank() }
            ?.let { strategy ->
                if (!mutable.containsKey("domain_strategy")) {
                    mutable["domain_strategy"] = JsonPrimitive(strategy)
                }
            }
        // `strategy` était un réglage par serveur. Dans le nouveau format il est
        // porté par dns.strategy (ou par une règle DNS) ; on conserve la première
        // valeur rencontrée pour ne pas laisser un champ devenu inconnu.
        (mutable.remove("strategy") as? JsonPrimitive)?.content
            ?.takeIf { it.isNotBlank() }
            ?.let { strategy ->
                if (!dns.containsKey("strategy")) {
                    dns["strategy"] = JsonPrimitive(strategy)
                }
            }

        // 3. Un detour vers direct/block/dns (ou vers un tag inexistant) est
        // invalide pour un serveur DNS. On le retire : sans detour, sing-box
        // utilise sa sortie directe vide par défaut, ce qui est autorisé.
        val type = (mutable["type"] as? JsonPrimitive)?.content
        val tag = (mutable["tag"] as? JsonPrimitive)?.content
        val detour = (mutable["detour"] as? JsonPrimitive)?.content
        if (detour != null) {
            val targetType = outboundTypes[detour]
            val invalidDetour =
                detour.isBlank() ||
                    targetType == null ||
                    targetType?.let { it in nonProxyOutboundTypes } == true ||
                    type?.let { it in nonDialingDnsTypes } == true ||
                    tag?.let { it in resolverTags } == true
            if (invalidDetour) {
                mutable.remove("detour")
            }
        }

        return JsonObject(mutable)
    }

    private fun parseDnsAddress(address: String): ParsedDnsAddress? {
        val value = address.trim()
        if (value.isEmpty()) return null
        when (value.lowercase()) {
            "local" -> return ParsedDnsAddress("local")
            "fakeip" -> return ParsedDnsAddress("fakeip")
        }
        // RCode n'a pas d'équivalent en serveur DNS 1.12+ (il devient une règle).
        if (value.startsWith("rcode://", ignoreCase = true)) return null

        val scheme = value.substringBefore("://", "").lowercase()
        if (scheme.isNotEmpty()) {
            val rest = value.substringAfter("://")
            return when (scheme) {
                "udp", "tcp" -> parseHostPort(rest, scheme, 53)
                "tls" -> parseHostPort(rest, "tls", 853)
                "quic" -> parseHostPort(rest, "quic", 853)
                "https" -> parseHttpAddress(rest, "https", 443)
                "h3" -> parseHttpAddress(rest, "h3", 443)
                "dhcp" -> {
                    val interfaceName = rest.substringBefore("/")
                        .takeIf { it.isNotBlank() && !it.equals("auto", ignoreCase = true) }
                    ParsedDnsAddress("dhcp", interfaceName = interfaceName)
                }
                else -> null
            }
        }
        // Une adresse sans schéma est un serveur UDP (IP ou nom de domaine).
        return parseHostPort(value, "udp", 53)
    }

    private fun parseHostPort(
        value: String,
        type: String,
        defaultPort: Int,
    ): ParsedDnsAddress? {
        val authority = value.substringBefore('/')
        if (authority.isBlank()) return null
        val (host, port) = splitHostPort(authority)
        if (host.isBlank()) return null
        return ParsedDnsAddress(type, host, port ?: defaultPort)
    }

    private fun parseHttpAddress(
        value: String,
        type: String,
        defaultPort: Int,
    ): ParsedDnsAddress? {
        val slash = value.indexOf('/')
        val authority = if (slash >= 0) value.substring(0, slash) else value
        val path = if (slash >= 0) value.substring(slash) else "/dns-query"
        if (authority.isBlank()) return null
        val (host, port) = splitHostPort(authority)
        if (host.isBlank()) return null
        return ParsedDnsAddress(type, host, port ?: defaultPort, path)
    }

    private fun splitHostPort(authority: String): Pair<String, Int?> {
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            if (end > 0) {
                val host = authority.substring(1, end)
                val port = authority.substring(end + 1).removePrefix(":").toIntOrNull()
                return host to port
            }
        }
        if (authority.count { it == ':' } == 1) {
            val host = authority.substringBeforeLast(':')
            val port = authority.substringAfterLast(':').toIntOrNull()
            if (host.isNotBlank() && port != null) return host to port
        }
        return authority to null
    }

    private fun isIpLiteral(value: String): Boolean {
        val host = value.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
        if (host.isEmpty()) return false
        if (host.contains(':')) {
            return host.all {
                it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.'
            }
        }
        val parts = host.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.all { char -> char.isDigit() } &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    // ── Contournement des réseaux privés ─────────────────────────────────────

    /** Tout le trafic LAN / lien-local reste hors du tunnel. */
    fun withBypassPrivateNetworks(content: String): String =
        runCatching {
            val root = parseRoot(content)
            val outbounds = outboundListOf(root)
            val directTag = ensureOutboundOfType(outbounds, "direct")
            root["outbounds"] = JsonArray(outbounds)

            val route = routeMap(root)
            prependRule(
                route,
                buildJsonObject {
                    put("ip_is_private", true)
                    put("outbound", directTag)
                },
            )
            root["route"] = JsonObject(route)
            encode(root)
        }.getOrDefault(content)

    // ── DNS personnalisé ─────────────────────────────────────────────────────

    /**
     * Remplace entièrement le DNS du profil : serveur distant (résolu via le proxy)
     * + bootstrap direct (résout le nom du serveur hors du tunnel).
     */
    fun withDnsOverride(
        content: String,
        override: DnsOverride,
    ): String =
        runCatching {
            // Un serveur vide ou un type inconnu rendrait la configuration invalide :
            // on conserve alors le DNS du profil au lieu de casser le tunnel.
            if (override.type !in setOf("https", "tls", "quic", "udp") ||
                override.remoteServer.isBlank() ||
                override.bootstrapServer.isBlank() ||
                // Le bootstrap est un serveur UDP sans resolver : il doit être
                // une adresse IP littérale, sinon il ne peut pas démarrer.
                !isIpLiteral(override.bootstrapServer)
            ) {
                return@runCatching content
            }
            val root = parseRoot(content)
            val outbounds = outboundListOf(root)
            val route = routeMap(root)
            val finalTag = proxyTagOf(root, outbounds)

            val remote = buildJsonObject {
                put("type", override.type)
                put("tag", "remote")
                val server =
                    if (override.type in setOf("https", "tls", "quic")) {
                        canonicalDnsHost(override.remoteServer)
                    } else {
                        override.remoteServer
                    }
                put("server", server)
                if (override.type == "https") put("path", "/dns-query")
                if (finalTag != null) put("detour", finalTag)
                // Si le serveur distant est un nom, il est résolu par le
                // bootstrap direct avant d'être joint via le proxy.
                if (!isIpLiteral(server)) {
                    put("domain_resolver", "bootstrap")
                }
            }
            val bootstrap = buildJsonObject {
                put("type", "udp")
                put("tag", "bootstrap")
                put("server", override.bootstrapServer)
                // Jamais de detour ici : un detour vers un direct vide fait
                // échouer sing-box 1.12+ au démarrage du transport DNS.
            }
            root["dns"] = buildJsonObject {
                put("servers", buildJsonArray {
                    add(remote)
                    add(bootstrap)
                })
                put("final", "remote")
                // Tunnel IPv4 : privilégier les réponses A pour éviter les lenteurs AAAA.
                put("strategy", "prefer_ipv4")
            }
            route["default_domain_resolver"] = buildJsonObject {
                put("server", "bootstrap")
            }
            root["route"] = JsonObject(route)
            encode(root)
        }.getOrDefault(content)

    // ── Réglages moteur ──────────────────────────────────────────────────────

    /** MTU et pile TUN (si le profil déclare un TUN), multiplexage sortant, niveau de journal. */
    fun withEngine(
        content: String,
        engine: EngineOptions,
    ): String =
        runCatching {
            val root = parseRoot(content)

            (root["tun"] as? JsonObject)?.let { tun ->
                val tunMutable = tun.toMutableMap()
                val mtu =
                    when (engine.mtuMode) {
                        "1280" -> 1280
                        "1500" -> 1500
                        "9000" -> 9000
                        "custom" -> engine.mtuCustom.takeIf { it in 576..65535 }
                        else -> null
                    }
                mtu?.let { tunMutable["mtu"] = JsonPrimitive(it) }
                if (engine.stackMode in setOf("gvisor", "mixed", "system")) {
                    tunMutable["stack"] = JsonPrimitive(engine.stackMode)
                }
                root["tun"] = JsonObject(tunMutable)
            }

            if (engine.logLevel in setOf("off", "error", "warn", "info", "debug", "trace")) {
                val log = (root["log"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                log["level"] = JsonPrimitive(engine.logLevel)
                root["log"] = JsonObject(log)
            }

            if (engine.muxEnabled) {
                val muxTypes = setOf("vmess", "vless", "trojan")
                val outbounds = (root["outbounds"] as? JsonArray)?.toMutableList() ?: mutableListOf()
                root["outbounds"] = JsonArray(
                    outbounds.map { element ->
                        if (outboundType(element) in muxTypes) {
                            val obj = (element as JsonObject).toMutableMap()
                            obj["multiplex"] = buildJsonObject { put("enabled", true) }
                            JsonObject(obj)
                        } else {
                            element
                        }
                    },
                )
            }

            encode(root)
        }.getOrDefault(content)

    // ── Options TLS et gaming ────────────────────────────────────────────────

    /**
     * Fragmentation TLS et SNI sur tous les sortants qui déclarent un objet TLS.
     *
     * `record_fragment` est la méthode recommandée par sing-box : elle découpe
     * le handshake en plusieurs enregistrements TLS. `disable_sni` est ajouté
     * quand l'utilisateur choisit de ne pas envoyer le SNI, et retiré sinon.
     * Les objets TLS explicitement désactivés (`enabled: false`) sont ignorés.
     */
    fun withTlsOptions(
        content: String,
        options: TlsOptions,
    ): String =
        runCatching {
            val root = parseRoot(content)
            val outbounds = outboundListOf(root)
            root["outbounds"] = JsonArray(
                outbounds.map { element ->
                    val obj = element as? JsonObject ?: return@map element
                    val tls = obj["tls"] as? JsonObject ?: return@map element
                    val enabled = (tls["enabled"] as? JsonPrimitive)?.content != "false"
                    if (!enabled) return@map element

                    val tlsMutable = tls.toMutableMap()
                    if (options.fragment) {
                        tlsMutable["record_fragment"] = JsonPrimitive(true)
                    }
                    if (options.sniEnabled) {
                        tlsMutable.remove("disable_sni")
                    } else {
                        tlsMutable["disable_sni"] = JsonPrimitive(true)
                    }

                    val objMutable = obj.toMutableMap()
                    objMutable["tls"] = JsonObject(tlsMutable)
                    JsonObject(objMutable)
                },
            )
            encode(root)
        }.getOrDefault(content)

    /**
     * Mode gaming : active TCP Fast Open et un keepalive court sur les sortants
     * qui dialent réellement (les groupes selector/urltest et les sortants
     * direct/block/dns sont laissés intacts).
     */
    fun withGamingMode(content: String): String =
        runCatching {
            val root = parseRoot(content)
            val outbounds = outboundListOf(root)
            val ignored = setOf("direct", "block", "dns", "selector", "urltest")
            root["outbounds"] = JsonArray(
                outbounds.map { element ->
                    val type = outboundType(element) ?: return@map element
                    if (type in ignored) return@map element
                    val obj = element as? JsonObject ?: return@map element
                    val mutable = obj.toMutableMap()
                    mutable["tcp_fast_open"] = JsonPrimitive(true)
                    mutable["tcp_keep_alive"] = JsonPrimitive("30s")
                    mutable["tcp_keep_alive_interval"] = JsonPrimitive("10s")
                    JsonObject(mutable)
                },
            )
            encode(root)
        }.getOrDefault(content)

    // ── Partage par point d'accès ────────────────────────────────────────────

    /**
     * Partage VPN : ajoute (ou remplace) un inbound mixte HTTP+SOCKS5 écoutant
     * sur toutes les interfaces, utilisé par les clients du point d'accès.
     */
    fun withHotspotProxy(
        content: String,
        port: Int,
    ): String =
        runCatching {
            if (port !in 1..65535) return@runCatching content
            val root = parseRoot(content)
            val inbounds = (root["inbounds"] as? JsonArray)?.toMutableList() ?: mutableListOf()
            val hotspot = buildJsonObject {
                put("type", "mixed")
                put("tag", "a2m-hotspot")
                put("listen", "0.0.0.0")
                put("listen_port", port)
            }
            val index = inbounds.indexOfFirst { outboundTag(it) == "a2m-hotspot" }
            if (index >= 0) {
                inbounds[index] = hotspot
            } else {
                inbounds.add(hotspot)
            }
            root["inbounds"] = JsonArray(inbounds)
            encode(root)
        }.getOrDefault(content)

    // ── Bloqueur de publicités ───────────────────────────────────────────────

    /**
     * Bloqueur de publicités : ajoute (si absents) un sortant « block » et une règle
     * de routage vers un rule-set distant de domaines publicitaires (geosite ads-all).
     */
    fun withAdBlock(content: String): String =
        runCatching {
            val root = parseRoot(content)
            val outbounds = outboundListOf(root)
            val blockTag = ensureOutboundOfType(outbounds, "block")
            val directTag = ensureOutboundOfType(outbounds, "direct")
            // Download the list through the selected proxy when available.
            // Direct downloads happen before the tunnel is ready and fail on
            // networks that block raw.githubusercontent.com.
            val downloadDetour = proxyTagOf(root, outbounds) ?: directTag
            root["outbounds"] = JsonArray(outbounds)

            val route = routeMap(root)
            prependRule(
                route,
                buildJsonObject {
                    put("rule_set", buildJsonArray { add(JsonPrimitive(ADS_RULE_SET_TAG)) })
                    put("outbound", blockTag)
                },
            )

            val ruleSets = (route["rule_set"] as? JsonArray)?.toMutableList() ?: mutableListOf()
            if (ruleSets.none { outboundTag(it) == ADS_RULE_SET_TAG }) {
                ruleSets.add(
                    buildJsonObject {
                        put("type", "remote")
                        put("tag", ADS_RULE_SET_TAG)
                        put("format", "binary")
                        put("url", ADS_RULE_SET_URL)
                        put("download_detour", downloadDetour)
                    },
                )
            }
            route["rule_set"] = JsonArray(ruleSets)
            root["route"] = JsonObject(route)
            encode(root)
        }.getOrDefault(content)
}
