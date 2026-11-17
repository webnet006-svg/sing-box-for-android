package io.nekohasekai.sfa.compose.screen.am

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de l'IP Finder : logique pure uniquement (règles ^/$/=, « sans
 * symbole = contient », séparateurs ; / , / ligne) et libellés du retour
 * tactile. Aucune dépendance Android.
 */
class A2MIPFinderTest {
    @Test
    fun rulesParseOperators() {
        val rules = parseIpRules("^10.21 ; $77 ; =1.2.3.4 ; 10.165")
        assertEquals(listOf('^' to "10.21", '$' to "77", '=' to "1.2.3.4", 'c' to "10.165"), rules)
    }

    @Test
    fun rulesAcceptAllSeparators() {
        assertEquals(
            parseIpRules("10.204;10.205;;10.206,10.207\n10.208"),
            listOf('c' to "10.204", 'c' to "10.205", 'c' to "10.206", 'c' to "10.207", 'c' to "10.208"),
        )
        assertEquals(0, parseIpRules("   \n ; ,").size)
    }

    @Test
    fun matchingFollowsOperatorSemantics() {
        assertTrue(ipMatchesRules("10.205.1.2", parseIpRules("10.205")))
        assertTrue(ipMatchesRules("10.219.0.1", parseIpRules("^10.21")))
        assertFalse(ipMatchesRules("192.168.10.21", parseIpRules("^10.21")))
        assertTrue(ipMatchesRules("100.66.7.77", parseIpRules("$77")))
        assertFalse(ipMatchesRules("100.66.7.7", parseIpRules("$77")))
        assertTrue(ipMatchesRules("10.165.136.134", parseIpRules("=10.165.136.134")))
        assertFalse(ipMatchesRules("10.165.136.135", parseIpRules("=10.165.136.134")))
        // Aucune règle : aucune IP n'est acceptée.
        assertFalse(ipMatchesRules("1.2.3.4", emptyList()))
    }

    @Test
    fun clickFeedbackLabelsAreFrench() {
        assertEquals("Son", clickFeedbackLabel("sound"))
        assertEquals("Vibreur", clickFeedbackLabel("vibrate"))
        assertEquals("Silence", clickFeedbackLabel("silent"))
        assertEquals("custom", clickFeedbackLabel("custom"))
    }

    @Test
    fun defaultIpListIsEmbeddedAndUsedWhenCustomIsBlank() {
        val default = parseIpRules(DEFAULT_IP_RULES)
        assertEquals(57, default.size)
        assertEquals('c' to "10.41", default.first())
        assertEquals('c' to "10.246", default.last())
        assertTrue(ipMatchesRules("10.204.12.34", effectiveIpRules("")))
        assertTrue(ipMatchesRules("10.213.1.1", effectiveIpRules("  ; \n")))
        assertFalse(ipMatchesRules("192.168.1.9", effectiveIpRules("")))
        // Liste personnalisée : elle remplace entièrement la liste par défaut.
        val custom = effectiveIpRules("^192.168")
        assertTrue(ipMatchesRules("192.168.1.9", custom))
        assertFalse(ipMatchesRules("10.204.12.34", custom))
    }
}
