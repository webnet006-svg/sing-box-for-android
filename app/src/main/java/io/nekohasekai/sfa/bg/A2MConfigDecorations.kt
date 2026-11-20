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
        var content = raw
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
            val root = parseRoot(content)
            val outbounds = outboundListOf(root)
            val directTag = ensureOutboundOfType(outbounds, "direct")
            root["outbounds"] = JsonArray(outbounds)

            val route = routeMap(root)
            val finalTag =
                (route["final"] as? JsonPrimitive)?.content
                    ?: outbounds
                        .firstOrNull {
                            val t = outboundType(it)
                            t != null && t !in setOf("direct", "block", "dns")
                        }?.let { outboundTag(it) }

            val remote = buildJsonObject {
                put("type", override.type)
                put("tag", "remote")
                put("server", override.remoteServer)
                if (override.type == "https") put("path", "/dns-query")
                if (finalTag != null) put("detour", finalTag)
            }
            val bootstrap = buildJsonObject {
                put("type", "udp")
                put("tag", "bootstrap")
                put("server", override.bootstrapServer)
                put("detour", directTag)
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
                        "custom" -> engine.mtuCustom.takeIf { it > 0 }
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
                        put("download_detour", directTag)
                    },
                )
            }
            route["rule_set"] = JsonArray(ruleSets)
            root["route"] = JsonObject(route)
            encode(root)
        }.getOrDefault(content)
}
