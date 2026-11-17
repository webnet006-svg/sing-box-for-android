package io.nekohasekai.sfa.dams.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDraftsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun outboundOf(config: String): JsonObject =
        json.parseToJsonElement(config).jsonObject["outbounds"]!!
            .jsonArray
            .map { it.jsonObject }
            .first { it["server"] != null }

    @Test
    fun buildsVlessRealityWithVision() {
        val draft =
            ServerDraft(
                name = "Mon serveur",
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
        val profile = ServerDrafts.build(draft)
        assertEquals("Mon serveur", profile.displayName)
        assertEquals(Importer.Protocol.VLESS, profile.protocol)
        assertEquals("exemple.com", profile.host)
        assertEquals(443, profile.port)

        val outbound = outboundOf(ServerDrafts.buildConfig(draft))
        assertEquals("vless", outbound["type"]!!.jsonPrimitive.content)
        assertEquals("11111111-2222-3333-4444-555555555555", outbound["uuid"]!!.jsonPrimitive.content)
        assertEquals("xtls-rprx-vision", outbound["flow"]!!.jsonPrimitive.content)
        val tls = outbound["tls"]!!.jsonObject
        assertTrue(tls["enabled"]!!.jsonPrimitive.boolean)
        assertEquals("cdn.exemple.com", tls["server_name"]!!.jsonPrimitive.content)
        assertEquals("chrome", tls["utls"]!!.jsonObject["fingerprint"]!!.jsonPrimitive.content)
        val reality = tls["reality"]!!.jsonObject
        assertEquals("CLE_PUBLIQUE", reality["public_key"]!!.jsonPrimitive.content)
        assertEquals("abcd", reality["short_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun buildsTrojanAndVmess() {
        val trojan =
            ServerDrafts.build(
                ServerDraft(
                    protocol = Importer.Protocol.TROJAN,
                    host = "t.exemple.com",
                    port = 8443,
                    identity = "secret",
                    security = "tls",
                ),
            )
        assertEquals("secret", trojan.outbound["password"]!!.jsonPrimitive.content)
        assertEquals("t.exemple.com", trojan.outbound["server"]!!.jsonPrimitive.content)
        assertEquals(8443, trojan.outbound["server_port"]!!.jsonPrimitive.int)

        val vmess =
            ServerDrafts.build(
                ServerDraft(
                    protocol = Importer.Protocol.VMESS,
                    host = "v.exemple.com",
                    port = 443,
                    identity = "uuid-vmess",
                    security = "tls",
                    vmessSecurity = "aes-128-gcm",
                    alterId = 4,
                    transport = "ws",
                    path = "/ray",
                    hostHeader = "v.exemple.com",
                ),
            )
        assertEquals("aes-128-gcm", vmess.outbound["security"]!!.jsonPrimitive.content)
        assertEquals(4, vmess.outbound["alter_id"]!!.jsonPrimitive.int)
        val transport = vmess.outbound["transport"]!!.jsonObject
        assertEquals("ws", transport["type"]!!.jsonPrimitive.content)
        assertEquals("/ray", transport["path"]!!.jsonPrimitive.content)
        assertEquals(
            "v.exemple.com",
            transport["headers"]!!.jsonObject["Host"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun roundTripsThroughConfig() {
        val draft =
            ServerDraft(
                name = "Aller-retour",
                protocol = Importer.Protocol.VLESS,
                host = "rt.exemple.com",
                port = 2053,
                identity = "00000000-0000-0000-0000-000000000001",
                security = "tls",
                sni = "sni.exemple.com",
                alpn = "h2,http/1.1",
                fingerprint = "firefox",
                transport = "grpc",
                serviceName = "mon-service",
                allowInsecure = true,
            )
        val restored = ServerDrafts.fromConfig(ServerDrafts.buildConfig(draft))
        assertNotNull(restored)
        restored!!
        assertEquals(Importer.Protocol.VLESS, restored.protocol)
        assertEquals("rt.exemple.com", restored.host)
        assertEquals(2053, restored.port)
        assertEquals("00000000-0000-0000-0000-000000000001", restored.identity)
        assertEquals("tls", restored.security)
        assertEquals("sni.exemple.com", restored.sni)
        assertEquals("h2,http/1.1", restored.alpn)
        assertEquals("firefox", restored.fingerprint)
        assertEquals("grpc", restored.transport)
        assertEquals("mon-service", restored.serviceName)
        assertTrue(restored.allowInsecure)
    }

    @Test
    fun rejectsIncompleteDrafts() {
        val base = ServerDraft(host = "exemple.com", port = 443, identity = "uuid")
        assertThrows(IllegalArgumentException::class.java) {
            ServerDrafts.build(base.copy(host = " "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerDrafts.build(base.copy(port = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerDrafts.build(base.copy(identity = ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerDrafts.build(base.copy(security = "reality", realityPublicKey = ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerDrafts.build(base.copy(transport = "kcp"))
        }
    }

    @Test
    fun fromConfigIgnoresUnsupportedOutbounds() {
        assertNull(ServerDrafts.fromConfig("pas du json"))
        assertNull(ServerDrafts.fromConfig("""{"outbounds":[{"type":"direct","tag":"direct"}]}"""))
    }

    @Test
    fun examplesAreBuildable() {
        ServerDrafts.examples.forEach { (label, draft) ->
            val config = ServerDrafts.buildConfig(draft)
            assertNotNull(label, ServerDrafts.fromConfig(config))
        }
        assertFalse(ServerDrafts.examples.isEmpty())
    }

    @Test
    fun draftFromConfigFallsBackToRawTransport() {
        val config =
            """
            {"outbounds":[{"type":"vless","tag":"proxy","server":"a.exemple.com","server_port":443,
            "uuid":"u","transport":{"type":"raw"}}]}
            """.trimIndent()
        val draft = ServerDrafts.fromConfig(config)
        assertNotNull(draft)
        assertEquals("tcp", draft!!.transport)
    }

    @Test
    fun emptyOutboundsAreRejected() {
        assertNull(ServerDrafts.fromConfig("""{"outbounds":[]}"""))
        assertNull(ServerDrafts.fromConfig("""{"outbounds":[{"type":"vless","tag":"proxy"}]}"""))
    }
}
