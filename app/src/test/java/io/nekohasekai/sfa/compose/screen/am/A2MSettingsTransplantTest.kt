package io.nekohasekai.sfa.compose.screen.am

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du transplant de réglages (façon ONFAA) : logique pure uniquement,
 * sans dépendance à Android (le catalogue utilise android.graphics.Color).
 */
class A2MSettingsTransplantTest {
    @Test
    fun themeModeFromKeyResolvesKnownKeys() {
        assertEquals(A2MThemeMode.SYSTEM, A2MThemeMode.fromKey("system"))
        assertEquals(A2MThemeMode.LIGHT, A2MThemeMode.fromKey("light"))
        assertEquals(A2MThemeMode.DARK, A2MThemeMode.fromKey("dark"))
    }

    @Test
    fun themeModeFromKeyFallsBackToDark() {
        assertEquals(A2MThemeMode.DARK, A2MThemeMode.fromKey("unknown-key"))
        assertEquals(A2MThemeMode.DARK, A2MThemeMode.fromKey(""))
    }

    @Test
    fun backgroundBehaviorLabelsAreFrenchAndStable() {
        assertEquals("Toujours actif", backgroundBehaviorLabel("always"))
        assertEquals("Optimisé", backgroundBehaviorLabel("optimized"))
        assertEquals("Arrêt en arrière-plan", backgroundBehaviorLabel("stop"))
        // Valeur inconnue : affichée telle quelle (aucune donnée inventée).
        assertEquals("custom", backgroundBehaviorLabel("custom"))
    }

    @Test
    fun choiceCatalogsStayBounded() {
        assertEquals(listOf("15", "30", "60", "120"), timeoutChoices().map { it.value })
        assertEquals(listOf("3", "5", "10", "30"), reconnectDelayChoices().map { it.value })
        assertEquals(listOf("always", "optimized", "stop"), backgroundBehaviorChoices().map { it.value })
        assertTrue(timeoutChoices().all { it.description == null })
        assertTrue(backgroundBehaviorChoices().all { !it.description.isNullOrBlank() })
    }
}
