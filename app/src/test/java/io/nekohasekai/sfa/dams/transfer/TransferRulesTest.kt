package io.nekohasekai.sfa.dams.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferRulesTest {
    @Test
    fun normalizeUrlAcceptsHttpAndHttps() {
        assertEquals("https://exemple.com/fichier.zip", TransferRules.normalizeUrl("  https://exemple.com/fichier.zip  "))
        assertEquals("http://10.0.0.1:8080/up", TransferRules.normalizeUrl("http://10.0.0.1:8080/up"))
        assertNull(TransferRules.normalizeUrl(""))
        assertNull(TransferRules.normalizeUrl("ftp://exemple.com/a"))
        assertNull(TransferRules.normalizeUrl("exemple.com/a"))
        assertNull(TransferRules.normalizeUrl("https://"))
        assertNull(TransferRules.normalizeUrl("https://user:pass@exemple.com/a"))
        assertNull(TransferRules.normalizeUrl("https://exemple.com/a b"))
        assertNull(TransferRules.normalizeUrl("https://exemple.com/" + "a".repeat(TransferRules.MAX_URL_LENGTH)))
    }

    @Test
    fun cleartextDetected() {
        assertTrue(TransferRules.isCleartext("http://exemple.com"))
        assertFalse(TransferRules.isCleartext("https://exemple.com"))
    }

    @Test
    fun fileNameFromUrlStripsQueryAndSanitizes() {
        assertEquals("fichier.zip", TransferRules.fileNameFromUrl("https://exemple.com/dossier/fichier.zip?x=1"))
        assertEquals("a_b", TransferRules.fileNameFromUrl("https://exemple.com/dossier/a%5Cb"))
        assertEquals("fichier", TransferRules.fileNameFromUrl("https://exemple.com/"))
        assertEquals("fichier", TransferRules.fileNameFromUrl("https://exemple.com"))
        assertEquals("a_b_c", TransferRules.sanitizeFileName("a/b\\c"))
        assertEquals("_x", TransferRules.sanitizeFileName("\u0001x"))
    }

    @Test
    fun progressIsReadable() {
        assertEquals("512 o", TransferRules.formatProgress(512, 0))
        assertEquals("512 o / 1.0 Ko (50 %)", TransferRules.formatProgress(512, 1024))
        assertEquals("2.0 Mo / 2.0 Mo (100 %)", TransferRules.formatProgress(2L * 1024 * 1024, 2L * 1024 * 1024))
        assertEquals("0 o / 1.0 Ko (0 %)", TransferRules.formatProgress(-5, 1024))
    }

    @Test
    fun uploadMethodIsRestricted() {
        assertEquals("PUT", TransferRules.normalizeUploadMethod("put"))
        assertEquals("POST", TransferRules.normalizeUploadMethod(" POST "))
        assertEquals("PUT", TransferRules.normalizeUploadMethod("DELETE"))
    }
}
