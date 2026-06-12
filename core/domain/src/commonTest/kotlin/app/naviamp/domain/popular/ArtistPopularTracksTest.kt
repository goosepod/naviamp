package app.naviamp.domain.popular

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtistPopularTracksTest {
    @Test
    fun matchesRemasteredPopularTrackToLibraryTitle() {
        val matches = matchPopularTracks(
            candidates = listOf(
                ArtistPopularTrackCandidate(
                    source = NavidromeAgentMetadataSource,
                    sourceTrackId = "source-1",
                    rank = 1,
                    title = "Symphony Of Destruction",
                    albumTitle = "Countdown To Extinction (Deluxe Edition)",
                    durationSeconds = 243,
                ),
            ),
            libraryTracks = listOf(
                track(
                    id = "local-1",
                    title = "Symphony Of Destruction (Remastered 2012)",
                    albumTitle = "Countdown To Extinction",
                    durationSeconds = 246,
                ),
            ),
        )

        assertEquals("local-1", matches["source-1"]?.id?.value)
    }

    @Test
    fun matchesPopularTrackWithSubtitleToPlainLibraryTitle() {
        val matches = matchPopularTracks(
            candidates = listOf(
                ArtistPopularTrackCandidate(
                    source = NavidromeAgentMetadataSource,
                    sourceTrackId = "source-2",
                    rank = 2,
                    title = "Holy Wars...The Punishment Due",
                    albumTitle = "Rust In Peace (2004 Remix / Expanded Edition)",
                    durationSeconds = 390,
                ),
            ),
            libraryTracks = listOf(
                track(
                    id = "local-2",
                    title = "Holy Wars... The Punishment Due",
                    albumTitle = "Rust in Peace",
                    durationSeconds = 393,
                ),
            ),
        )

        assertEquals("local-2", matches["source-2"]?.id?.value)
    }

    private fun track(
        id: String,
        title: String,
        albumTitle: String,
        durationSeconds: Int,
    ): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistName = "Megadeth",
            albumTitle = albumTitle,
            durationSeconds = durationSeconds,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
