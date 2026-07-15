package app.naviamp.desktop

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.downloadedTrackRemovedStatus
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopDownloadMutationsTest {
    @Test
    fun downloadTracksForPlaybackReturnsNullForInvalidSelection() {
        assertNull(desktopDownloadTracksForPlayback(emptyList(), index = 0))
        assertNull(desktopDownloadTracksForPlayback(listOf(download("one")), index = 1))
    }

    @Test
    fun downloadTracksForPlaybackReturnsTrackListForValidSelection() {
        val downloads = listOf(download("one"), download("two"))

        assertEquals(
            downloads.map { it.track },
            desktopDownloadTracksForPlayback(downloads, index = 1),
        )
    }

    @Test
    fun downloadedTrackRemovedStatusUsesTrackTitle() {
        assertEquals("Removed One.", downloadedTrackRemovedStatus(download("one", title = "One").track.title))
    }

    private fun download(id: String, title: String = id): DownloadedTrack =
        DownloadedTrack(
            track = Track(
                id = TrackId(id),
                title = title,
                artistName = "Artist",
                albumTitle = "Album",
                durationSeconds = 180,
                coverArtId = null,
                audioInfo = null,
                replayGain = null,
            ),
            path = Path.of("/tmp/$id.mp3"),
            sizeBytes = 123L,
            contentType = "audio/mpeg",
            qualityKey = "original",
            downloadedAtEpochMillis = 1L,
        )
}
