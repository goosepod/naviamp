package app.naviamp.desktop

import app.naviamp.app.NaviampPlaybackReportingController
import app.naviamp.app.NaviampProviderActionController
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.PendingProviderAction
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.provider.PlaybackReportState
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPlaybackReportingAdapterTest {
    @Test
    fun executesEligibleReportsThroughTheDesktopHostBoundary() = runTest {
        val provider = RecordingDesktopReportProvider()
        val track = track("track-1")
        val progress = PlaybackProgress(positionSeconds = 12.5, durationSeconds = 60.0)
        val adapter = DesktopPlaybackReportingAdapter(
            scope = this,
            provider = { provider },
            sourceId = { "source-1" },
            providerActions = NaviampProviderActionController(NoOpPendingActions),
            reporting = NaviampPlaybackReportingController(),
            playbackProgress = { progress },
            nowPlayingTrack = { track },
            playReportSessionId = { 7 },
            nowEpochMillis = { 20_000L },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        adapter.reportNowPlaying(track)
        adapter.maybeReportPlaybackState(PlaybackState.Playing)
        advanceUntilIdle()

        assertEquals(listOf(track.id), provider.nowPlayingReports)
        assertEquals(
            listOf(Triple<TrackId, PlaybackReportState, Double?>(track.id, PlaybackReportState.Playing, 12.5)),
            provider.stateReports,
        )
    }

    @Test
    fun skipsReportsWhenTheProviderDoesNotSupportThem() = runTest {
        val provider = RecordingDesktopReportProvider(supportsPlayReporting = false)
        val track = track("track-1")
        val adapter = DesktopPlaybackReportingAdapter(
            scope = this,
            provider = { provider },
            sourceId = { "source-1" },
            providerActions = NaviampProviderActionController(NoOpPendingActions),
            reporting = NaviampPlaybackReportingController(),
            playbackProgress = { PlaybackProgress.Unknown },
            nowPlayingTrack = { track },
            playReportSessionId = { 7 },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        adapter.reportNowPlaying(track)
        adapter.maybeReportPlaybackState(PlaybackState.Playing)
        advanceUntilIdle()

        assertEquals(emptyList(), provider.nowPlayingReports)
        assertEquals(emptyList(), provider.stateReports)
    }

    private fun track(id: String) = Track(
        id = TrackId(id),
        title = id,
        artistName = "Artist",
        albumTitle = null,
        durationSeconds = 60,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
}

private class RecordingDesktopReportProvider(
    supportsPlayReporting: Boolean = true,
) : MediaProvider {
    override val id = ProviderId("fake")
    override val displayName = "Fake"
    override val capabilities = ProviderCapabilities(
        supportsStreamingTranscode = false,
        supportsDownloadTranscode = false,
        supportsArtistRadio = false,
        supportsAlbumRadio = false,
        supportsTrackRadio = false,
        supportsPlayReporting = supportsPlayReporting,
    )
    val nowPlayingReports = mutableListOf<TrackId>()
    val stateReports = mutableListOf<Triple<TrackId, PlaybackReportState, Double?>>()

    override suspend fun reportNowPlaying(trackId: TrackId) {
        nowPlayingReports += trackId
    }

    override suspend fun reportPlaybackState(
        trackId: TrackId,
        state: PlaybackReportState,
        positionSeconds: Double?,
    ) {
        stateReports += Triple(trackId, state, positionSeconds)
    }

    override suspend fun validateConnection(): ConnectionValidation = error("Not used")
    override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = error("Not used")
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int): List<Artist> = error("Not used")
    override suspend fun tracks(limit: Int): List<Track> = error("Not used")
    override suspend fun search(query: String, limit: Int): MediaSearchResults = error("Not used")
    override suspend fun streamUrl(request: StreamRequest): String = error("Not used")
    override fun coverArtUrl(coverArtId: String): String = error("Not used")
}

private object NoOpPendingActions : PendingProviderActionRepository {
    override fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean?,
        longValue: Long?,
        replaceMatchingEntityAction: Boolean,
    ) = Unit

    override fun pendingProviderActions(sourceId: String, limit: Int): List<PendingProviderAction> = emptyList()
    override fun deletePendingProviderAction(id: Long) = Unit
    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) = Unit
}
