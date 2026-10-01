package app.naviamp.domain.media

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.SavedTrack
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumDiscSectionsTest {
    @Test
    fun sortsDiscsAndTracksAndKeepsRepeatedNumbersDistinct() {
        val tracks = listOf(track("2-2", 2, 2), track("1-2", 1, 2), track("2-1", 2, 1), track("1-1", 1, 1))
        val sections = tracks.albumDiscSections()
        assertEquals(listOf(1, 2), sections.map { it.number })
        assertEquals(listOf("1-1", "1-2", "2-1", "2-2"), tracks.inAlbumOrder().map { it.id.value })
        val selection = selectedTrackPlayback("2-1", MediaTrackLookupSources(primaryTracks = tracks.inAlbumOrder()))
        assertEquals("2-1", selection?.track?.id?.value)
        assertEquals(4, selection?.tracks?.size)
    }

    @Test
    fun incompleteMetadataPreservesEveryTrackAndStableTies() {
        val tracks = listOf(track("unknown", null, null), track("invalid", -1, 0),
            track("second", 2, 1), track("one-a", 1, 1), track("one-b", 1, 1))
        assertEquals(listOf("one-a", "one-b", "unknown", "invalid", "second"),
            tracks.inAlbumOrder().map { it.id.value })
        assertEquals(tracks.size, tracks.albumDiscSections().sumOf { it.tracks.size })
    }

    @Test
    fun singleDiscAndUntaggedAlbumsHaveOneSectionAndTrimTitles() {
        assertEquals(emptyList(), emptyList<Track>().albumDiscSections())
        val tracks = listOf(track("a", null, null), track("b", null, null))
        assertEquals(tracks, tracks.inAlbumOrder())
        assertEquals(1, tracks.albumDiscSections().size)
        assertNull(tracks.albumDiscSections().single().title)
        assertEquals("Bonus", listOf(track("bonus", 3, 1).copy(discTitle = " Bonus "))
            .albumDiscSections().single().title)
    }

    @Test
    fun savedTracksRoundTripMetadataAndOlderPayloadsDefaultToNull() {
        val original = track("bonus", 2, 7).copy(discTitle = "Bonus")
        assertEquals(original, Json.decodeFromString<SavedTrack>(Json.encodeToString(SavedTrack.fromTrack(original))).toTrack())
        val old = Json.decodeFromString<SavedTrack>("""{"id":"old","title":"Old","artistName":"Artist"}""").toTrack()
        assertNull(old.discNumber)
        assertNull(old.trackNumber)
        assertNull(old.discTitle)
    }

    private fun track(id: String, disc: Int?, number: Int?) = Track(
        id = TrackId(id), title = id, artistName = "Artist", albumTitle = "Album",
        durationSeconds = null, coverArtId = null, audioInfo = null, replayGain = null,
        discNumber = disc, trackNumber = number,
    )
}
