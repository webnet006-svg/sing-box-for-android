package io.nekohasekai.sfa.dams.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2MExportFileTest {
    private val entries =
        listOf(
            A2MExportFile.Entry(
                "Serveur A",
                """{"outbounds":[{"type":"direct","tag":"direct"}]}""",
            ),
            A2MExportFile.Entry(
                "Serveur B",
                """{"outbounds":[{"type":"block","tag":"block"}]}""",
            ),
        )

    private fun decoded(text: String): A2MExportFile.Decoded.Success =
        A2MExportFile.decode(text) as? A2MExportFile.Decoded.Success
            ?: error("décodage .a2m attendu : ${(A2MExportFile.decode(text) as? A2MExportFile.Decoded.Error)?.message}")

    @Test
    fun simpleFileIsReadableAndRoundTrips() {
        val text = A2MExportFile.encodeSimple(entries)
        assertTrue(A2MExportFile.isA2M(text))
        assertTrue(text.contains("\"format\": \"a2m\""))
        assertTrue(text.contains("\"Serveur A\""))

        val result = decoded(text)
        assertEquals(listOf("Serveur A", "Serveur B"), result.entries.map { it.name })
        assertEquals(entries.map { it.config }, result.entries.map { it.config })
        assertEquals("json", result.encoding)
    }

    @Test
    fun codedFileIsGzipBase64AndRoundTrips() {
        val text = A2MExportFile.encodeCoded(entries)
        assertTrue(A2MExportFile.isA2M(text))
        assertTrue(text.contains("\"encoding\": \"gzip+base64url\""))
        assertFalse(text.contains("Serveur A"))

        val result = decoded(text)
        assertEquals(entries.map { it.name }, result.entries.map { it.name })
        assertEquals(entries.map { it.config }, result.entries.map { it.config })
        assertEquals("gzip+base64url", result.encoding)
    }

    @Test
    fun blankNameGetsStableDefaultBeforeChecksum() {
        val text = A2MExportFile.encodeSimple(listOf(A2MExportFile.Entry("", "{}")))
        val result = decoded(text)
        assertEquals("Serveur", result.entries.single().name)
    }

    @Test
    fun tamperedSimpleFileIsRejected() {
        val text = A2MExportFile.encodeSimple(entries).replace("Serveur A", "Serveur X")
        val error = A2MExportFile.decode(text) as? A2MExportFile.Decoded.Error
        assertTrue(error?.message.orEmpty().contains("Intégrité"))
    }

    @Test
    fun unknownVersionIsRejected() {
        val text =
            A2MExportFile.encodeSimple(entries)
                .replace(Regex("\"version\"\\s*:\\s*1"), "\"version\": 99")
        val error = A2MExportFile.decode(text) as? A2MExportFile.Decoded.Error
        assertTrue(error?.message.orEmpty().contains("Version"))
    }

    @Test
    fun importerReadsA2MEntriesAsConfigs() {
        val text = A2MExportFile.encodeSimple(entries)
        val configs = Importer.parse(text).filterIsInstance<Importer.Entry.Config>()
        assertEquals(2, configs.size)
        assertEquals("Serveur A", configs[0].name)
        assertEquals(entries[0].config, configs[0].content)
    }

    @Test
    fun normalConfigIsNotMistakenForA2M() {
        assertFalse(A2MExportFile.isA2M("""{"outbounds":[{"type":"direct"}]}"""))
    }
}
