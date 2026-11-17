package io.nekohasekai.sfa.dams.transfer

import java.net.URLDecoder
import java.util.Locale

/**
 * Règles pures des transferts de fichiers (aucune dépendance Android).
 * Sécurité : les URL contenant des identifiants (`user:pass@`) sont refusées
 * pour éviter toute fuite dans l'historique ou les journaux.
 */
object TransferRules {
    const val MAX_URL_LENGTH = 2048

    /** Retourne l'URL validée (http/https, sans identifiants) ou null. */
    fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH) return null
        val lower = trimmed.lowercase(Locale.ROOT)
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return null
        val withoutScheme = trimmed.substringAfter("://")
        val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty()) return null
        if (authority.contains('@')) return null
        if (authority.contains(' ')) return null
        return trimmed
    }

    fun isCleartext(url: String): Boolean = url.trim().lowercase(Locale.ROOT).startsWith("http://")

    /** Nom de fichier proposé à partir du chemin de l'URL. */
    fun fileNameFromUrl(url: String): String {
        val withoutQuery = url.substringBefore('?').substringBefore('#')
        val last = withoutQuery.trimEnd('/').substringAfterLast('/')
        val decoded =
            try {
                URLDecoder.decode(last, "UTF-8")
            } catch (_: Exception) {
                last
            }
        val sanitized = sanitizeFileName(decoded)
        return sanitized.ifBlank { "fichier" }
    }

    /** Supprime séparateurs de chemin et caractères de contrôle. */
    fun sanitizeFileName(name: String): String =
        name
            .map { ch -> if (ch == '/' || ch == '\\' || ch.code < 0x20) '_' else ch }
            .joinToString("")
            .trim()
            .take(120)

    /** Progression lisible : « 1,2 Mo / 3,4 Mo (35 %) ». */
    fun formatProgress(
        transferred: Long,
        total: Long,
    ): String {
        if (total <= 0L) return formatBytes(transferred)
        val percent = ((transferred.coerceAtLeast(0L) * 100) / total).coerceIn(0L, 100L)
        return "${formatBytes(transferred)} / ${formatBytes(total)} ($percent %)"
    }

    /** Tailles façon française : o, Ko, Mo, Go (une décimale à partir de Ko). */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "—"
        if (bytes < 1024L) return "$bytes o"
        val units = listOf("Ko", "Mo", "Go", "To")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        val text = if (value >= 100.0) "%.0f".format(Locale.ROOT, value) else "%.1f".format(Locale.ROOT, value)
        return "$text ${units[unit.coerceAtLeast(0)]}"
    }

    /** Méthodes HTTP acceptées pour le téléversement. */
    fun normalizeUploadMethod(raw: String): String {
        val method = raw.trim().uppercase(Locale.ROOT)
        return if (method == "POST") "POST" else "PUT"
    }
}
