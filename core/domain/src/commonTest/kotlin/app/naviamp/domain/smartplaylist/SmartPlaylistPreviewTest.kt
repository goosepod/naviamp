package app.naviamp.domain.smartplaylist

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartPlaylistPreviewTest {
    @Test
    fun previewsNestedGenreAliasesAndAppliesTheLimit() {
        val definition = SmartPlaylistDefinition(
            name = "Hip Hop",
            rules = listOf(
                SmartPlaylistGroup(
                    match = SmartPlaylistMatch.Any,
                    rules = listOf("Rap", "Hip-Hop").map { name ->
                        SmartPlaylistCondition(
                            SmartPlaylistOperator.Is,
                            SmartPlaylistFields.Genre,
                            SmartPlaylistValue.Text(name),
                        )
                    },
                ),
            ),
            limit = 1,
        )

        val preview = previewSmartPlaylist(
            definition = definition,
            tracks = listOf(
                track("rap", "First", listOf("Rap")),
                track("hip-hop", "Second", listOf("Hip-Hop")),
                track("jazz", "Third", listOf("Jazz")),
            ),
            nowEpochMillis = 1_000_000L,
        )

        assertTrue(preview.available)
        assertEquals(2, preview.matchingTrackCount)
        assertEquals(1, preview.resultTrackCount)
        assertEquals(listOf("First"), preview.exampleTracks.map(Track::title))
    }

    @Test
    fun reportsFieldsThatTheSyncedTrackIndexCannotEvaluate() {
        val preview = previewSmartPlaylist(
            definition = SmartPlaylistDefinition(
                name = "Lyrics",
                rules = listOf(
                    SmartPlaylistCondition(
                        SmartPlaylistOperator.Contains,
                        SmartPlaylistFields.Lyrics,
                        SmartPlaylistValue.Text("moon"),
                    ),
                ),
            ),
            tracks = listOf(track("1", "Song", emptyList())),
            nowEpochMillis = 1_000_000L,
        )

        assertFalse(preview.available)
        assertEquals(listOf(SmartPlaylistFields.Lyrics), preview.unsupportedFields)
    }

    private fun track(id: String, title: String, genres: List<String>) = Track(
        id = TrackId(id),
        title = title,
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
        genres = genres,
    )
}
