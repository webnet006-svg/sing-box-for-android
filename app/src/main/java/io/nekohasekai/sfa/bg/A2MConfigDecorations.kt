package io.nekohasekai.sfa.bg

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Décorations de configuration appliquées par A2M TUN au chargement du tunnel.
 *
 * Aucune règle inventée : on injecte des objets de configuration sing-box standards.
 * En cas d'échec du parse (config exotique), la configuration originale est rendue telle quelle.
 */
object A2MConfigDecorations {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private const val ADS_RULE_SET_TAG = "geosite-category-ads-all"
    private const val ADS_RULE_SET_URL =
        "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ads-all.srs"

    /**
     * Bloqueur de publicités : ajoute (si absents) un sortant « block » et une règle
     * de routage vers un rule-set distant de domaines publicitaires (geosite ads-all).
     */
    fun withAdBlock(content: String): String =
        runCatching { addAdBlockRules(content) }.getOrDefault(content)

    private fun addAdBlockRules(content: String): String {
        val root = json.parseToJsonElement(content).jsonObject.toMutableMap()

        // 1. Sortant de blocage : réutilise celui de la config s'il existe.
        val outbounds = (root["outbounds"] as? JsonArray)?.toMutableList() ?: JsonArray(emptyList()).toMutableList()
        val existingBlockTag =
            outbounds
                .filterIsInstance<JsonObject>()
                .firstOrNull { (it["type"] as? JsonPrimitive)?.content == "block" }
                ?.let { (it["tag"] as? JsonPrimitive)?.content }
        val blockTag = existingBlockTag ?: run {
            val tag = "block"
            if (outbounds.none { (it as? JsonObject)?.get("tag")?.let { t -> (t as? JsonPrimitive)?.content } == tag }) {
                outbounds.add(
                    buildJsonObject {
                        put("type", "block")
                        put("tag", tag)
                    },
                )
            }
            tag
        }
        root["outbounds"] = JsonArray(outbounds)

        // Sortant direct pour le téléchargement du rule-set : réutilise ou ajoute.
        val existingDirectTag =
            outbounds
                .filterIsInstance<JsonObject>()
                .firstOrNull { (it["type"] as? JsonPrimitive)?.content == "direct" }
                ?.let { (it["tag"] as? JsonPrimitive)?.content }
        val directTag = existingDirectTag ?: run {
            val tag = "direct"
            if (outbounds.none { (it as? JsonObject)?.get("tag")?.let { t -> (t as? JsonPrimitive)?.content } == tag }) {
                outbounds.add(
                    buildJsonObject {
                        put("type", "direct")
                        put("tag", tag)
                    },
                )
            }
            tag
        }
        root["outbounds"] = JsonArray(outbounds)

        // 2. Route : règle de blocage en tête + définition du rule-set.
        val route = (root["route"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val rules = (route["rules"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        rules.add(
            0,
            buildJsonObject {
                put("rule_set", buildJsonArray { add(JsonPrimitive(ADS_RULE_SET_TAG)) })
                put("outbound", blockTag)
            },
        )
        route["rules"] = JsonArray(rules)

        val ruleSets = (route["rule_set"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        val alreadyPresent =
            ruleSets.any {
                (it as? JsonObject)?.get("tag")?.let { t -> (t as? JsonPrimitive)?.content } == ADS_RULE_SET_TAG
            }
        if (!alreadyPresent) {
            ruleSets.add(
                buildJsonObject {
                    put("type", "remote")
                    put("tag", ADS_RULE_SET_TAG)
                    put("format", "binary")
                    put("url", ADS_RULE_SET_URL)
                    put("download_detour", directTag)
                },
            )
        }
        route["rule_set"] = JsonArray(ruleSets)
        root["route"] = JsonObject(route)

        return json.encodeToString(JsonObject.serializer(), JsonObject(root))
    }
}
