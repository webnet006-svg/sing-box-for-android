package io.nekohasekai.sfa.bg

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests des décorations de configuration A2M. La transformation doit produire
 * une configuration sing-box valide (format DNS 1.12+) sans écraser l'existant
 * ni inventer d'options. Chaque décorateur reçoit des options explicites.
 */
class A2MConfigDecorationsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(content: String) = json.parseToJsonElement(content).jsonObject

    // ── Bloqueur de publicités ───────────────────────────────────────────────

    @Test
    fun adBlockAddsBlockAndDirectOutboundsAndRule() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy","server":"a.com"}],"route":{"rules":[]}}"""
        val decorated = A2MConfigDecorations.withAdBlock(config)
        val root = parse(decorated)

        val outbounds = root["outbounds"]!!.jsonArray
        assertTrue(outbounds.any { it.jsonObject["type"]?.jsonPrimitive?.content == "block" })
        assertTrue(outbounds.any { it.jsonObject["type"]?.jsonPrimitive?.content == "direct" })

        val adRule = root["route"]!!.jsonObject["rules"]!!.jsonArray.first().jsonObject
        assertEquals("geosite-category-ads-all", adRule["rule_set"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("block", adRule["outbound"]!!.jsonPrimitive.content)

        val ruleSet = root["route"]!!.jsonObject["rule_set"]!!.jsonArray[0].jsonObject
        assertEquals("remote", ruleSet["type"]!!.jsonPrimitive.content)
        assertEquals("direct", ruleSet["download_detour"]!!.jsonPrimitive.content)
        assertTrue(ruleSet["url"]!!.jsonPrimitive.content.contains("geosite-category-ads-all"))
    }

    @Test
    fun adBlockReusesExistingBlockOutboundAndPrependsRule() {
        val config =
            """{"outbounds":[{"type":"vmess","tag":"proxy"},{"type":"block","tag":"stop"}],""" +
                """"route":{"rules":[{"inbound":["dns"],"action":"sniff"}]}}"""
        val root = parse(A2MConfigDecorations.withAdBlock(config))
        val outbounds = root["outbounds"]!!.jsonArray
        // proxy + block existants conservés, direct ajouté pour le download_detour.
        assertEquals(3, outbounds.size)
        assertTrue(outbounds.any { it.jsonObject["type"]?.jsonPrimitive?.content == "direct" })
        val adRule = root["route"]!!.jsonObject["rules"]!!.jsonArray.first().jsonObject
        assertEquals("stop", adRule["outbound"]!!.jsonPrimitive.content)
    }

    @Test
    fun adBlockDoesNotDuplicateRuleSetOnRepeatedApplication() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}]}"""
        val once = A2MConfigDecorations.withAdBlock(config)
        val twice = A2MConfigDecorations.withAdBlock(once)
        assertEquals(1, parse(twice)["route"]!!.jsonObject["rule_set"]!!.jsonArray.size)
    }

    @Test
    fun adBlockKeepsOriginalContentWhenUnparseable() {
        val broken = "ceci n'est pas du json"
        assertEquals(broken, A2MConfigDecorations.withAdBlock(broken))
    }

    // ── Contournement des réseaux privés ─────────────────────────────────────

    @Test
    fun bypassPrivatePrependsLanRuleAndAddsDirect() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}]}"""
        val root = parse(A2MConfigDecorations.withBypassPrivateNetworks(config))
        val lanRule = root["route"]!!.jsonObject["rules"]!!.jsonArray.first().jsonObject
        assertEquals(true, lanRule["ip_is_private"]!!.jsonPrimitive.boolean)
        assertEquals("direct", lanRule["outbound"]!!.jsonPrimitive.content)
        assertTrue(root["outbounds"]!!.jsonArray.any { it.jsonObject["type"]?.jsonPrimitive?.content == "direct" })
    }

    // ── DNS personnalisé ─────────────────────────────────────────────────────

    @Test
    fun dnsOverrideUsesNewFormatAndRoutesServerNameThroughBootstrap() {
        val config =
            """{"outbounds":[{"type":"vmess","tag":"proxy"}],""" +
                """"dns":{"servers":[{"type":"udp","tag":"old"}],"final":"old"},"route":{"rules":[]}}"""
        val root = parse(
            A2MConfigDecorations.withDnsOverride(
                config,
                A2MConfigDecorations.DnsOverride(type = "https", remoteServer = "1.1.1.1", bootstrapServer = "9.9.9.9"),
            ),
        )

        val dns = root["dns"]!!.jsonObject
        val servers = dns["servers"]!!.jsonArray
        assertEquals(2, servers.size)
        val remote = servers[0].jsonObject
        assertEquals("https", remote["type"]!!.jsonPrimitive.content)
        assertEquals("1.1.1.1", remote["server"]!!.jsonPrimitive.content)
        assertEquals("/dns-query", remote["path"]!!.jsonPrimitive.content)
        assertEquals("proxy", remote["detour"]!!.jsonPrimitive.content)
        val bootstrap = servers[1].jsonObject
        assertEquals("udp", bootstrap["type"]!!.jsonPrimitive.content)
        assertEquals("9.9.9.9", bootstrap["server"]!!.jsonPrimitive.content)
        assertEquals("direct", bootstrap["detour"]!!.jsonPrimitive.content)
        assertEquals("remote", dns["final"]!!.jsonPrimitive.content)
        assertEquals("prefer_ipv4", dns["strategy"]!!.jsonPrimitive.content)
        assertEquals(
            "bootstrap",
            root["route"]!!.jsonObject["default_domain_resolver"]!!.jsonObject["server"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun dnsOverrideDoTAndUdpTypesHaveNoPath() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}]}"""
        listOf("tls", "quic", "udp").forEach { type ->
            val root = parse(
                A2MConfigDecorations.withDnsOverride(
                    config,
                    A2MConfigDecorations.DnsOverride(type = type, remoteServer = "8.8.8.8", bootstrapServer = "1.1.1.1"),
                ),
            )
            val remote = root["dns"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject
            assertEquals(type, remote["type"]!!.jsonPrimitive.content)
            assertFalse(remote.containsKey("path"))
        }
    }

    @Test
    fun dnsOverrideWithoutRouteFinalUsesFirstProxyOutboundAsDetour() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}]}"""
        val root = parse(
            A2MConfigDecorations.withDnsOverride(
                config,
                A2MConfigDecorations.DnsOverride(type = "udp", remoteServer = "1.1.1.1", bootstrapServer = "1.1.1.1"),
            ),
        )
        val remote = root["dns"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject
        // Sans route.final explicite, la résolution passe par le premier sortant proxy du profil.
        assertEquals("proxy", remote["detour"]!!.jsonPrimitive.content)
    }

    // ── Réglages moteur ──────────────────────────────────────────────────────

    @Test
    fun engineAppliesMtuStackLogAndMux() {
        val config =
            """{"outbounds":[{"type":"vmess","tag":"proxy"},{"type":"direct","tag":"direct"}],""" +
                """"tun":{"mtu":1500,"stack":"system"},"log":{"level":"info"},"route":{"rules":[]}}"""
        val root = parse(
            A2MConfigDecorations.withEngine(
                config,
                A2MConfigDecorations.EngineOptions(
                    mtuMode = "1280",
                    mtuCustom = 1500,
                    stackMode = "gvisor",
                    muxEnabled = true,
                    logLevel = "debug",
                ),
            ),
        )
        val tun = root["tun"]!!.jsonObject
        assertEquals(1280, tun["mtu"]!!.jsonPrimitive.content.toInt())
        assertEquals("gvisor", tun["stack"]!!.jsonPrimitive.content)
        assertEquals("debug", root["log"]!!.jsonObject["level"]!!.jsonPrimitive.content)
        val vmess = root["outbounds"]!!.jsonArray.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy" }.jsonObject
        assertEquals(true, vmess["multiplex"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
        val direct = root["outbounds"]!!.jsonArray.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "direct" }.jsonObject
        assertFalse(direct.containsKey("multiplex"))
    }

    @Test
    fun engineCustomMtuAndAutoModesIgnored() {
        val config = """{"tun":{"mtu":9000,"stack":"gvisor"}}"""
        val root = parse(
            A2MConfigDecorations.withEngine(
                config,
                A2MConfigDecorations.EngineOptions(
                    mtuMode = "custom",
                    mtuCustom = 1400,
                    stackMode = "auto",
                    muxEnabled = false,
                    logLevel = "auto",
                ),
            ),
        )
        assertEquals(1400, root["tun"]!!.jsonObject["mtu"]!!.jsonPrimitive.content.toInt())
        assertEquals("gvisor", root["tun"]!!.jsonObject["stack"]!!.jsonPrimitive.content)
        assertFalse(root.containsKey("log"))
    }

    // ── TLS / SNI ─────────────────────────────────────────────────────────────

    @Test
    fun tlsOptionsFragmentAndDisableSniOnTlsOutbounds() {
        val config =
            """{"outbounds":[""" +
                """{"type":"vless","tag":"proxy","tls":{"enabled":true,"server_name":"example.com"}},""" +
                """{"type":"direct","tag":"direct"}]}"""
        val root = parse(
            A2MConfigDecorations.withTlsOptions(
                config,
                A2MConfigDecorations.TlsOptions(fragment = true, sniEnabled = false),
            ),
        )
        val proxy = root["outbounds"]!!.jsonArray.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy" }.jsonObject
        val tls = proxy["tls"]!!.jsonObject
        assertEquals(true, tls["record_fragment"]!!.jsonPrimitive.boolean)
        assertEquals(true, tls["disable_sni"]!!.jsonPrimitive.boolean)

        val direct = root["outbounds"]!!.jsonArray.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "direct" }.jsonObject
        assertFalse(direct.containsKey("tls"))
    }

    @Test
    fun tlsOptionsReenableSniAndIgnoreDisabledTls() {
        val config =
            """{"outbounds":[""" +
                """{"type":"vless","tag":"proxy","tls":{"enabled":true,"disable_sni":true}},""" +
                """{"type":"trojan","tag":"off","tls":{"enabled":false,"disable_sni":true}}]}"""
        val root = parse(
            A2MConfigDecorations.withTlsOptions(
                config,
                A2MConfigDecorations.TlsOptions(fragment = false, sniEnabled = true),
            ),
        )
        val proxyTls = root["outbounds"]!!.jsonArray.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy" }.jsonObject["tls"]!!.jsonObject
        assertFalse(proxyTls.containsKey("disable_sni"))
        assertFalse(proxyTls.containsKey("record_fragment"))

        val offTls = root["outbounds"]!!.jsonArray.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "off" }.jsonObject["tls"]!!.jsonObject
        assertEquals(true, offTls["disable_sni"]!!.jsonPrimitive.boolean)
    }

    // ── Mode gaming ───────────────────────────────────────────────────────────

    @Test
    fun gamingModeAddsDialOptionsOnlyToRealOutbounds() {
        val config =
            """{"outbounds":[""" +
                """{"type":"vmess","tag":"proxy","server":"a.com"},""" +
                """{"type":"selector","tag":"select","outbounds":["proxy"]},""" +
                """{"type":"direct","tag":"direct"}]}"""
        val root = parse(A2MConfigDecorations.withGamingMode(config))
        val proxy = root["outbounds"]!!.jsonArray.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy" }.jsonObject
        assertEquals(true, proxy["tcp_fast_open"]!!.jsonPrimitive.boolean)
        assertEquals("30s", proxy["tcp_keep_alive"]!!.jsonPrimitive.content)
        assertEquals("10s", proxy["tcp_keep_alive_interval"]!!.jsonPrimitive.content)

        val selector = root["outbounds"]!!.jsonArray.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "select" }.jsonObject
        assertFalse(selector.containsKey("tcp_fast_open"))
        val direct = root["outbounds"]!!.jsonArray.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "direct" }.jsonObject
        assertFalse(direct.containsKey("tcp_fast_open"))
    }

    // ── Partage par point d'accès ─────────────────────────────────────────────

    @Test
    fun hotspotAddsMixedInboundOnLan() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}]}"""
        val root = parse(A2MConfigDecorations.withHotspotProxy(config, 2080))
        val inbound = root["inbounds"]!!.jsonArray.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "a2m-hotspot" }.jsonObject
        assertEquals("mixed", inbound["type"]!!.jsonPrimitive.content)
        assertEquals("0.0.0.0", inbound["listen"]!!.jsonPrimitive.content)
        assertEquals(2080, inbound["listen_port"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun hotspotReplacesExistingEntryInsteadOfDuplicating() {
        val config = """{"inbounds":[{"type":"tun","tag":"tun-in"}]}"""
        val once = A2MConfigDecorations.withHotspotProxy(config, 2080)
        val twice = A2MConfigDecorations.withHotspotProxy(once, 7893)
        val inbounds = parse(twice)["inbounds"]!!.jsonArray
        assertEquals(2, inbounds.size)
        assertEquals(
            7893,
            inbounds.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "a2m-hotspot" }.jsonObject["listen_port"]!!.jsonPrimitive.content.toInt(),
        )
    }

    // ── Chaîne complète ──────────────────────────────────────────────────────

    @Test
    fun decorateChainsAllStepsAndFailsIndependently() {
        val config =
            """{"outbounds":[{"type":"vmess","tag":"proxy","tls":{"enabled":true}}],""" +
                """"tun":{"mtu":1500},"route":{"rules":[]}}"""
        val decorated = A2MConfigDecorations.decorate(
            raw = config,
            bypassPrivate = true,
            dns = A2MConfigDecorations.DnsOverride(type = "tls", remoteServer = "1.1.1.1", bootstrapServer = "1.1.1.1"),
            engine = A2MConfigDecorations.EngineOptions(
                mtuMode = "1500",
                mtuCustom = 1500,
                stackMode = "mixed",
                muxEnabled = false,
                logLevel = "warn",
            ),
            adBlock = true,
            hotspotPort = null,
            tls = A2MConfigDecorations.TlsOptions(fragment = true, sniEnabled = false),
            gaming = true,
        )
        val root = parse(decorated)
        val rules = root["route"]!!.jsonObject["rules"]!!.jsonArray
        // La règle pub est en tête (dernière décorations préposée), LAN juste après.
        assertEquals("geosite-category-ads-all", rules[0].jsonObject["rule_set"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals(true, rules[1].jsonObject["ip_is_private"]!!.jsonPrimitive.boolean)
        assertEquals("tls", root["dns"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("mixed", root["tun"]!!.jsonObject["stack"]!!.jsonPrimitive.content)
        assertEquals("warn", root["log"]!!.jsonObject["level"]!!.jsonPrimitive.content)
        val proxy = root["outbounds"]!!.jsonArray.first { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy" }.jsonObject
        assertEquals(true, proxy["tls"]!!.jsonObject["record_fragment"]!!.jsonPrimitive.boolean)
        assertEquals(true, proxy["tls"]!!.jsonObject["disable_sni"]!!.jsonPrimitive.boolean)
        assertEquals(true, proxy["tcp_fast_open"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun decorateWithoutOptionsReturnsOriginal() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}]}"""
        assertEquals(
            config,
            A2MConfigDecorations.decorate(
                raw = config,
                bypassPrivate = false,
                dns = null,
                engine = null,
                adBlock = false,
                hotspotPort = null,
            ),
        )
    }

    @Test
    fun decorateKeepsOriginalWhenUnparseable() {
        val broken = "not json at all"
        assertEquals(
            broken,
            A2MConfigDecorations.decorate(
                raw = broken,
                bypassPrivate = true,
                dns = A2MConfigDecorations.DnsOverride("https", "1.1.1.1", "1.1.1.1"),
                engine = A2MConfigDecorations.EngineOptions("1500", 1500, "gvisor", true, "info"),
                adBlock = true,
                hotspotPort = null,
            ),
        )
    }
}
