package io.nekohasekai.sfa.dams.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Format de thème portable `.a2mtheme` (version 1).
 *
 * Un fichier `.a2mtheme` est un JSON autonome :
 * ```
 * {
 *   "format": "a2mtheme",
 *   "version": 1,
 *   "name": "Mon thème",
 *   "dark": true,
 *   "colors": { "backgroundTop": "#FF0D1418", ... }
 * }
 * ```
 *
 * Aucune dépendance Android : ce fichier est testable sur la JVM (couleurs
 * manipulées sous forme de chaînes hexadécimales `#RRGGBB` ou `#AARRGGBB`).
 */
object ThemePackage {
    const val FORMAT = "a2mtheme"
    const val VERSION = 1
    const val EXTENSION = ".a2mtheme"
    const val MIME_TYPE = "application/json"

    /** Les 13 jetons de couleur d'une palette A2M TUN, dans l'ordre d'affichage. */
    val COLOR_KEYS =
        listOf(
            "backgroundTop",
            "backgroundMid",
            "accent",
            "accentUpload",
            "text",
            "textMuted",
            "faint",
            "border",
            "cardTop",
            "cardBottom",
            "tile",
            "danger",
            "amber",
        )

    /** Libellés français des jetons, utilisés par l'éditeur de thème. */
    val COLOR_LABELS: Map<String, String> =
        mapOf(
            "backgroundTop" to "Fond (haut)",
            "backgroundMid" to "Fond (bas)",
            "accent" to "Accent principal",
            "accentUpload" to "Accent secondaire",
            "text" to "Texte",
            "textMuted" to "Texte secondaire",
            "faint" to "Texte discret",
            "border" to "Bordures",
            "cardTop" to "Cartes (haut)",
            "cardBottom" to "Cartes (bas)",
            "tile" to "Tuiles",
            "danger" to "Alerte",
            "amber" to "Avertissement",
        )

    data class Data(
        val name: String,
        val dark: Boolean,
        val colors: Map<String, String>,
    ) {
        fun color(key: String): String = colors.getValue(key)
    }

    private val prettyJson =
        Json {
            prettyPrint = true
        }

    private val compactJson = Json

    /**
     * Encode en JSON. Les couleurs sont normalisées (`#RRGGBB` ou `#AARRGGBB`)
     * pour que deux thèmes identiques produisent le même fichier.
     */
    fun encode(data: Data): String = prettyJson.encodeToString(JsonObject.serializer(), wire(data))

    /** Variante compacte, pratique pour être stockée en une seule préférence. */
    fun encodeCompact(data: Data): String = compactJson.encodeToString(JsonObject.serializer(), wire(data))

    private fun wire(data: Data): JsonObject =
        buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("name", data.name.ifBlank { "Personnalisé" })
            put("dark", data.dark)
            put(
                "colors",
                buildJsonObject {
                    COLOR_KEYS.forEach { key ->
                        put(key, normalizeHex(data.colors[key] ?: "") ?: "#FF000000")
                    }
                },
            )
        }

    /** Décode un fichier ou une préférence ; `null` si le contenu est invalide. */
    fun decode(text: String): Data? {
        if (text.isBlank()) return null
        val root =
            runCatching { compactJson.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: return null
        if (root.string("format") != FORMAT) return null
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: return null
        if (version !in 1..VERSION) return null
        val colorsJson = root["colors"] as? JsonObject ?: return null
        val colors = LinkedHashMap<String, String>()
        for (key in COLOR_KEYS) {
            val raw = (colorsJson[key] as? JsonPrimitive)?.contentOrNull ?: return null
            colors[key] = normalizeHex(raw) ?: return null
        }
        val dark = root["dark"]?.jsonPrimitive?.booleanOrNull ?: true
        val name = root.string("name")?.trim().orEmpty().ifBlank { "Personnalisé" }
        return Data(name = name, dark = dark, colors = colors)
    }

    /**
     * Construit un thème complet à partir d'un mode de base, d'une couleur
     * d'accent et d'une couleur secondaire : les neutres sont choisis pour
     * rester lisibles quel que soit l'accent.
     */
    fun derive(
        dark: Boolean,
        accent: String,
        accentUpload: String,
        name: String = "Personnalisé",
    ): Data {
        val safeAccent = normalizeHex(accent) ?: (if (dark) "#FF16E0C4" else "#FF0FA98F")
        val safeUpload = normalizeHex(accentUpload) ?: (if (dark) "#FF7C5CFF" else "#FF7C5CFF")
        val colors =
            if (dark) {
                linkedMapOf(
                    "backgroundTop" to "#FF0D1418",
                    "backgroundMid" to "#FF080E12",
                    "accent" to safeAccent,
                    "accentUpload" to safeUpload,
                    "text" to "#FFEAF6F3",
                    "textMuted" to "#FF7FA6A0",
                    "faint" to "#FF4A6B66",
                    "border" to "#FF1D2E33",
                    "cardTop" to "#FF122024",
                    "cardBottom" to "#FF0C171A",
                    "tile" to "#FF14262B",
                    "danger" to "#FFFF5C6C",
                    "amber" to "#FFFFB020",
                )
            } else {
                linkedMapOf(
                    "backgroundTop" to "#FFDDF3EE",
                    "backgroundMid" to "#FFF7FBFA",
                    "accent" to safeAccent,
                    "accentUpload" to safeUpload,
                    "text" to "#FF0D2B33",
                    "textMuted" to "#FF54787F",
                    "faint" to "#FF93B3B8",
                    "border" to "#FFD8E8E5",
                    "cardTop" to "#FFFFFFFF",
                    "cardBottom" to "#FFF4FAF8",
                    "tile" to "#FFE3F2EE",
                    "danger" to "#FFE5484D",
                    "amber" to "#FFD97706",
                )
            }
        return Data(name = name, dark = dark, colors = colors)
    }

    /** Normalise `0xRRGGBB`, `#rrggbb`, `RRGGBBAA`… en `#RRGGBB` / `#AARRGGBB`. */
    fun normalizeHex(raw: String): String? {
        val value =
            raw.trim()
                .removePrefix("0x")
                .removePrefix("0X")
                .removePrefix("#")
        if (value.length != 6 && value.length != 8) return null
        if (!value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return "#" + value.uppercase()
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}
