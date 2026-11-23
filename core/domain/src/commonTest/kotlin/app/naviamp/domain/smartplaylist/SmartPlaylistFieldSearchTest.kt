package app.naviamp.domain.smartplaylist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartPlaylistFieldSearchTest {
    private val englishLabel: (SmartPlaylistFieldOption) -> String = { it.label }

    private fun ruleChoices(query: String, label: (SmartPlaylistFieldOption) -> String = englishLabel) =
        smartPlaylistFieldChoices(SmartPlaylistFieldCatalog.fields, query, SmartPlaylistFieldCatalog.commonRuleFields, label)

    private fun sortChoices(query: String) = smartPlaylistFieldChoices(
        SmartPlaylistFieldCatalog.sortableFields, query, SmartPlaylistFieldCatalog.commonSortFields, englishLabel,
    )

    private fun List<SmartPlaylistFieldChoiceGroup>.keys() = flatMap { group -> group.fields.map { it.field } }

    @Test
    fun emptyQueryLeadsWithCommonFieldsAndListsEveryFieldOnceUnderACategory() {
        val groups = ruleChoices("  ")

        assertEquals(SmartPlaylistFieldCategory.Common, groups.first().category)
        assertEquals(SmartPlaylistFieldCatalog.commonRuleFields, groups.first().fields.map { it.field })
        assertEquals(SmartPlaylistFieldCatalog.fields.map { it.field }.sorted(), groups.keys().sorted())
        assertTrue(groups.size in 5..SmartPlaylistFieldCategory.entries.size)
        groups.drop(1).forEach { group ->
            assertEquals(group.fields.map { it.label.lowercase() }.sorted(), group.fields.map { it.label.lowercase() })
            assertTrue(group.fields.all { it.category == group.category })
        }
    }

    @Test
    fun searchMatchesLocalizedLabelsBeforeTechnicalAliases() {
        val localized: (SmartPlaylistFieldOption) -> String = { if (it.field == SmartPlaylistFields.PlayCount) "Wiedergaben" else it.label }

        assertEquals(SmartPlaylistFields.PlayCount, ruleChoices("wieder", localized).keys().first())
        assertTrue(SmartPlaylistFields.PlayCount in ruleChoices("playcount", localized).keys())
        assertTrue(SmartPlaylistFields.PlayCount in ruleChoices("play count", localized).keys())
        assertTrue(SmartPlaylistFields.Loved in ruleChoices("starred").keys())
        assertEquals(SmartPlaylistFields.Bpm, ruleChoices("tempo").keys().single())
        assertEquals(6, ruleChoices("mbid").keys().count { it.startsWith("mbz_") })
        val rating = ruleChoices("rat")
        assertEquals(SmartPlaylistFields.Rating, rating.first { it.category == SmartPlaylistFieldCategory.Activity }.fields.first().field)
        assertTrue(rating.keys().containsAll(listOf(SmartPlaylistFields.Rating, SmartPlaylistFields.AlbumRating, SmartPlaylistFields.DateRated)))
    }

    @Test
    fun searchResultsStayGroupedAndEmptyResultsAreEmpty() {
        val groups = ruleChoices("date")
        assertTrue(groups.isNotEmpty())
        assertTrue(groups.all { group -> group.fields.all { it.category == group.category } })
        assertTrue(SmartPlaylistFieldCategory.Common !in groups.map { it.category })
        assertTrue(ruleChoices("zzzz-no-such-field").isEmpty())
    }

    @Test
    fun sortChoicesOnlyOfferSortableFieldsAndKeepRandom() {
        assertTrue(sortChoices("").keys().all { key -> SmartPlaylistFieldCatalog.sortableFields.any { it.field == key } })
        assertTrue(SmartPlaylistFields.Random in sortChoices("").first().fields.map { it.field })
        assertEquals(listOf(SmartPlaylistFields.Random), sortChoices("shuffle").keys())
        assertEquals(listOf(SmartPlaylistFields.Random), sortChoices("random").keys())
        assertTrue(sortChoices("playlist").isEmpty())
        assertTrue("id" in ruleChoices("playlist").keys())
    }
}
