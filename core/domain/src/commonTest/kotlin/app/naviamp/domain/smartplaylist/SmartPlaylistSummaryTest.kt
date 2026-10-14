package app.naviamp.domain.smartplaylist

import kotlin.test.*

class SmartPlaylistSummaryTest {
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
