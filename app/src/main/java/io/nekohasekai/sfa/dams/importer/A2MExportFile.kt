package io.nekohasekai.sfa.dams.importer

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Format de fichier `.a2m` : export/import d'un ou plusieurs serveurs.
 *
 * Deux formes, volontairement distinctes de tout chiffrement :
 *  - « simple » : JSON lisible, configurations incluses telles quelles ;
 *  - « codé » : les mêmes entrées sont compressées (GZIP) puis encodées
 *    (base64url). Ce n'est ni un chiffrement ni une signature.
 *
 * Chaque forme embarque une empreinte SHA-256 des entrées canoniques. Elle
 * détecte une altération ou une corruption accidentelle ; elle ne prouve pas
 * l'origine du fichier.
 *
 * Le champ `version` permet d'ajouter plus tard un vrai chiffrement sans
 * casser les fichiers actuels : les versions inconnues sont refusées, les
 * anciennes restent lisibles.
 */
object A2MExportFile {
    const val EXTENSION = ".a2m"
    const val MIME_TYPE = "application/octet-stream"

    private const val FORMAT = "a2m"
    private const val VERSION = 1
    private const val MAX_ENTRIES = 500
    private const val MAX_DECODED_BYTES = 16 * 1024 * 1024

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
        }

    private val compactJson =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    data class Entry(
        val name: String,
        val config: String,
    )

    sealed class Decoded {
        data class Success(
            val entries: List<Entry>,
            val encoding: String,
        ) : Decoded()

        data class Error(val message: String) : Decoded()
    }

    fun isA2M(text: String): Boolean = parseRoot(text)?.string("format") == FORMAT

    /** Fichier lisible : les configurations restent des objets JSON. */
    fun encodeSimple(entries: List<Entry>): String {
        require(entries.isNotEmpty()) { "Aucun serveur à exporter." }
        require(entries.size <= MAX_ENTRIES) { "Trop de serveurs (maximum $MAX_ENTRIES)." }
        val canonical = canonicalEntries(entries)
        val root =
            buildJsonObject {
                put("format", FORMAT)
                put("version", VERSION)
                put("encoding", "json")
                put("entries", readableEntries(entries))
                put("sha256", sha256(canonical))
            }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** Fichier compact : GZIP + base64url, sans chiffrement. */
    fun encodeCoded(entries: List<Entry>): String {
        require(entries.isNotEmpty()) { "Aucun serveur à exporter." }
        require(entries.size <= MAX_ENTRIES) { "Trop de serveurs (maximum $MAX_ENTRIES)." }
        val canonical = canonicalEntries(entries)
        val payload = Base64.UrlSafe.encode(gzip(canonical.toByteArray(Charsets.UTF_8)))
        val root =
            buildJsonObject {
                put("format", FORMAT)
                put("version", VERSION)
                put("encoding", "gzip+base64url")
                put("payload", payload)
                put("sha256", sha256(canonical))
            }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    fun decode(text: String): Decoded {
        val root = parseRoot(text) ?: return Decoded.Error("Fichier .a2m illisible.")
        if (root.string("format") != FORMAT) return Decoded.Error("Ce fichier n'est pas un export A2M.")
        val version = root.int("version")
        if (version <= 0 || version > VERSION) {
            return Decoded.Error("Version .a2m non prise en charge : $version.")
        }
        val expected = root.string("sha256").lowercase()
        if (expected.isBlank()) return Decoded.Error("Somme de contrôle absente.")
        val encoding = root.string("encoding").ifBlank { "json" }
        val canonical =
            when (encoding) {
                "json" -> {
                    val array = root["entries"] as? JsonArray
                        ?: return Decoded.Error("Liste de serveurs absente.")
                    canonicalFromArray(array)
                }
                "gzip+base64url" -> {
                    val payload = root.string("payload")
                    if (payload.isBlank()) return Decoded.Error("Charge codée absente.")
                    val decoded =
                        runCatching {
                            gunzipLimited(Base64.UrlSafe.decode(payload), MAX_DECODED_BYTES)
                        }.getOrNull() ?: return Decoded.Error("Charge codée invalide.")
                    val array =
                        runCatching { compactJson.parseToJsonElement(decoded).jsonArray }.getOrNull()
                            ?: return Decoded.Error("Contenu .a2m invalide.")
                    canonicalFromArray(array)
                }
                else -> return Decoded.Error("Encodage .a2m inconnu : $encoding.")
            } ?: return Decoded.Error("Entrées .a2m invalides.")
        if (canonical.entries.isEmpty()) return Decoded.Error("Aucun serveur dans le fichier.")
        if (canonical.entries.size > MAX_ENTRIES) {
            return Decoded.Error("Trop de serveurs dans le fichier (maximum $MAX_ENTRIES).")
        }
        val actual = sha256(canonical.text)
        if (!MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())) {
            return Decoded.Error(
                "Intégrité .a2m invalide : le fichier a été modifié ou corrompu.",
            )
        }
        return Decoded.Success(canonical.entries, encoding)
    }

    private data class Canonical(
        val entries: List<Entry>,
        val text: String,
    )

    private fun parseRoot(text: String): JsonObject? =
        runCatching {
            compactJson.parseToJsonElement(text.replace("\uFEFF", "").trim()).jsonObject
        }.getOrNull()

    private fun canonicalEntries(entries: List<Entry>): String {
        val array =
            buildJsonArray {
                entries.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("name", entry.name.ifBlank { "Serveur" })
                            put("config", canonicalConfig(entry.config))
                        },
                    )
                }
            }
        return compactJson.encodeToString(JsonElement.serializer(), array)
    }

    private fun readableEntries(entries: List<Entry>): JsonArray =
        buildJsonArray {
            entries.forEach { entry ->
                add(
                    buildJsonObject {
                        put("name", entry.name.ifBlank { "Serveur" })
                        val parsed =
                            runCatching { compactJson.parseToJsonElement(entry.config) }.getOrNull()
                        if (parsed != null) put("config", parsed) else put("config", entry.config)
                    },
                )
            }
        }

    private fun canonicalFromArray(array: JsonArray): Canonical? {
        if (array.size > MAX_ENTRIES) return null
        val entries =
            array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = obj.string("name").ifBlank { "Serveur" }
                val configElement = obj["config"] ?: return@mapNotNull null
                val config =
                    when (configElement) {
                        is JsonPrimitive ->
                            configElement.contentOrNull ?: return@mapNotNull null
                        else ->
                            compactJson.encodeToString(JsonElement.serializer(), configElement)
                    }.trim()
                if (config.isBlank()) return@mapNotNull null
                Entry(name, canonicalConfig(config))
            }
        if (entries.size != array.size) return null
        return Canonical(entries, canonicalEntries(entries))
    }

    private fun canonicalConfig(config: String): String {
        val parsed =
            runCatching { compactJson.parseToJsonElement(config.trim()) }.getOrNull()
        return if (parsed != null) {
            compactJson.encodeToString(JsonElement.serializer(), parsed)
        } else {
            config.trim()
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun gzip(text: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(text) }
        return output.toByteArray()
    }

    private fun gunzipLimited(
        compressed: ByteArray,
        maxBytes: Int,
    ): String {
        GZIPInputStream(compressed.inputStream()).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw IllegalArgumentException("Fichier .a2m trop volumineux.")
                }
                output.write(buffer, 0, read)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0
}
