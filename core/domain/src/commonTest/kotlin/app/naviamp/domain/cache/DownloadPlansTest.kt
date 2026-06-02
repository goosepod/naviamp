package app.naviamp.domain.cache

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadPlansTest {
    @Test
    fun blocksDownloadsWithoutProviderOrSource() {
        assertEquals(
            DownloadBlockReason.MissingConnection,
            planDownloadTracks(listOf(track("one")), hasProvider = false, hasSource = true).blockedReason,
        )
        assertEquals(
            DownloadBlockReason.MissingConnection,
            planDownloadTracks(listOf(track("one")), hasProvider = true, hasSource = false).blockedReason,
        )
    }

    @Test
    fun blocksEmptyDownloads() {
        val plan = planDownloadTracks(emptyList(), hasProvider = true, hasSource = true)

        assertFalse(plan.isReady)
        assertEquals(DownloadBlockReason.EmptyTracks, plan.blockedReason)
    }

    @Test
    fun blocksMobileDownloadsWhenDisabled() {
        val plan = planDownloadTracks(
            tracks = listOf(track("one")),
            hasProvider = true,
            hasSource = true,
            isActiveNetworkMobileData = true,
            allowMobileDownloads = false,
        )

        assertEquals(DownloadBlockReason.MobileDataDisabled, plan.blockedReason)
    }

    @Test
    fun returnsReadyTracksAndCanDeduplicate() {
        val tracks = listOf(track("one"), track("one"), track("two"))

        val plan = planDownloadTracks(
            tracks = tracks,
            hasProvider = true,
            hasSource = true,
            deduplicateTracks = true,
        )

        assertTrue(plan.isReady)
        assertNull(plan.blockedReason)
        assertEquals(listOf(TrackId("one"), TrackId("two")), plan.tracks.map { it.id })
    }

    @Test
    fun downloadStatusMessagesMatchSharedUiCopy() {
        assertEquals("Connect to Navidrome before downloading.", downloadConnectionRequiredStatus())
        assertEquals("Album did not return any tracks.", emptyDownloadStatus("Album"))
        assertEquals("Downloads over mobile data are disabled.", downloadMobileDataDisabledStatus())
        assertEquals("Downloading Album...", downloadStartingStatus("Album"))
        assertEquals("Downloading Album (2/4)...", downloadProgressStatus("Album", index = 1, total = 4))
        assertEquals("Downloaded Album.", downloadCompletedStatus("Album"))
        assertEquals("Downloaded Album (4 tracks).", downloadCompletedStatus("Album", completed = 4))
        assertEquals("network failed", downloadErrorStatus("Album", IllegalStateException("network failed")))
        assertEquals("Could not download Album.", downloadErrorStatus("Album", RuntimeException()))
        assertEquals("Removed One.", downloadedTrackRemovedStatus("One"))
        assertEquals("Could not remove download.", downloadRemoveErrorStatus(RuntimeException()))
    }

    @Test
    fun blockedStatusMapsReasonToMessage() {
        assertEquals(
            "Album did not return any tracks.",
            downloadBlockedStatus(DownloadBlockReason.EmptyTracks, "Album"),
        )
    }

    @Test
    fun downloadTracksForPlaybackReturnsNullForInvalidSelection() {
        assertNull(downloadTracksForPlayback(emptyList<Track>(), index = 0) { it })
        assertNull(downloadTracksForPlayback(listOf(track("one")), index = 1) { it })
    }

    @Test
    fun downloadTracksForPlaybackReturnsTrackListForValidSelection() {
        val tracks = listOf(track("one"), track("two"))

        assertEquals(tracks, downloadTracksForPlayback(tracks, index = 1) { it })
    }

    @Test
    fun downloadTracksWithStatusRunsDeduplicatedTracksWithProgress() = runTest {
        val statuses = mutableListOf<String>()
        val downloaded = mutableListOf<TrackId>()

        val result = downloadTracksWithStatus(
            label = "Playlist",
            tracks = listOf(track("one"), track("one"), track("two")),
            hasProvider = true,
            hasSource = true,
            setStatus = statuses::add,
            downloadTrack = { downloaded += it.id },
        )

        val completed = assertIs<DownloadTracksResult.Completed>(result)
        assertEquals(2, completed.completed)
        assertEquals(listOf(TrackId("one"), TrackId("two")), downloaded)
        assertEquals(
            listOf(
                "Downloading Playlist...",
                "Downloading Playlist (1/2)...",
                "Downloading Playlist (2/2)...",
                "Downloaded Playlist (2 tracks).",
            ),
            statuses,
        )
    }

    @Test
    fun downloadTracksWithStatusCanOmitCompletedCount() = runTest {
        val statuses = mutableListOf<String>()

        val result = downloadTracksWithStatus(
            label = "Track One",
            tracks = listOf(track("one")),
            hasProvider = true,
            hasSource = true,
            includeCompletedCount = false,
            setStatus = statuses::add,
            downloadTrack = {},
        )

        assertIs<DownloadTracksResult.Completed>(result)
        assertEquals(listOf("Downloading Track One...", "Downloaded Track One."), statuses)
    }

    @Test
    fun downloadTracksWithStatusReportsFailureAfterCompletedTracks() = runTest {
        val statuses = mutableListOf<String>()

        val result = downloadTracksWithStatus(
            label = "Album",
            tracks = listOf(track("one"), track("two")),
            hasProvider = true,
            hasSource = true,
            setStatus = statuses::add,
            downloadTrack = { track ->
                if (track.id.value == "two") error("network failed")
            },
        )

        val failed = assertIs<DownloadTracksResult.Failed>(result)
        assertEquals(1, failed.completed)
        assertEquals(
            listOf(
                "Downloading Album...",
                "Downloading Album (1/2)...",
                "Downloading Album (2/2)...",
                "network failed",
            ),
            statuses,
        )
    }

    @Test
    fun downloadTracksWithStatusReportsBlockedReason() = runTest {
        val statuses = mutableListOf<String>()

        val result = downloadTracksWithStatus(
            label = "Album",
            tracks = listOf(track("one")),
            hasProvider = false,
            hasSource = true,
            setStatus = statuses::add,
            downloadTrack = {},
        )

        val blocked = assertIs<DownloadTracksResult.Blocked>(result)
        assertEquals(DownloadBlockReason.MissingConnection, blocked.reason)
        assertEquals(listOf("Connect to Navidrome before downloading."), statuses)
    }

    @Test
    fun redownloadTracksWithStatusUsesSharedDownloadStatusFlow() = runTest {
        val statuses = mutableListOf<String>()
        val replaced = mutableListOf<TrackId>()

        val result = redownloadTracksWithStatus(
            tracks = listOf(track("one"), track("two")),
            hasProvider = true,
            hasSource = true,
            setStatus = statuses::add,
            replaceTrack = { replaced += it.id },
        )

        val completed = assertIs<DownloadTracksResult.Completed>(result)
        assertEquals(2, completed.completed)
        assertEquals(listOf(TrackId("one"), TrackId("two")), replaced)
        assertEquals(
            listOf(
                "Downloading downloads...",
                "Downloading downloads (1/2)...",
                "Downloading downloads (2/2)...",
                "Downloaded downloads (2 tracks).",
            ),
            statuses,
        )
        assertTrue(shouldRefreshDownloadsAfter(result))
    }

    @Test
    fun downloadServiceRedownloadsThroughReplacementRepository() = runTest {
        val statuses = mutableListOf<String>()
        val repository = RecordingDownloadRepository()
        val provider = FakeMediaProvider()

        val result = DownloadService(repository, repository).redownloadTracksWithStatus(
            sourceId = "source-one",
            provider = provider,
            tracks = listOf(track("one"), track("one"), track("two")),
            quality = StreamQuality.Original,
            maxDownloadBytes = 5000L,
            setStatus = statuses::add,
        )

        val completed = assertIs<DownloadTracksResult.Completed>(result)
        assertEquals(2, completed.completed)
        assertEquals(
            listOf(
                ReplacementCall("source-one", provider, TrackId("one"), StreamQuality.Original, 5000L),
                ReplacementCall("source-one", provider, TrackId("two"), StreamQuality.Original, 5000L),
            ),
            repository.replacementCalls,
        )
        assertEquals("Downloaded downloads (2 tracks).", statuses.last())
    }

    @Test
    fun downloadServiceBlocksWhenProviderOrSourceIsMissing() = runTest {
        val repository = RecordingDownloadRepository()
        val statuses = mutableListOf<String>()

        val result = DownloadService(repository, repository).redownloadTracksWithStatus(
            sourceId = null,
            provider = FakeMediaProvider(),
            tracks = listOf(track("one")),
            quality = StreamQuality.Original,
            maxDownloadBytes = 5000L,
            setStatus = statuses::add,
        )

        val blocked = assertIs<DownloadTracksResult.Blocked>(result)
        assertEquals(DownloadBlockReason.MissingConnection, blocked.reason)
        assertTrue(repository.downloadCalls.isEmpty())
        assertTrue(repository.replacementCalls.isEmpty())
        assertEquals(listOf("Connect to Navidrome before downloading."), statuses)
    }

    @Test
    fun downloadServiceDownloadsThroughDownloadRepository() = runTest {
        val statuses = mutableListOf<String>()
        val repository = RecordingDownloadRepository()
        val provider = FakeMediaProvider()

        val result = DownloadService(repository, repository).downloadTracksWithStatus(
            sourceId = "source-one",
            provider = provider,
            tracks = listOf(track("one"), track("one"), track("two")),
            quality = StreamQuality.Original,
            maxDownloadBytes = 5000L,
            label = "Playlist",
            setStatus = statuses::add,
        )

        val completed = assertIs<DownloadTracksResult.Completed>(result)
        assertEquals(2, completed.completed)
        assertEquals(
            listOf(
                DownloadCall("source-one", provider, TrackId("one"), StreamQuality.Original, 5000L),
                DownloadCall("source-one", provider, TrackId("two"), StreamQuality.Original, 5000L),
            ),
            repository.downloadCalls,
        )
        assertEquals("Downloaded Playlist (2 tracks).", statuses.last())
    }

    @Test
    fun shouldRefreshDownloadsAfterIgnoresBlockedAndEmptyFailures() {
        assertFalse(shouldRefreshDownloadsAfter(DownloadTracksResult.Blocked(DownloadBlockReason.MissingConnection)))
        assertFalse(shouldRefreshDownloadsAfter(DownloadTracksResult.Failed(0, RuntimeException())))
        assertTrue(shouldRefreshDownloadsAfter(DownloadTracksResult.Failed(1, RuntimeException())))
    }

    private fun track(id: String, title: String = id): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )

    private data class ReplacementCall(
        val sourceId: String,
        val provider: MediaProvider,
        val trackId: TrackId,
        val quality: StreamQuality,
        val maxDownloadBytes: Long,
    )

    private data class DownloadCall(
        val sourceId: String,
        val provider: MediaProvider,
        val trackId: TrackId,
        val quality: StreamQuality,
        val maxDownloadBytes: Long,
    )

    private class RecordingDownloadRepository : DownloadRepository<Unit, Track>, DownloadReplacementRepository<Unit> {
        val downloadCalls = mutableListOf<DownloadCall>()
        val replacementCalls = mutableListOf<ReplacementCall>()

        override suspend fun downloadedAudioFile(
            sourceId: String,
            trackId: TrackId,
            quality: StreamQuality,
        ) {
        }

        override suspend fun downloadedAudioFile(
            sourceId: String,
            trackId: TrackId,
        ) {
        }

        override suspend fun downloadAudioTrack(
            sourceId: String,
            provider: MediaProvider,
            track: Track,
            quality: StreamQuality,
            maxDownloadBytes: Long,
        ) {
            downloadCalls += DownloadCall(sourceId, provider, track.id, quality, maxDownloadBytes)
        }

        override fun downloadedTracks(sourceId: String): List<Track> =
            emptyList()

        override fun removeDownloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) {
        }

        override fun removeDownloadedAudio(sourceId: String, trackId: TrackId) {
        }

        override suspend fun replaceDownloadedAudioTrack(
            sourceId: String,
            provider: MediaProvider,
            track: Track,
            quality: StreamQuality,
            maxDownloadBytes: Long,
        ) {
            replacementCalls += ReplacementCall(sourceId, provider, track.id, quality, maxDownloadBytes)
        }
    }

    private class FakeMediaProvider : MediaProvider {
        override val id: ProviderId = ProviderId("fake")
        override val displayName: String = "Fake"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
        )

        override suspend fun validateConnection(): ConnectionValidation =
            error("unused")

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
            error("unused")

        override suspend fun album(albumId: AlbumId): AlbumDetails =
            error("unused")

        override suspend fun artist(artistId: ArtistId): ArtistDetails =
            error("unused")

        override suspend fun artists(limit: Int): List<Artist> =
            error("unused")

        override suspend fun tracks(limit: Int): List<Track> =
            error("unused")

        override suspend fun search(query: String, limit: Int): MediaSearchResults =
            error("unused")

        override suspend fun streamUrl(request: StreamRequest): String =
            error("unused")

        override fun coverArtUrl(coverArtId: String): String =
            error("unused")
    }
}
