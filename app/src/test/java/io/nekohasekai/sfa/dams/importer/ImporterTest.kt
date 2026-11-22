package io.nekohasekai.sfa.dams.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ImporterTest {
    private val vlessLink =
        "vless://11111111-1111-1111-1111-111111111111@example.com:443" +
            "?security=reality&pbk=PUBLIC_KEY&sid=abcd&sni=cdn.example.com&fp=chrome" +
            "&type=ws&path=%2Fws&host=cdn.example.com#My%20Node"

    @Test
    fun parsesVlessRealityWithWebSocket() {
        val profile = singleLink(Importer.parse(vlessLink)).profile
        assertEquals(Importer.Protocol.VLESS, profile.protocol)
        assertEquals("example.com", profile.host)
        assertEquals(443, profile.port)

        val outbound = profile.outbound
        assertEquals("vless", outbound.str("type"))
        assertEquals("11111111-1111-1111-1111-111111111111", outbound.str("uuid"))
        assertEquals("proxy", outbound.str("tag"))

        val tls = outbound.obj("tls")
        assertEquals("true", tls.str("enabled"))
        assertEquals("cdn.example.com", tls.str("server_name"))
        assertEquals("chrome", tls.obj("utls").str("fingerprint"))
        assertEquals("PUBLIC_KEY", tls.obj("reality").str("public_key"))
        assertEquals("abcd", tls.obj("reality").str("short_id"))

        val transport = outbound.obj("transport")
        assertEquals("ws", transport.str("type"))
        assertEquals("/ws", transport.str("path"))
        assertEquals("cdn.example.com", transport.obj("headers").str("Host"))
    }

    @Test
    fun parsesVlessRealityVariantsWithoutParameterOrderDependency() {
        val accepted = "vless://4db93c59-6639-4f2c-a4ba-43d218f6f041@Maxit-mobile.orange.bf.cdn.siakaouatta.site:442" +
            "?security=reality&sni=www.cloudflare.com&fp=chrome&pbk=VSqYc4O4Qwpg0QvoKgJ6zNHTYrOvb3TdXSFoMpiOTw8&sid=18c3#%5BVless%5D%20damstunnel-realit"
        val extended = "vless://4db93c59-6639-4f2c-a4ba-43d218f6f041@Maxit-mobile.orange.bf.cdn.siakaouatta.site:442" +
            "?security=reality&encryption=none&pbk=VSqYc4O4Qwpg0QvoKgJ6zNHTYrOvb3TdXSFoMpiOTw8&headerType=none" +
            "&fp=chrome&spx=%2Fc9206a914a564b4&type=tcp&sni=www.cloudflare.com&sid=18c3#damstunnel-realit"
        val first = singleLink(Importer.parse(accepted)).profile
        val second = singleLink(Importer.parse(extended)).profile
        assertEquals(first.protocol, second.protocol)
        assertEquals(first.host, second.host)
        assertEquals(first.port, second.port)
        assertEquals(first.outbound.str("uuid"), second.outbound.str("uuid"))
        assertEquals(first.outbound.obj("tls").obj("reality").str("public_key"), second.outbound.obj("tls").obj("reality").str("public_key"))
        assertEquals(first.outbound.obj("tls").obj("reality").str("short_id"), second.outbound.obj("tls").obj("reality").str("short_id"))
        assertEquals("chrome", second.outbound.obj("tls").obj("utls").str("fingerprint"))
        assertTrue(second.outbound["transport"] == null)
    }

    @Test
    fun parsesVmessJson() {
        val payload = """
            {"v":"2","ps":"VM Node","add":"vm.example.com","port":"8443","id":"uuid-1",
            "aid":"0","scy":"auto","net":"ws","type":"none","host":"vm.example.com",
            "path":"/vm","tls":"tls","sni":"vm.example.com"}
        """.trimIndent()
        val profile = singleLink(Importer.parse("vmess://${b64(payload)}")).profile

        assertEquals(Importer.Protocol.VMESS, profile.protocol)
        assertEquals("vm.example.com", profile.host)
        assertEquals(8443, profile.port)
        assertEquals("ws", profile.outbound.obj("transport").str("type"))
        assertEquals("true", profile.outbound.obj("tls").str("enabled"))
        assertEquals("VM Node", profile.displayName)
    }

    @Test
    fun parsesShadowsocksWithObfsPlugin() {
        val credentials = b64url("aes-256-gcm:secret")
        val link =
            "ss://$credentials@ss.example.com:8388" +
                "?plugin=obfs-local%3Bobfs%3Dhttp%3Bobfs-host%3Dbing.com#SS%20Node"
        val profile = singleLink(Importer.parse(link)).profile

        assertEquals(Importer.Protocol.SHADOWSOCKS, profile.protocol)
        assertEquals("ss.example.com", profile.host)
        assertEquals(8388, profile.port)
        assertEquals("aes-256-gcm", profile.outbound.str("method"))
        assertEquals("secret", profile.outbound.str("password"))
        assertEquals("obfs-local", profile.outbound.str("plugin"))
        assertEquals("obfs=http;obfs-host=bing.com", profile.outbound.str("plugin_opts"))
    }

    @Test
    fun parsesLegacyShadowsocksBase64() {
        val payload = b64("aes-128-gcm:legacy@legacy.example.com:8000")
        val profile = singleLink(Importer.parse("ss://$payload#Legacy")).profile

        assertEquals("aes-128-gcm", profile.outbound.str("method"))
        assertEquals("legacy.example.com", profile.host)
        assertEquals(8000, profile.port)
    }

    @Test
    fun parsesHysteria2WithObfuscation() {
        val link =
            "hy2://password@hy.example.com:443?sni=hy.example.com&insecure=1" +
                "&obfs=salamander&obfs-password=secret#HY"
        val profile = singleLink(Importer.parse(link)).profile

        assertEquals(Importer.Protocol.HYSTERIA2, profile.protocol)
        assertEquals("hysteria2", profile.outbound.str("type"))
        assertEquals("password", profile.outbound.str("password"))
        assertEquals("salamander", profile.outbound.obj("obfs").str("type"))
        assertEquals("secret", profile.outbound.obj("obfs").str("password"))
        assertEquals("true", profile.outbound.obj("tls").str("insecure"))
    }

    @Test
    fun parsesTuic() {
        val link =
            "tuic://uuid-2:password@tuic.example.com:443" +
                "?congestion_control=bbr&udp_relay_mode=native&sni=tuic.example.com#TU"
        val profile = singleLink(Importer.parse(link)).profile

        assertEquals(Importer.Protocol.TUIC, profile.protocol)
        assertEquals("uuid-2", profile.outbound.str("uuid"))
        assertEquals("password", profile.outbound.str("password"))
        assertEquals("bbr", profile.outbound.str("congestion_control"))
        assertEquals("native", profile.outbound.str("udp_relay_mode"))
    }

    @Test
    fun parsesTrojanWebSocket() {
        val link =
            "trojan://pass@tr.example.com:443?sni=tr.example.com&type=ws&path=%2Ft" +
                "&host=tr.example.com#TR"
        val profile = singleLink(Importer.parse(link)).profile

        assertEquals(Importer.Protocol.TROJAN, profile.protocol)
        assertEquals("pass", profile.outbound.str("password"))
        assertEquals("ws", profile.outbound.obj("transport").str("type"))
        assertEquals("/t", profile.outbound.obj("transport").str("path"))
    }

    @Test
    fun parsesBase64Subscription() {
        val trojan = "trojan://pass@tr.example.com:443#TR"
        val subscription = b64("$vlessLink\n$trojan")

        val entries = Importer.parse(subscription)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it is Importer.Entry.Link })
    }

    @Test
    fun parsesFullConfig() {
        val content = """
            {"outbounds":[
              {"type":"vless","tag":"proxy","server":"x.example.com","server_port":443,"uuid":"u"},
              {"type":"direct","tag":"direct"}
            ]}
        """.trimIndent()

        val entry = Importer.parse(content).single()
        assertTrue(entry is Importer.Entry.Config)
        entry as Importer.Entry.Config
        assertEquals("[Config] x.example.com", entry.name)
        assertNotNull(entry.dedupKey)
    }

    @Test
    fun rejectsUnsupportedTransport() {
        val entry = Importer.parse("vless://u@x.example.com:443?type=xhttp#bad").single()
        assertTrue(entry is Importer.Entry.Invalid)
    }

    @Test
    fun stripsQuotesAndBom() {
        val text = "\uFEFF\"$vlessLink\"\r\n"
        val profile = singleLink(Importer.parse(text)).profile
        assertEquals(Importer.Protocol.VLESS, profile.protocol)
    }

    @Test
    fun buildsValidLookingConfig() {
        val profile = singleLink(Importer.parse(vlessLink)).profile
        val root = Json.parseToJsonElement(Importer.buildConfig(profile)).jsonObject

        val inbound = root.array("inbounds")[0].jsonObject
        assertEquals("tun", inbound.str("type"))
        assertEquals("mixed", inbound.str("stack"))

        val outbounds = root.array("outbounds")
        assertEquals("proxy", outbounds[0].jsonObject.str("tag"))
        assertEquals("direct", outbounds[1].jsonObject.str("type"))

        val route = root.obj("route")
        assertEquals("proxy", route.str("final"))
        assertEquals("dns-local", route.str("default_domain_resolver"))
        assertEquals("sniff", route.array("rules")[0].jsonObject.str("action"))
        assertEquals("hijack-dns", route.array("rules")[1].jsonObject.str("action"))

        val dns = root.obj("dns")
        assertEquals("proxy", dns.array("servers")[1].jsonObject.str("detour"))
    }

    @Test
    fun dedupKeyMatchesAcrossFormats() {
        val linkProfile = singleLink(Importer.parse(vlessLink)).profile
        val config = Importer.buildConfig(linkProfile)
        assertEquals(linkProfile.dedupKey, Importer.dedupKeyOfConfig(config))
    }

    private fun singleLink(entries: List<Importer.Entry>): Importer.Entry.Link {
        assertEquals(1, entries.size)
        return entries.single() as Importer.Entry.Link
    }

    private fun b64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun b64url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.content.orEmpty()

    private fun JsonObject.obj(key: String): JsonObject = this[key] as JsonObject

    private fun JsonObject.array(key: String): JsonArray = this[key] as JsonArray
}
