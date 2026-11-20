package io.nekohasekai.sfa.compose.screen.am

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class A2MServerSpeedTest {
    @Test
    fun choosesSmallestSuccessfulLatency() {
        val latencies = mapOf(1L to 180L, 2L to 42L, 3L to 95L)
        assertEquals(2L, fastestProfileId(latencies) ?: -1L)
    }

    @Test
    fun ignoresProfilesWithoutSuccessfulTest() {
        val latencies = mapOf(7L to 30L, 8L to 120L)
        assertEquals(7L, fastestProfileId(latencies) ?: -1L)
    }

    @Test
    fun returnsNullWhenNothingWasTested() {
        assertNull(fastestProfileId(emptyMap()))
    }
}
