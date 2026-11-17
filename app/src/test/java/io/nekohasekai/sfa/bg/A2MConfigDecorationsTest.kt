package io.nekohasekai.sfa.bg

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la décoration de configuration A2M (bloqueur de publicités).
 * La transformation doit produire une configuration sing-box valide :
 * sortant block + règle de routage + rule-set distant, sans écraser l'existant.
 */
class A2MConfigDecorationsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun addsBlockOutboundAndRuleWhenMissing() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy","server":"a.com"}],"route":{"rules":[]}}"""
        val decorated = A2MConfigDecorations.withAdBlock(config)
        val root = json.parseToJsonElement(decorated).jsonObject

        val outbounds = root["outbounds"]!!.jsonArray
        val block = outbounds.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "block" }
        assertTrue("outbound block doit être ajouté", block != null)
        val direct = outbounds.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "direct" }
        assertTrue("outbound direct doit être ajouté", direct != null)

        val rules = root["route"]!!.jsonObject["rules"]!!.jsonArray
        val adRule = rules.first().jsonObject
        assertEquals("geosite-category-ads-all", adRule["rule_set"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("block", adRule["outbound"]!!.jsonPrimitive.content)

        val ruleSet = root["route"]!!.jsonObject["rule_set"]!!.jsonArray[0].jsonObject
        assertEquals("remote", ruleSet["type"]!!.jsonPrimitive.content)
        assertEquals("direct", ruleSet["download_detour"]!!.jsonPrimitive.content)
        assertTrue(ruleSet["url"]!!.jsonPrimitive.content.contains("geosite-category-ads-all"))
    }

    @Test
    fun reusesExistingBlockOutboundAndPrependsRule() {
        val config =
            """{"outbounds":[{"type":"vmess","tag":"proxy"},{"type":"block","tag":"stop"}],""" +
                """"route":{"rules":[{"inbound":["dns"],"action":"sniff"}]}}"""
        val decorated = A2MConfigDecorations.withAdBlock(config)
        val root = json.parseToJsonElement(decorated).jsonObject
        val outbounds = root["outbounds"]!!.jsonArray
        assertEquals(2, outbounds.size)
        val adRule = root["route"]!!.jsonObject["rules"]!!.jsonArray.first().jsonObject
        assertEquals("stop", adRule["outbound"]!!.jsonPrimitive.content)
    }

    @Test
    fun doesNotDuplicateRuleSetOnRepeatedApplication() {
        val config = """{"outbounds":[{"type":"vmess","tag":"proxy"}]}"""
        val once = A2MConfigDecorations.withAdBlock(config)
        val twice = A2MConfigDecorations.withAdBlock(once)
        assertEquals(
            1,
            json.parseToJsonElement(twice).jsonObject["route"]!!.jsonObject["rule_set"]!!.jsonArray.size,
        )
    }

    @Test
    fun keepsOriginalContentWhenUnparseable() {
        val broken = "ceci n'est pas du json"
        assertEquals(broken, A2MConfigDecorations.withAdBlock(broken))
    }
}
