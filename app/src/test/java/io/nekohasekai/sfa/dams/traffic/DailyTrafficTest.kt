package io.nekohasekai.sfa.dams.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyTrafficTest {
    @Test
    fun parseIgnoresInvalidLinesAndSorts() {
        val raw =
            """
            2026-11-16|100|20
            ligne invalide
            2026-11-15|5|1
            2026-13-01|9|9
            2026-11-17|-3|4
            2026-11-17|50|7|extra
            2026-11-17|300|40
            """.trimIndent()
        assertEquals(
            listOf(
                DailyTraffic("2026-11-15", 5, 1),
                DailyTraffic("2026-11-16", 100, 20),
                DailyTraffic("2026-11-17", 300, 40),
            ),
            DailyTrafficLog.parse(raw),
        )
        assertTrue(DailyTrafficLog.parse("").isEmpty())
    }

    @Test
    fun serializeRoundTrips() {
        val entries =
            listOf(
                DailyTraffic("2026-11-15", 5, 1),
                DailyTraffic("2026-11-16", 100, 20),
            )
        assertEquals(entries, DailyTrafficLog.parse(DailyTrafficLog.serialize(entries)))
        assertEquals("", DailyTrafficLog.serialize(emptyList()))
    }

    @Test
    fun mergeAccumulatesSameDayAndPrunesOldEntries() {
        val today = "2026-11-17"
        val old = "2020-01-01"
        val entries =
            listOf(
                DailyTraffic(old, 1, 1),
                DailyTraffic(today, 10, 2),
            )
        val merged = DailyTrafficLog.merge(entries, today, 90, 8, today)
        assertEquals(listOf(DailyTraffic(today, 100, 10)), merged)

        val created = DailyTrafficLog.merge(emptyList(), "2026-11-16", 3, 4, today)
        assertEquals(listOf(DailyTraffic("2026-11-16", 3, 4)), created)
    }

    @Test
    fun mergeIgnoresEmptyOrInvalidDelta() {
        val entries = listOf(DailyTraffic("2026-11-17", 10, 2))
        assertEquals(entries, DailyTrafficLog.merge(entries, "2026-11-17", 0, 0, "2026-11-17"))
        assertEquals(entries, DailyTrafficLog.merge(entries, "pas-une-date", 5, 5, "2026-11-17"))
    }

    @Test
    fun seriesFillsMissingDaysInOrder() {
        val entries =
            listOf(
                DailyTraffic("2026-11-12", 7, 1),
                DailyTraffic("2026-11-16", 3, 2),
            )
        val series = DailyTrafficLog.series(entries, "2026-11-17", 7)
        assertEquals(7, series.size)
        assertEquals("2026-11-11", series.first().date)
        assertEquals("2026-11-17", series.last().date)
        assertEquals(DailyTraffic("2026-11-12", 7, 1), series[1])
        assertEquals(0L, series.last().total)
        assertEquals(DailyTraffic("2026-11-16", 3, 2), series[5])
        assertTrue(DailyTrafficLog.series(entries, "2026-11-17", 0).isEmpty())
    }

    @Test
    fun sumsAndCsvUseRealValues() {
        val entries =
            listOf(
                DailyTraffic("2026-11-16", 100, 20),
                DailyTraffic("2026-11-17", 300, 40),
            )
        assertEquals(400L to 60L, DailyTrafficLog.sum(entries))
        val csv = DailyTrafficLog.csv(entries)
        val lines = csv.trim().lines()
        assertEquals("date;telechargement_octets;televersement_octets;total_octets", lines[0])
        assertEquals("2026-11-16;100;20;120", lines[1])
        assertEquals("2026-11-17;300;40;340", lines[2])
        assertEquals(120L, entries[0].total)
    }
}
