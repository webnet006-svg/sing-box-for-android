package io.nekohasekai.sfa.bg

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        assertEquals("proxy", ruleSet["download_detour"]!!.jsonPrimitive.content)
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
        assertThrows(RuntimeException::class.java) { A2MConfigDecorations.withAdBlock(broken) }
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
        assertEquals("cloudflare-dns.com", remote["server"]!!.jsonPrimitive.content)
        assertEquals("/dns-query", remote["path"]!!.jsonPrimitive.content)
        assertEquals("proxy", remote["detour"]!!.jsonPrimitive.content)
        assertEquals("bootstrap", root["outbounds"]!!.jsonArray[0].jsonObject["domain_resolver"]!!.jsonPrimitive.content)
        assertEquals("ipv4_only", root["outbounds"]!!.jsonArray[0].jsonObject["domain_strategy"]!!.jsonPrimitive.content)
        val bootstrap = servers[1].jsonObject
        assertEquals("udp", bootstrap["type"]!!.jsonPrimitive.content)
        assertEquals("9.9.9.9", bootstrap["server"]!!.jsonPrimitive.content)
        // Un detour vers le direct vide est refusé par sing-box 1.12+.
        assertFalse(bootstrap.containsKey("detour"))
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
    fun dnsOverrideKeepsProfileWhenServersBlankOrTypeUnknown() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}],"dns":{"servers":[]}}"""
        listOf(
            A2MConfigDecorations.DnsOverride(type = "https", remoteServer = "", bootstrapServer = "1.1.1.1"),
            A2MConfigDecorations.DnsOverride(type = "https", remoteServer = "1.1.1.1", bootstrapServer = " "),
            A2MConfigDecorations.DnsOverride(type = "doit", remoteServer = "1.1.1.1", bootstrapServer = "1.1.1.1"),
        ).forEach { override ->
            assertThrows(RuntimeException::class.java) { A2MConfigDecorations.withDnsOverride(config, override) }
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

    @Test
    fun dnsOverrideIgnoresRouteFinalPointingToDirect() {
        val config =
            """{"outbounds":[{"type":"direct","tag":"direct"},{"type":"vmess","tag":"proxy"}],""" +
                """"route":{"final":"direct","rules":[]}}"""
        val root = parse(
            A2MConfigDecorations.withDnsOverride(
                config,
                A2MConfigDecorations.DnsOverride(type = "https", remoteServer = "1.1.1.1", bootstrapServer = "9.9.9.9"),
            ),
        )
        val remote = root["dns"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject
        assertEquals("proxy", remote["detour"]!!.jsonPrimitive.content)
    }

    @Test
    fun dnsOverrideResolvesDomainRemoteThroughBootstrap() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}]}"""
        val root = parse(
            A2MConfigDecorations.withDnsOverride(
                config,
                A2MConfigDecorations.DnsOverride(type = "https", remoteServer = "dns.google", bootstrapServer = "9.9.9.9"),
            ),
        )
        val servers = root["dns"]!!.jsonObject["servers"]!!.jsonArray
        val remote = servers[0].jsonObject
        assertEquals("bootstrap", remote["domain_resolver"]!!.jsonPrimitive.content)
        assertFalse(servers[1].jsonObject.containsKey("detour"))
    }

    @Test
    fun dnsOverrideRejectsNonIpBootstrap() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}],"dns":{"servers":[]}}"""
        assertThrows(RuntimeException::class.java) {
            A2MConfigDecorations.withDnsOverride(
                config,
                A2MConfigDecorations.DnsOverride(type = "https", remoteServer = "1.1.1.1", bootstrapServer = "dns.google"),
            )
        }
    }

    // ── Normalisation DNS legacy ──────────────────────────────────────────────

    @Test
    fun normalizeDnsConvertsLegacyServersAndRemovesDirectDetour() {
        val config =
            """{"outbounds":[{"type":"vmess","tag":"proxy"},{"type":"direct","tag":"direct"}],""" +
                """"dns":{"servers":[""" +
                """{"tag":"remote","address":"https://dns.google/dns-query","address_resolver":"local","address_strategy":"ipv4_only","detour":"direct"},""" +
                """{"tag":"plain","address":"1.1.1.1"},""" +
                """{"tag":"sys","address":"local","detour":"direct"}]}}"""
        val root = parse(A2MConfigDecorations.normalizeDns(config))
        val servers = root["dns"]!!.jsonObject["servers"]!!.jsonArray

        val remote = servers[0].jsonObject
        assertEquals("https", remote["type"]!!.jsonPrimitive.content)
        assertEquals("dns.google", remote["server"]!!.jsonPrimitive.content)
        assertEquals("/dns-query", remote["path"]!!.jsonPrimitive.content)
        assertEquals("local", remote["domain_resolver"]!!.jsonPrimitive.content)
        assertEquals("ipv4_only", remote["domain_strategy"]!!.jsonPrimitive.content)
        assertFalse(remote.containsKey("address"))
        assertFalse(remote.containsKey("detour"))

        val plain = servers[1].jsonObject
        assertEquals("udp", plain["type"]!!.jsonPrimitive.content)
        assertEquals("1.1.1.1", plain["server"]!!.jsonPrimitive.content)
        assertEquals(53, plain["server_port"]!!.jsonPrimitive.content.toInt())
        assertFalse(plain.containsKey("detour"))

        val system = servers[2].jsonObject
        assertEquals("local", system["type"]!!.jsonPrimitive.content)
        assertFalse(system.containsKey("detour"))
    }

    @Test
    fun normalizeDnsKeepsProxyDetourAndMovesLegacyStrategy() {
        val config =
            """{"outbounds":[{"type":"vmess","tag":"proxy"}],"dns":{"servers":[""" +
                """{"type":"https","tag":"remote","server":"1.1.1.1","detour":"proxy"},""" +
                """{"address":"8.8.8.8","strategy":"ipv6_only"}]}}"""
        val root = parse(A2MConfigDecorations.normalizeDns(config))
        val dns = root["dns"]!!.jsonObject
        val servers = dns["servers"]!!.jsonArray

        assertEquals("proxy", servers[0].jsonObject["detour"]!!.jsonPrimitive.content)
        assertEquals("ipv6_only", dns["strategy"]!!.jsonPrimitive.content)
        assertFalse(servers[1].jsonObject.containsKey("strategy"))
    }

    @Test
    fun normalizeDnsUsesDeclaredDirectResolverForProxyEndpointOnlyWhenUnset() {
        val config =
            """{"outbounds":[{"type":"trojan","tag":"proxy","server":"proxy.example"},{"type":"vless","tag":"explicit","server":"other.example","domain_resolver":"custom"}],""" +
                """"dns":{"servers":[{"type":"udp","tag":"bootstrap","server":"1.1.1.1"}]}}"""
        val root = parse(A2MConfigDecorations.normalizeDns(config))
        val outbounds = root["outbounds"]!!.jsonArray
        assertEquals("bootstrap", outbounds[0].jsonObject["domain_resolver"]!!.jsonPrimitive.content)
        assertEquals("custom", outbounds[1].jsonObject["domain_resolver"]!!.jsonPrimitive.content)
    }

    @Test
    fun normalizeDnsDoesNotChooseResolverRoutedThroughProxy() {
        val config =
            """{"outbounds":[{"type":"trojan","tag":"proxy","server":"proxy.example"}],""" +
                """"dns":{"servers":[{"type":"https","tag":"remote","server":"dns.example","detour":"proxy"}]}}"""
        val root = parse(A2MConfigDecorations.normalizeDns(config))
        assertFalse(root["outbounds"]!!.jsonArray[0].jsonObject.containsKey("domain_resolver"))
    }

    @Test
    fun normalizeDnsRemovesProxyDetourFromResolverServer() {
        val config =
            """{"outbounds":[{"type":"vmess","tag":"proxy","server":"example.com","domain_resolver":"bootstrap"}],""" +
                """"dns":{"servers":[{"type":"udp","tag":"bootstrap","server":"9.9.9.9","detour":"proxy"}]}}"""
        val root = parse(A2MConfigDecorations.normalizeDns(config))
        val bootstrap = root["dns"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject
        assertFalse(bootstrap.containsKey("detour"))
    }

    @Test
    fun decorateNormalizesLegacyDnsEvenWithoutDnsOverride() {
        val config =
            """{"outbounds":[{"type":"vmess","tag":"proxy"},{"type":"direct","tag":"direct"}],""" +
                """"dns":{"servers":[{"tag":"remote","address":"tls://dns.google","detour":"direct"}]},"route":{"rules":[]}}"""
        val root = parse(
            A2MConfigDecorations.decorate(
                raw = config,
                bypassPrivate = false,
                dns = null,
                engine = null,
                adBlock = true,
                hotspotPort = null,
            ),
        )
        val remote = root["dns"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject
        assertEquals("tls", remote["type"]!!.jsonPrimitive.content)
        assertEquals("dns.google", remote["server"]!!.jsonPrimitive.content)
        assertFalse(remote.containsKey("detour"))
        val firstRule = root["route"]!!.jsonObject["rules"]!!.jsonArray[0].jsonObject
        assertEquals("geosite-category-ads-all", firstRule["rule_set"]!!.jsonArray[0].jsonPrimitive.content)
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

    @Test
    fun engineRejectsJumboMtu() {
        val config = """{"tun":{"mtu":1500}}"""
        val root = parse(
            A2MConfigDecorations.withEngine(
                config,
                A2MConfigDecorations.EngineOptions("custom", 9000, "auto", false, "auto"),
            ),
        )
        assertEquals(1500, root["tun"]!!.jsonObject["mtu"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun engineIgnoresOutOfRangeCustomMtu() {
        val config = """{"tun":{"mtu":1500}}"""
        val root = parse(
            A2MConfigDecorations.withEngine(
                config,
                A2MConfigDecorations.EngineOptions(
                    mtuMode = "custom",
                    mtuCustom = 0,
                    stackMode = "auto",
                    muxEnabled = false,
                    logLevel = "auto",
                ),
            ),
        )
        assertEquals(1500, root["tun"]!!.jsonObject["mtu"]!!.jsonPrimitive.content.toInt())
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
    fun hotspotIgnoresInvalidPort() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}]}"""
        assertThrows(RuntimeException::class.java) { A2MConfigDecorations.withHotspotProxy(config, 0) }
        assertThrows(RuntimeException::class.java) { A2MConfigDecorations.withHotspotProxy(config, 70000) }
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

    @Test
    fun decorateReportIdentifiesRequestedDecorationsThatFailed() {
        val broken = "not json"
        val result = A2MConfigDecorations.decorateWithReport(
            raw = broken,
            bypassPrivate = true,
            dns = A2MConfigDecorations.DnsOverride("https", "1.1.1.1", "1.1.1.1"),
            engine = null,
            adBlock = true,
            hotspotPort = null,
        )
        assertEquals(broken, result.content)
        assertTrue(result.failures.any { it.setting == "Normalisation DNS" })
        assertTrue(result.failures.any { it.setting == "DNS personnalisé" })
        assertTrue(result.failures.any { it.setting == "Ad blocker" })
        assertFalse("DNS personnalisé" in result.applied)
    }

    @Test
    fun decorateReportDoesNotClaimTlsWasAppliedWhenProfileHasNoTlsOutbound() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}]}"""
        val result = A2MConfigDecorations.decorateWithReport(
            raw = config,
            bypassPrivate = false,
            dns = null,
            engine = null,
            adBlock = false,
            hotspotPort = null,
            tls = A2MConfigDecorations.TlsOptions(fragment = true, sniEnabled = false),
        )
        assertTrue("TLS / SNI" in result.skipped)
        assertFalse("TLS / SNI" in result.applied)
    }
}
