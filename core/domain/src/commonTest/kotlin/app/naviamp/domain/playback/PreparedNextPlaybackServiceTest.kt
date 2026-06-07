package app.naviamp.domain.playback

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Lyrics
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.queue.PlaybackQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class PreparedNextPlaybackServiceTest {
    @Test
    fun buildsPreparedNextRequestFromLocalAudioPlan() = runTest {
        val nextTrack = track("next")
        val plan = planPrepareNextQueuePlayback(
            queue = PlaybackQueue(tracks = listOf(track("current"), nextTrack), currentIndex = 0),
            progress = PlaybackProgress(positionSeconds = 95.0, durationSeconds = 100.0),
            nextQueueIndex = 1,
            alreadyPreparedNext = false,
            gaplessEnabled = true,
            supportsGapless = true,
            crossfadeDurationSeconds = 0,
            supportsCrossfade = true,
            gaplessPrepareWindowSeconds = 8.0,
        ) ?: error("expected prepare plan")

        val prepared = preparedNextPlaybackRequest(
            plan = plan,
            provider = FakeMediaProvider(),
            sourceId = "source",
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            audioAssets = RecordingAudioAssets(localAudio("cache/next.flac")),
            replayGainMode = ReplayGainMode.Album,
            supportsReplayGain = false,
            replayGainForTrack = { _, _ -> null },
        )

        assertEquals(1, prepared.nextQueueIndex)
        assertEquals("next", prepared.track.id.value)
        assertEquals("file://cache/next.flac", prepared.request.url)
        assertEquals("next", prepared.request.mediaId)
        assertEquals(ReplayGainMode.Off, prepared.request.replayGainMode)
    }

    @Test
    fun coordinatorPlansAndBuildsPreparedNextRequest() = runTest {
        val nextTrack = track("next")
        val coordinator = PreparedNextPlaybackCoordinator(
            provider = { FakeMediaProvider() },
            sourceId = { "source" },
            quality = { StreamQuality.Original },
            audioCachingEnabled = { true },
            audioAssets = RecordingAudioAssets(localAudio("cache/next.flac")),
            replayGainMode = { ReplayGainMode.Track },
            supportsReplayGain = { true },
            replayGainForTrack = { _, _ -> null },
        )
        val plan = coordinator.plan(
            queue = PlaybackQueue(tracks = listOf(track("current"), nextTrack), currentIndex = 0),
            progress = PlaybackProgress(positionSeconds = 99.0, durationSeconds = 100.0),
            nextQueueIndex = 1,
            preparedNextIndex = null,
            settings = PreparedNextPlaybackSettings(
                gaplessEnabled = true,
                supportsGapless = true,
                crossfadeDurationSeconds = 0,
                supportsCrossfade = true,
                gaplessPrepareWindowSeconds = 8.0,
            ),
        ) ?: error("expected prepare plan")

        val prepared = coordinator.request(plan)

        assertEquals(1, prepared?.nextQueueIndex)
        assertEquals("next", prepared?.track?.id?.value)
        assertEquals("file://cache/next.flac", prepared?.request?.url)
        assertEquals(ReplayGainMode.Track, prepared?.request?.replayGainMode)
    }

    @Test
    fun coordinatorSkipsAlreadyPreparedNextIndex() {
        val coordinator = PreparedNextPlaybackCoordinator(
            provider = { FakeMediaProvider() },
            sourceId = { "source" },
            quality = { StreamQuality.Original },
            audioCachingEnabled = { true },
            audioAssets = RecordingAudioAssets(localAudio("cache/next.flac")),
            replayGainMode = { ReplayGainMode.Track },
            supportsReplayGain = { true },
            replayGainForTrack = { _, _ -> null },
        )

        val plan = coordinator.plan(
            queue = PlaybackQueue(tracks = listOf(track("current"), track("next")), currentIndex = 0),
            progress = PlaybackProgress(positionSeconds = 99.0, durationSeconds = 100.0),
            nextQueueIndex = 1,
            preparedNextIndex = 1,
            settings = PreparedNextPlaybackSettings(
                gaplessEnabled = true,
                supportsGapless = true,
                crossfadeDurationSeconds = 0,
                supportsCrossfade = true,
                gaplessPrepareWindowSeconds = 8.0,
            ),
        )

        assertNull(plan)
    }
}

private class RecordingAudioAssets(
    private val localAudio: PlaybackLocalAudio,
) : PlaybackAudioAssetRepository {
    override suspend fun downloadedAudio(sourceId: String, trackId: TrackId): PlaybackLocalAudio? = localAudio

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? = localAudio

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? = null
}

private fun localAudio(path: String): PlaybackLocalAudio =
    PlaybackLocalAudio(
        path = path,
        uri = "file://$path",
    )

private fun track(id: String): Track =
    Track(
        id = TrackId(id),
        title = id,
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 100,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )

private class FakeMediaProvider : MediaProvider {
    override val id: ProviderId = ProviderId("fake")
    override val displayName: String = "Fake"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsStreamingTranscode = true,
        supportsDownloadTranscode = true,
        supportsArtistRadio = false,
        supportsAlbumRadio = false,
        supportsTrackRadio = false,
    )

    override suspend fun validateConnection(): ConnectionValidation = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = emptyList()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int): List<Artist> = emptyList()
    override suspend fun tracks(limit: Int): List<Track> = emptyList()
    override suspend fun search(query: String, limit: Int): MediaSearchResults = MediaSearchResults()
    override suspend fun lyrics(trackId: TrackId): Lyrics? = null
    override suspend fun streamUrl(request: StreamRequest): String = "https://example.test/stream/${request.trackId.value}"
    override fun coverArtUrl(coverArtId: String): String = "https://example.test/cover/$coverArtId"
}
