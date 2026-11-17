package io.nekohasekai.sfa.dams.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackageTest {
    private fun sample(dark: Boolean = true): ThemePackage.Data =
        ThemePackage.derive(dark, "#FF16E0C4", "#FF7C5CFF", "Essai")

    @Test
    fun roundTripsAllTokens() {
        val data = sample()
        val decoded = ThemePackage.decode(ThemePackage.encode(data))
        assertNotNull(decoded)
        decoded!!
        assertEquals("Essai", decoded.name)
        assertTrue(decoded.dark)
        assertEquals(ThemePackage.COLOR_KEYS.toSet(), decoded.colors.keys)
        ThemePackage.COLOR_KEYS.forEach { key ->
            assertEquals(data.color(key), decoded.color(key))
        }
    }

    @Test
    fun compactAndPrettyEncodeSameData() {
        val data = sample(false)
        val pretty = ThemePackage.decode(ThemePackage.encode(data))
        val compact = ThemePackage.decode(ThemePackage.encodeCompact(data))
        assertEquals(pretty, compact)
    }

    @Test
    fun rejectsInvalidDocuments() {
        assertNull(ThemePackage.decode(""))
        assertNull(ThemePackage.decode("pas du json"))
        assertNull(ThemePackage.decode("""{"format":"autre","version":1}"""))
        assertNull(ThemePackage.decode("""{"format":"a2mtheme","version":99}"""))
    }

    @Test
    fun rejectsMissingOrInvalidColors() {
        val valid = ThemePackage.encodeCompact(sample())
        val missing = valid.replace(",\"amber\":\"#FFFFB020\"", "")
        assertNull(ThemePackage.decode(missing))
        val invalidHex = valid.replaceFirst("#FF16E0C4", "#ZZZZZZ")
        assertNull(ThemePackage.decode(invalidHex))
    }

    @Test
    fun normalizationAcceptsCommonForms() {
        assertEquals("#112233", ThemePackage.normalizeHex("112233"))
        assertEquals("#112233", ThemePackage.normalizeHex("#112233"))
        assertEquals("#FF112233", ThemePackage.normalizeHex("0xFF112233"))
        assertEquals("#AABBCCDD", ThemePackage.normalizeHex("aabbccdd"))
        assertNull(ThemePackage.normalizeHex("#12345"))
        assertNull(ThemePackage.normalizeHex("rouge"))
    }

    @Test
    fun deriveKeepsAccentsAndNeutralsCoherent() {
        val dark = ThemePackage.derive(true, "#ffff0000", "#ff00ff00")
        assertEquals("#FFFF0000", dark.color("accent"))
        assertEquals("#FF00FF00", dark.color("accentUpload"))
        assertTrue(dark.dark)
        val light = ThemePackage.derive(false, "#ff0000ff", "#ff00ffff")
        assertEquals("#FF0000FF", light.color("accent"))
        assertNotEquals(dark.color("text"), light.color("text"))
    }

    @Test
    fun deriveFallsBackOnInvalidAccent() {
        val fallback = ThemePackage.derive(true, "pas-une-couleur", "")
        assertEquals("#FF16E0C4", fallback.color("accent"))
        assertEquals("#FF7C5CFF", fallback.color("accentUpload"))
    }
}
