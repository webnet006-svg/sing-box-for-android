package io.nekohasekai.sfa.dams.importer

import java.net.URLDecoder
import kotlin.io.encoding.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareExportTest {
    private val vlessDraft =
        ServerDraft(
            protocol = Importer.Protocol.VLESS,
            host = "exemple.com",
            port = 443,
            identity = "11111111-2222-3333-4444-555555555555",
            security = "reality",
            sni = "cdn.exemple.com",
            fingerprint = "chrome",
            realityPublicKey = "CLE_PUBLIQUE",
            realityShortId = "abcd",
            flow = "xtls-rprx-vision",
        )

    private fun linkOf(uri: String): Importer.ParsedProfile =
        Importer.parse(uri)
            .filterIsInstance<Importer.Entry.Link>()
            .firstOrNull()
            ?.profile
            ?: error("lien non reconnu par l'importeur : $uri")

    @Test
    fun exportsVlessRoundTrippable() {
        val exported = ShareExport.export(ServerDrafts.buildConfig(vlessDraft), "Mon serveur")
        assertNotNull(exported)
        exported!!
        assertEquals("Vless", exported.protocolLabel)
        assertTrue(exported.shareUri.startsWith("vless://"))
        assertTrue(exported.simpleLink.startsWith("a2mtun://server/"))
        assertTrue(exported.codedLink.startsWith("a2mtun://config/"))

        val parsed = linkOf(exported.shareUri)
        assertEquals("exemple.com", parsed.host)
        assertEquals(443, parsed.port)
        val outbound = parsed.outbound
        assertEquals("11111111-2222-3333-4444-555555555555", (outbound["uuid"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("xtls-rprx-vision", (outbound["flow"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun exportsVmessAndTrojanRoundTrippable() {
        val vmess =
            ServerDrafts.buildConfig(
                ServerDraft(
                    protocol = Importer.Protocol.VMESS,
                    host = "v.exemple.com",
                    port = 443,
                    identity = "22222222-3333-4444-5555-666666666666",
                    security = "tls",
                    transport = "ws",
                    path = "/ray",
                    hostHeader = "v.exemple.com",
                ),
            )
        val vmessParsed = linkOf(ShareExport.export(vmess, "VMess")!!.shareUri)
        assertEquals("v.exemple.com", vmessParsed.host)
        assertEquals(Importer.Protocol.VMESS, vmessParsed.protocol)

        val trojan =
            ServerDrafts.buildConfig(
                ServerDraft(
                    protocol = Importer.Protocol.TROJAN,
                    host = "t.exemple.com",
                    port = 8443,
                    identity = "mot de passe",
                    security = "tls",
                ),
            )
        val trojanParsed = linkOf(ShareExport.export(trojan, "Trojan")!!.shareUri)
        assertEquals("t.exemple.com", trojanParsed.host)
        assertEquals(8443, trojanParsed.port)
        assertEquals(Importer.Protocol.TROJAN, trojanParsed.protocol)
    }

    @Test
    fun codedLinkCarriesFullConfig() {
        val config = ServerDrafts.buildConfig(vlessDraft)
        val coded = ShareExport.codedLink(config)
        assertEquals(config, ShareExport.decodeCoded(coded))
        assertNull(ShareExport.decodeCoded("a2mtun://config/pas-du-base64-gzip"))
        assertNull(ShareExport.decodeCoded("a2mtun://config/"))
    }

    @Test
    fun simpleLinkEncodesShareUri() {
        val exported = ShareExport.export(ServerDrafts.buildConfig(vlessDraft), "Nom")!!
        val segment = exported.simpleLink.removePrefix("a2mtun://server/")
        assertEquals(exported.shareUri, URLDecoder.decode(segment, "UTF-8"))
    }

    @Test
    fun masksSecretsForDisplay() {
        val exported = ShareExport.export(ServerDrafts.buildConfig(vlessDraft), "Nom")!!
        val maskedShare = ShareExport.maskSensitive(exported.shareUri)
        assertFalse(maskedShare.contains("11111111-2222-3333-4444-555555555555"))
        assertTrue(maskedShare.contains("exemple.com"))
        assertEquals("a2mtun://config/••••••", ShareExport.maskSensitive(exported.codedLink))
    }

    @Test
    fun exportsShadowsocksSip002RoundTrippable() {
        val credentials = Base64.UrlSafe.encode("aes-256-gcm:secret".toByteArray())
        val link =
            "ss://$credentials@ss.example.com:8388" +
                "?plugin=obfs-local%3Bobfs%3Dhttp%3Bobfs-host%3Dbing.com#SS"
        val config = Importer.buildConfig(linkOf(link))
        val exported = ShareExport.export(config, "SS")!!
        assertEquals("SS", exported.protocolLabel)
        assertTrue(exported.shareUri.startsWith("ss://"))

        val parsed = linkOf(exported.shareUri)
        assertEquals(Importer.Protocol.SHADOWSOCKS, parsed.protocol)
        assertEquals("ss.example.com", parsed.host)
        assertEquals(8388, parsed.port)
        assertEquals(
            "aes-256-gcm",
            (parsed.outbound["method"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
        assertEquals(
            "secret",
            (parsed.outbound["password"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
        assertEquals(
            "obfs-local",
            (parsed.outbound["plugin"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
        assertEquals(
            "obfs=http;obfs-host=bing.com",
            (parsed.outbound["plugin_opts"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
    }

    @Test
    fun exportsHysteria2RoundTrippable() {
        val link =
            "hy2://password@hy.example.com:443?sni=hy.example.com&insecure=1" +
                "&obfs=salamander&obfs-password=secret#HY"
        val config = Importer.buildConfig(linkOf(link))
        val exported = ShareExport.export(config, "HY")!!
        assertEquals("Hysteria2", exported.protocolLabel)
        assertTrue(exported.shareUri.startsWith("hysteria2://"))

        val parsed = linkOf(exported.shareUri)
        assertEquals(Importer.Protocol.HYSTERIA2, parsed.protocol)
        assertEquals("hy.example.com", parsed.host)
        assertEquals(443, parsed.port)
        assertEquals(
            "password",
            (parsed.outbound["password"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
    }

    @Test
    fun exportsTuicRoundTrippable() {
        val link =
            "tuic://11111111-2222-3333-4444-555555555555:secret@tu.example.com:443" +
                "?congestion_control=bbr&udp_relay_mode=native&sni=tu.example.com&allow_insecure=1#TUIC"
        val config = Importer.buildConfig(linkOf(link))
        val exported = ShareExport.export(config, "TUIC")!!
        assertEquals("TUIC", exported.protocolLabel)
        assertTrue(exported.shareUri.startsWith("tuic://"))

        val parsed = linkOf(exported.shareUri)
        assertEquals(Importer.Protocol.TUIC, parsed.protocol)
        assertEquals("tu.example.com", parsed.host)
        assertEquals(443, parsed.port)
        assertEquals(
            "11111111-2222-3333-4444-555555555555",
            (parsed.outbound["uuid"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
        assertEquals(
            "secret",
            (parsed.outbound["password"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
    }

    @Test
    fun unsupportedConfigsReturnNull() {
        assertNull(ShareExport.export("pas du json", "x"))
        assertNull(ShareExport.export("""{"outbounds":[{"type":"direct","tag":"direct"}]}""", "x"))
    }
}
