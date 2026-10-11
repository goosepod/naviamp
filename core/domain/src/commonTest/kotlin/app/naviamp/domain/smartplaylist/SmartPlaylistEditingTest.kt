package app.naviamp.domain.smartplaylist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class SmartPlaylistEditingTest {
    @Test fun unsupportedDefinitionsRetainTheExactInput() {
        for (rule in listOf(
            """{"all":[{"any":[{"is":{"loved":true}}]}]}""",
            """{"futureOperator":{"rating":4}}""",
            """{"is":{"futureField":"value"}}""",
            """{"gt":{"rating":3.75}}""",
        )) {
            val original = """{ "name": "Original", "all": [$rule], "futureSetting": {"nested":[1,2,3]} }"""
            val result = assertIs<SmartPlaylistEditingResult.ReadOnly>(inspectSmartPlaylistJson(original))
            assertEquals(original, result.originalJson)
        }
    }
    @Test fun unsupportedRulesAreDetectedWithoutAnUnknownTopLevelOption() {
        for (rule in listOf("""{"all":[{"any":[{"is":{"loved":true}}]}]}""",
            """{"futureOperator":{"rating":4}}""", """{"is":{"futureField":"yes"}}""",
            """{"gt":{"rating":3.75}}""")) {
            val original = """{"name":"Original","all":[$rule]}"""
            assertEquals(original, assertIs<SmartPlaylistEditingResult.ReadOnly>(inspectSmartPlaylistJson(original)).originalJson)
        }
    }
    @Test fun supportedGroupsAndMetadataRemainEditable() {
        val original = """{"name":"Supported","public":true,"comment":"Keep me","all":[
          {"any":[{"is":{"loved":true}},{"gt":{"rating":3}}]}, {"gt":{"year":2000}}
        ],"sort":"-rating,title","limitPercent":25,"refreshDelay":"1d"}"""
        val result = assertIs<SmartPlaylistEditingResult.Editable>(inspectSmartPlaylistJson(original))
        assertEquals(original, result.originalJson)
        assertEquals("Keep me", result.definition.comment)
        assertEquals(true, result.definition.isPublic)
        assertEquals(25, result.definition.limitPercent)
        assertEquals("1d", result.definition.refreshDelay)
    }
    @Test fun missingServerRulesAreExplicitWhileInvalidImportsRemainInspectable() {
        assertIs<SmartPlaylistEditingResult.Unavailable>(inspectSmartPlaylistObject(Json.parseToJsonElement("""{"id":"p","name":"No rules"}""").jsonObject))
        for (original in listOf("not JSON", "{}", """{"name":"Bad","rules":[1,2]}""")) {
            assertEquals(original, assertIs<SmartPlaylistEditingResult.ReadOnly>(inspectSmartPlaylistJson(original)).originalJson)
        }
    }
    @Test fun ambiguousNodesAndInvalidOptionTypesAreInspectableWithoutCoercion() {
        for (original in listOf(
            """{"name":"Ambiguous","all":[{"is":{"loved":true},"gt":{"rating":3}}]}""",
            """{"name":"Both","all":[{"is":{"loved":true}}],"any":[{"is":{"loved":false}}]}""",
            """{"name":"Unknown schema","rules":{"futureConjunction":[{"future":"rule"}]}}""",
            """{"name":"Invalid visibility","public":"yes","all":[{"is":{"loved":true}}]}""",
        )) assertEquals(original, assertIs<SmartPlaylistEditingResult.ReadOnly>(inspectSmartPlaylistJson(original)).originalJson)
    }
    @Test fun overflowingLimitsAreNotSilentlyTruncated() {
        val original = """{"name":"Overflow","all":[{"is":{"loved":true}}],"limit":4294967297}"""
        assertEquals(original, assertIs<SmartPlaylistEditingResult.ReadOnly>(inspectSmartPlaylistJson(original)).originalJson)
    }
    @Test fun integratedInspectionPreservesUnlimitedDecimalsAndEffectiveSortDirections() {
        for (options in listOf("", "\"sort\":\"-AVGRATING,+TITLE\",\"order\":\"desc\",", "\"limitPercent\":25,")) {
            val original = """{"name":"Integrated",$options"all":[{"gt":{"avgrating":3.75}},{"inTheRange":{"duration":[180.5,240.25]}}]}"""
            val editing = assertIs<SmartPlaylistEditingResult.Editable>(inspectSmartPlaylistJson(original))
            val saved = SmartPlaylistDraft.fromDefinition(editing.definition).toDefinition()
            assertEquals(editing.definition.rules, saved.rules)
            assertEquals(editing.definition.sort, saved.sort)
            assertEquals(editing.definition.limit, saved.limit)
            assertEquals(editing.definition.limitPercent, saved.limitPercent)
            assertEquals(original, editing.originalJson)
            assertIs<SmartPlaylistEditingResult.Editable>(inspectSmartPlaylistJson(saved.toNspJson()))
        }
        val sorted = assertIs<SmartPlaylistEditingResult.Editable>(inspectSmartPlaylistJson(
            """{"name":"Sort","all":[{"is":{"loved":true}}],"sort":"-AVGRATING,+TITLE","order":"desc"}"""))
        assertEquals(listOf(SmartPlaylistSort("avgrating", false), SmartPlaylistSort("title", true)), sorted.definition.sort)
    }

    @Test fun integratedValidationKeepsInvalidValuesOutOfTheEditor() {
        for (rule in listOf(
            """{"after":{"dateadded":"2025-02-29"}}""",
            """{"inTheRange":{"duration":[240.25,180.5]}}""",
            """{"gt":{"rating":6}}""",
        )) {
            val original = """{"name":"Invalid","all":[$rule]}"""
            assertEquals(original, assertIs<SmartPlaylistEditingResult.ReadOnly>(inspectSmartPlaylistJson(original)).originalJson)
        }
    }

}
