package app.naviamp.domain.smartplaylist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartPlaylistRoundTripTest {
    private fun imported(properties: String = ""): SmartPlaylistDefinition =
        SmartPlaylistDefinition.fromNspJson(
            """{"name":"Mix","all":[{"is":{"loved":true}}]$properties}""",
        )

    private fun reopen(definition: SmartPlaylistDefinition): SmartPlaylistDefinition =
        SmartPlaylistDefinition.fromNspJson(SmartPlaylistDraft.fromDefinition(definition).toDefinition().toNspJson())

    @Test
    fun absentLimitAndSortStayAbsentWhileNewDraftKeepsIntentionalDefaults() {
        val draft = SmartPlaylistDraft.fromDefinition(imported())
        assertEquals(SmartPlaylistLimitMode.Unlimited, draft.limitMode)
        assertTrue(draft.sort.isEmpty())
        val saved = reopen(imported())
        assertNull(saved.limit)
        assertNull(saved.limitPercent)
        assertTrue(saved.sort.isEmpty())
        assertEquals(SmartPlaylistLimitMode.TrackCount, SmartPlaylistDraft().limitMode)
        assertEquals(100, SmartPlaylistDraft().limit)
        assertTrue(SmartPlaylistDraft().sort.isNotEmpty())
    }

    @Test
    fun countAndPercentageLimitsRemainDistinct() {
        for (value in listOf(1, 25, 100)) {
            val count = reopen(imported(",\"limit\":$value"))
            assertEquals(value, count.limit)
            assertNull(count.limitPercent)
            val percent = reopen(imported(",\"limitPercent\":$value"))
            assertEquals(value, percent.limitPercent)
            assertNull(percent.limit)
        }
    }

    @Test
    fun signedFieldsAndGlobalDirectionAreNormalizedWithoutChangingOrder() {
        val original = imported(",\"sort\":\" -Rating, + Title, Year \",\"order\":\" DESC \"")
        val expected = listOf(
            SmartPlaylistSort(SmartPlaylistFields.Rating),
            SmartPlaylistSort(SmartPlaylistFields.Title, descending = true),
            SmartPlaylistSort(SmartPlaylistFields.Year, descending = true),
        )
        assertEquals(expected, original.sort)
        assertEquals(expected, reopen(original).sort)
        assertEquals(listOf(SmartPlaylistSort(SmartPlaylistFields.Title, true)), reopen(imported(",\"order\":\"desc\"")).sort)
        assertTrue(reopen(imported(",\"order\":\"asc\"")).sort.isEmpty())
    }

    @Test
    fun decimalComparisonsAndRangesSurviveTwoEditorRoundTrips() {
        val original = SmartPlaylistDefinition.fromNspJson(
            """{"name":"Decimals","public":true,"all":[
              {"gt":{"averagerating":3.75}},
              {"inTheRange":{"duration":[120.25,240.75]}},
              {"lt":{"albumduration":3600.5}}
            ],"refreshDelay":"1d12h","sort":"+duration,-albumduration","limitPercent":17}""",
        )
        val restored = reopen(reopen(original))
        assertEquals(original, restored)
    }

    @Test
    fun numericLookingTextRemainsText() {
        for (text in listOf("123", "3.75", "true")) {
            val original = SmartPlaylistDefinition.fromNspJson("""{"name":"Text","all":[{"is":{"title":"$text"}}]}""")
            assertEquals(SmartPlaylistValue.Text(text), (original.rules.single() as SmartPlaylistCondition).value)
            assertEquals(original.rules, reopen(original).rules)
        }
    }

    @Test
    fun unsupportedOrAmbiguousImportsFailRatherThanLosingProperties() {
        for (properties in listOf(
            ",\"limit\":4294967297", ",\"limit\":1.5", ",\"limit\":\"25\"",
            ",\"limit\":25,\"limitPercent\":25", ",\"limitPercent\":101",
            ",\"sort\":\"title,unknown\"", ",\"order\":\"backwards\"",
            ",\"futureOption\":true", ",\"any\":[{\"is\":{\"loved\":true}}]",
        )) assertFailsWith<IllegalArgumentException> { imported(properties) }
        assertFailsWith<IllegalArgumentException> {
            SmartPlaylistDefinition.fromNspJson("""{"name":"Ambiguous","all":[{"is":{"loved":true},"gt":{"rating":3}}]}""")
        }
    }

    @Test
    fun nestedProgrammaticDefinitionsCannotBeSilentlyFlattened() {
        val nested = imported().copy(rules = listOf(SmartPlaylistGroup(SmartPlaylistMatch.Any,
            listOf(SmartPlaylistGroup(SmartPlaylistMatch.All, imported().rules)))))
        assertFailsWith<IllegalArgumentException> { SmartPlaylistDraft.fromDefinition(nested) }
    }

    @Test
    fun valuesTheEditorCannotRepresentAreRejectedBeforeOpening() {
        for (rule in listOf(
            """{"gt":{"rating":3.75}}""",
            """{"is":{"year":[1990,2000]}}""",
            """{"inTheRange":{"year":2000}}""",
            """{"inTheRange":{"duration":[[1,2],3]}}""",
        )) {
            assertFailsWith<IllegalArgumentException> {
                SmartPlaylistDraft.fromDefinition(SmartPlaylistDefinition.fromNspJson(
                    """{"name":"Invalid","all":[$rule]}""",
                ))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            SmartPlaylistDefinition.fromNspJson("""{"name":"Infinite","all":[{"gt":{"duration":1e999}}]}""")
        }
    }
}
