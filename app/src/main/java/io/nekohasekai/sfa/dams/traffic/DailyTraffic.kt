package io.nekohasekai.sfa.dams.traffic

import java.time.LocalDate

/**
 * Consommation d'une journée (octets téléchargés / téléversés).
 * `date` est au format ISO `AAAA-MM-JJ` (tri lexicographique = tri chronologique).
 */
data class DailyTraffic(
    val date: String,
    val download: Long,
    val upload: Long,
) {
    val total: Long
        get() = download + upload
}

/**
 * Journal de consommation : logique pure (aucune dépendance Android).
 *
 * Format de stockage : une ligne par jour, `AAAA-MM-JJ|descendants|montants`.
 * L'analyse ignore silencieusement les lignes invalides afin qu'un fichier
 * corrompu ne bloque jamais le service.
 */
object DailyTrafficLog {
    /** Nombre de jours conservés (90 jours glissants). */
    const val KEEP_DAYS = 90

    private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")

    fun parse(raw: String): List<DailyTraffic> =
        raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split('|')
                if (parts.size != 3) return@mapNotNull null
                val date = parts[0]
                if (!DATE_PATTERN.matches(date)) return@mapNotNull null
                val download = parts[1].toLongOrNull() ?: return@mapNotNull null
                val upload = parts[2].toLongOrNull() ?: return@mapNotNull null
                if (download < 0 || upload < 0) return@mapNotNull null
                DailyTraffic(date, download, upload)
            }
            .sortedBy { it.date }
            .toList()

    fun serialize(entries: List<DailyTraffic>): String =
        entries
            .sortedBy { it.date }
            .joinToString("\n") { "${it.date}|${it.download}|${it.upload}" }

    /**
     * Ajoute un delta à la journée [date] puis élague au-delà de [KEEP_DAYS]
     * jours (par rapport à [today]).
     */
    fun merge(
        entries: List<DailyTraffic>,
        date: String,
        download: Long,
        upload: Long,
        today: String = LocalDate.now().toString(),
    ): List<DailyTraffic> {
        if (download <= 0 && upload <= 0) return entries
        if (!DATE_PATTERN.matches(date)) return entries
        val byDate = entries.associateBy { it.date }.toMutableMap()
        val current = byDate[date] ?: DailyTraffic(date, 0, 0)
        byDate[date] =
            current.copy(
                download = current.download + download,
                upload = current.upload + upload,
            )
        val oldest = LocalDate.parse(today).minusDays((KEEP_DAYS - 1).toLong()).toString()
        return byDate.values
            .filter { it.date >= oldest && it.date <= today }
            .sortedBy { it.date }
    }

    /** Série continue de [days] jours se terminant à [end] (jours manquants à zéro). */
    fun series(
        entries: List<DailyTraffic>,
        end: String,
        days: Int,
    ): List<DailyTraffic> {
        if (days <= 0) return emptyList()
        val byDate = entries.associateBy { it.date }
        val endDate = LocalDate.parse(end)
        return (days - 1 downTo 0).map { offset ->
            val date = endDate.minusDays(offset.toLong()).toString()
            byDate[date] ?: DailyTraffic(date, 0, 0)
        }
    }

    /** Totaux descendants / montants d'une sélection de jours. */
    fun sum(entries: List<DailyTraffic>): Pair<Long, Long> =
        entries.fold(0L to 0L) { acc, entry ->
            (acc.first + entry.download) to (acc.second + entry.upload)
        }

    /** Export CSV (entêtes en français, séparateur point-virgule). */
    fun csv(entries: List<DailyTraffic>): String =
        buildString {
            appendLine("date;telechargement_octets;televersement_octets;total_octets")
            entries.sortedBy { it.date }.forEach {
                appendLine("${it.date};${it.download};${it.upload};${it.total}")
            }
        }

    /** Clé de la journée courante, heure locale du téléphone. */
    fun today(): String = LocalDate.now().toString()
}
