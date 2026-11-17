package io.nekohasekai.sfa.compose.screen.am

import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests unitaires de la lecture d'informations A2M TUN depuis une configuration réelle.
 */
class A2MProfileInfoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun profileWithConfig(configJson: String): Profile {
        val file = File.createTempFile("a2m-test-config", ".json")
        file.writeText(configJson)
        val typed = TypedProfile()
        typed.type = TypedProfile.Type.Local
        typed.path = file.absolutePath
        return Profile(id = 1L, name = "Test", typed = typed)
    }

    private fun config(
        outboundType: String,
        dnsServers: String? = null,
    ): String =
        buildString {
            append("{")
            append("\"outbounds\":[{\"type\":\"")
            append(outboundType)
            append("\",\"tag\":\"proxy\",\"server\":\"example.com\"}]")
            if (dnsServers != null) {
                append(",\"dns\":{\"servers\":")
                append(dnsServers)
                append("}")
            }
            append("}")
        }

    @Test
    fun readsVlessProtocol() {
        val profile = profileWithConfig(config("vless"))
        assertEquals("VLESS", A2MProfileInfo.protocolLabel(profile))
    }

    @Test
    fun readsHysteria2Protocol() {
        val profile = profileWithConfig(config("hysteria2"))
        assertEquals("Hysteria2", A2MProfileInfo.protocolLabel(profile))
    }

    @Test
    fun ignoresSelectorAndDirectOutbounds() {
        val jsonText =
            """
            {"outbounds":[
                {"type":"direct","tag":"direct"},
                {"type":"selector","tag":"auto"},
                {"type":"vmess","tag":"proxy"}
            ]}
            """.trimIndent()
        val profile = profileWithConfig(jsonText)
        assertEquals("VMess", A2MProfileInfo.protocolLabel(profile))
    }

    @Test
    fun unknownProtocolIsUppercased() {
        val profile = profileWithConfig(config("somefutureproto"))
        assertEquals("SOMEFUTUREPROTO", A2MProfileInfo.protocolLabel(profile))
    }

    @Test
    fun missingOutboundFallsBackToTypeLabel() {
        val profile = profileWithConfig("{\"dns\":{\"servers\":[]}}")
        assertEquals("Configuration", A2MProfileInfo.protocolLabel(profile))
    }

    @Test
    fun typeLabelLocalIsConfiguration() {
        val profile = profileWithConfig(config("vless"))
        assertEquals("Configuration", A2MProfileInfo.typeLabel(profile))
    }

    @Test
    fun readsStringDnsServers() {
        val profile =
            profileWithConfig(
                config(
                    "vless",
                    dnsServers = """["https://1.1.1.1/dns-query","8.8.8.8"]""",
                ),
            )
        assertEquals(
            listOf("https://1.1.1.1/dns-query", "8.8.8.8"),
            A2MProfileInfo.dnsServers(profile),
        )
    }

    @Test
    fun readsObjectDnsServers() {
        val profile =
            profileWithConfig(
                config(
                    "vless",
                    dnsServers = """[{"tag":"dns-cf","server":"1.1.1.1"},{"server":"9.9.9.9"}]""",
                ),
            )
        assertEquals(listOf("1.1.1.1", "9.9.9.9"), A2MProfileInfo.dnsServers(profile))
    }

    @Test
    fun missingDnsSectionReturnsEmptyList() {
        val profile = profileWithConfig(config("vless"))
        assertTrue(A2MProfileInfo.dnsServers(profile).isEmpty())
    }

    @Test
    fun unreadablePathFallsBackGracefully() {
        val typed = TypedProfile()
        typed.type = TypedProfile.Type.Local
        typed.path = "/nonexistent/a2m/does-not-exist.json"
        val profile = Profile(id = 2L, name = "Broken", typed = typed)
        assertEquals("Configuration", A2MProfileInfo.protocolLabel(profile))
        assertTrue(A2MProfileInfo.dnsServers(profile).isEmpty())
    }

    @Test
    fun parsesRealisticSingBoxConfig() {
        val configJson =
            """
            {
              "log": {"level": "info"},
              "dns": {"servers": ["https://dns.google/dns-query"]},
              "outbounds": [
                {"type": "selector", "tag": "auto"},
                {"type": "trojan", "tag": "proxy", "server": "trojan.example.com", "port": 443}
              ]
            }
            """.trimIndent()
        val root = json.parseToJsonElement(configJson).jsonObject
        val outbounds = root["outbounds"] as List<*>
        assertEquals(2, outbounds.size)
        val dns = (root["dns"] as JsonObject)["servers"]!!
        assertTrue(dns.toString().contains("dns.google"))
        val profile = profileWithConfig(configJson)
        assertEquals("Trojan", A2MProfileInfo.protocolLabel(profile))
        assertEquals(listOf("https://dns.google/dns-query"), A2MProfileInfo.dnsServers(profile))
    }

    @Test
    fun readsShortProtocolCodes() {
        assertEquals("VL", A2MProfileInfo.protocolCode(profileWithConfig(config("vless"))))
        assertEquals("VM", A2MProfileInfo.protocolCode(profileWithConfig(config("vmess"))))
        assertEquals("TR", A2MProfileInfo.protocolCode(profileWithConfig(config("trojan"))))
        assertEquals("H2", A2MProfileInfo.protocolCode(profileWithConfig(config("hysteria2"))))
    }

    @Test
    fun readsServerAddressAndHandlesBrokenFile() {
        val profile = profileWithConfig(config("vless"))
        assertEquals("example.com", A2MProfileInfo.serverAddress(profile))
        val broken = Profile(id = 3L, name = "Broken", typed = missingFileTyped())
        assertTrue(A2MProfileInfo.serverAddress(broken) == null)
        assertEquals("", A2MProfileInfo.protocolCode(broken))
    }

    private fun missingFileTyped(): TypedProfile {
        val typed = TypedProfile()
        typed.type = TypedProfile.Type.Local
        typed.path = "/nonexistent/a2m/does-not-exist.json"
        return typed
    }
}
