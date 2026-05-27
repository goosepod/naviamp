package app.naviamp.desktop

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopDownloadMutationsTest {
    @Test
    fun downloadStatusMessagesMatchDesktopUiCopy() {
        assertEquals("Connect to Navidrome before downloading.", downloadConnectionRequiredStatus())
        assertEquals("Album did not return any tracks.", emptyDownloadStatus("Album"))
        assertEquals("Downloading Album...", downloadStartingStatus("Album"))
        assertEquals("Downloading Album (2/4)...", downloadProgressStatus("Album", index = 1, total = 4))
        assertEquals("Downloaded Album (4 tracks).", downloadCompletedStatus("Album", completed = 4))
        assertEquals(
            "network failed",
            downloadErrorStatus("Album", IllegalStateException("network failed")),
        )
    }

    @Test
    fun downloadTracksForPlaybackReturnsNullForInvalidSelection() {
        assertNull(downloadTracksForPlayback(emptyList(), index = 0))
        assertNull(downloadTracksForPlayback(listOf(download("one")), index = 1))
    }

    @Test
    fun downloadTracksForPlaybackReturnsTrackListForValidSelection() {
        val downloads = listOf(download("one"), download("two"))

        assertEquals(
            downloads.map { it.track },
            downloadTracksForPlayback(downloads, index = 1),
        )
    }

    @Test
    fun downloadedTrackRemovedStatusUsesTrackTitle() {
        assertEquals("Removed One.", downloadedTrackRemovedStatus(download("one", title = "One")))
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
            downloadedAtEpochMillis = 1L,
        )
}
