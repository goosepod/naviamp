package app.naviamp.domain.smartplaylist

import kotlin.test.*

class SmartPlaylistSummaryTest {
    @Test fun booleanEqualityAndNegationHaveTheSameReadableMeaning() {
        for (value in listOf(true, false)) {
            val rule = SmartPlaylistCondition(SmartPlaylistOperator.Is, SmartPlaylistFields.Loved, SmartPlaylistValue.Flag(value))
            assertEquals(value, rule.summaryBooleanValue())
            assertEquals(!value, rule.copy(operator = SmartPlaylistOperator.IsNot).summaryBooleanValue())
            assertNull(rule.copy(operator = SmartPlaylistOperator.IsMissing).summaryBooleanValue())
            assertNull(rule.copy(value = SmartPlaylistValue.Text("true")).summaryBooleanValue())
        }
    }
    @Test fun missingAndPresentFlagsDescribeTheEffectivePredicateWithoutChangingTheRule() {
        for (operator in listOf(SmartPlaylistOperator.IsMissing, SmartPlaylistOperator.IsPresent)) {
            val rule = SmartPlaylistCondition(operator, SmartPlaylistFields.Title, SmartPlaylistValue.Flag(true))
            val json = rule.toJsonElement()
            assertEquals(operator, rule.summaryOperator())
            assertEquals(if (operator == SmartPlaylistOperator.IsMissing) SmartPlaylistOperator.IsPresent else SmartPlaylistOperator.IsMissing,
                rule.copy(value = SmartPlaylistValue.Flag(false)).summaryOperator())
            assertEquals(json, rule.toJsonElement())
        }
        assertEquals(SmartPlaylistOperator.IsNot,
            SmartPlaylistCondition(SmartPlaylistOperator.IsNot, SmartPlaylistFields.Loved, SmartPlaylistValue.Flag(false)).summaryOperator())
    }

    @Test fun referenceNamesDisambiguateOwnersKindsAndOtherwiseIdenticalNames() {
        val references = listOf(
            SmartPlaylistReference("a", "Favorites", "Alice"),
            SmartPlaylistReference("b", "favorites", "Bob"),
            SmartPlaylistReference("c", "Favorites", "Alice", isSmart = true),
            SmartPlaylistReference("d", "Favorites", "Alice", isSmart = true),
            SmartPlaylistReference("e", "Jazz", "Alice"),
        )
        assertEquals(SmartPlaylistSummaryReference("Favorites", "Alice"), smartPlaylistSummaryReference("a", references))
        assertEquals(SmartPlaylistSummaryReference("favorites", "Bob"), smartPlaylistSummaryReference("b", references))
        assertEquals(SmartPlaylistSummaryReference("Favorites", "Alice", true, "c"), smartPlaylistSummaryReference("c", references))
        assertEquals(SmartPlaylistSummaryReference("Jazz"), smartPlaylistSummaryReference("e", references))
        assertNull(smartPlaylistSummaryReference("missing", references))
        assertNull(smartPlaylistSummaryReference("blank", listOf(SmartPlaylistReference("blank", " "))))
    }

    @Test fun averageRatingsAreGlobalButPersonalSortPriorityIsOwnerDependent() {
        val global = SmartPlaylistDefinition("Global", rules = listOf(
            SmartPlaylistCondition(SmartPlaylistOperator.GreaterThan, SmartPlaylistFields.AverageRating, SmartPlaylistValue.Number(3)),
        ))
        assertTrue(global.ownerDependentFields().isEmpty())
        assertEquals(setOf(SmartPlaylistFields.Rating), global.copy(sort = listOf(SmartPlaylistSort(SmartPlaylistFields.Rating)))
            .ownerDependentFields())
    }
    @Test fun ownerDependenceIncludesGroupedExclusionsAndAlbumHistory() {
        val definition = SmartPlaylistDefinition("Owned", rules = listOf(
            SmartPlaylistGroup(SmartPlaylistMatch.Any, listOf(
                SmartPlaylistCondition(SmartPlaylistOperator.IsNot, SmartPlaylistFields.Loved, SmartPlaylistValue.Flag(true)),
                SmartPlaylistCondition(SmartPlaylistOperator.GreaterThan, SmartPlaylistFields.AlbumPlayCount, SmartPlaylistValue.Number(2)),
            )),
            SmartPlaylistCondition(SmartPlaylistOperator.Contains, SmartPlaylistFields.Genre, SmartPlaylistValue.Text("rock")),
        ))
        assertEquals(setOf(SmartPlaylistFields.Loved, SmartPlaylistFields.AlbumPlayCount), definition.ownerDependentFields())
    }

    @Test fun anEmptyLocalIndexNeverClaimsZeroServerMatches() {
        val preview = previewSmartPlaylist(SmartPlaylistDefinition("Local", rules = listOf(
            SmartPlaylistCondition(SmartPlaylistOperator.Contains, "title", SmartPlaylistValue.Text("song")),
        )), emptyList(), nowEpochMillis = 0L)
        assertFalse(preview.available)
        assertNull(preview.matchingTrackCount)
        assertEquals(0, preview.indexedTrackCount)
    }
}
