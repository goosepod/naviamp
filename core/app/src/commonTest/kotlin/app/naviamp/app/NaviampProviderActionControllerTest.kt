package app.naviamp.app

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
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.PendingActionReportNowPlaying
import app.naviamp.domain.provider.PendingActionTrackFavorite
import app.naviamp.domain.provider.PendingProviderAction
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampProviderActionControllerTest {
    @Test
    fun failedOfflineCapableMutationIsQueuedWithSharedPolicy() = runTest {
        val repository = RecordingPendingActions()
        val provider = RecordingProvider(failReports = true)
        val controller = NaviampProviderActionController(repository)

        controller.offlineCapable(provider, "source").reportNowPlaying(TrackId("track"))

        assertEquals(listOf("source:$PendingActionReportNowPlaying:track:null:false"), repository.enqueued)
    }

    @Test
    fun offlineCapableProviderForwardsPlaylistTrackReplacement() = runTest {
        val provider = RecordingProvider(failReports = false)
        val wrapped = NaviampProviderActionController(RecordingPendingActions())
            .offlineCapable(provider, "source")

        wrapped.replacePlaylistTracks(
            playlistId = "playlist",
            currentTrackIds = listOf(TrackId("old")),
            trackIds = listOf(TrackId("new")),
        )

        assertEquals(listOf("playlist:old:new"), provider.playlistReplacements)
    }

    @Test
    fun explicitOfflineActionsUseTheSameGraphOwnedQueuePolicy() {
        val repository = RecordingPendingActions()
        val controller = NaviampProviderActionController(repository)

        controller.enqueueNowPlaying("source", TrackId("track"))
        controller.enqueueTrackFavorite("source", TrackId("track"), favorite = true)

        assertEquals(
            listOf(
                "source:$PendingActionReportNowPlaying:track:null:false",
                "source:$PendingActionTrackFavorite:track:true:true",
            ),
            repository.enqueued,
        )
    }

    @Test
    fun replayPublishesSharedSuccessAndFailureStatusPolicy() = runTest {
        val applicationStatus = NaviampApplicationStatusController()
        val repository = RecordingPendingActions().apply {
            pending += pendingAction(id = 1, entityId = "first")
            pending += pendingAction(id = 2, entityId = "second")
        }
        val controller = NaviampProviderActionController(repository, applicationStatus)

        val result = controller.replay("source", RecordingProvider(failReports = true))

        assertEquals(2, result.failed)
        assertEquals(NaviampApplicationStatusArea.ProviderActions, applicationStatus.state.value?.area)
        assertEquals(NaviampApplicationStatusLevel.Warning, applicationStatus.state.value?.level)
        assertEquals(
            "Could not sync 2 offline actions; they remain pending.",
            applicationStatus.state.value?.message,
        )
    }

    @Test
    fun replayWithNoPendingActionsDoesNotPublishStatus() = runTest {
        val applicationStatus = NaviampApplicationStatusController()
        val controller = NaviampProviderActionController(RecordingPendingActions(), applicationStatus)

        controller.replay("source", RecordingProvider(failReports = false))

        assertNull(applicationStatus.state.value)
    }

    @Test
    fun successfulReplayDeliversAndRemovesThePendingAction() = runTest {
        val repository = RecordingPendingActions().apply {
            pending += pendingAction(id = 1, entityId = "track")
        }
        val provider = RecordingProvider(failReports = false)

        val result = NaviampProviderActionController(repository).replay("source", provider)

        assertEquals(1, result.completed)
        assertEquals(listOf("track"), provider.nowPlayingReports)
        assertEquals(emptyList<PendingProviderAction>(), repository.pending)
    }

    @Test
    fun replayIsSourceScopedAndLeavesFailuresPendingWithTheirError() = runTest {
        val repository = RecordingPendingActions().apply {
            pending += pendingAction(id = 1, entityId = "current")
            pending += pendingAction(id = 2, entityId = "other", sourceId = "other-source")
        }
        val controller = NaviampProviderActionController(repository)

        val result = controller.replay("source", RecordingProvider(failReports = true))

        assertEquals(1, result.attempted)
        assertEquals(1, result.failed)
        assertEquals(listOf<Pair<Long, String?>>(1L to "offline"), repository.failures)
        assertEquals(listOf(1L, 2L), repository.pending.map { it.id })
    }
}

internal class RecordingPendingActions : PendingProviderActionRepository {
    val enqueued = mutableListOf<String>()
    val pending = mutableListOf<PendingProviderAction>()
    val failures = mutableListOf<Pair<Long, String?>>()

    override fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean?,
        longValue: Long?,
        replaceMatchingEntityAction: Boolean,
    ) {
        enqueued += "$sourceId:$actionType:$entityId:$boolValue:$replaceMatchingEntityAction"
    }

    override fun pendingProviderActions(sourceId: String, limit: Int): List<PendingProviderAction> =
        pending.filter { it.sourceId == sourceId }.take(limit)

    override fun deletePendingProviderAction(id: Long) {
        pending.removeAll { it.id == id }
    }
    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) {
        failures += id to errorMessage
    }
}

internal fun pendingAction(
    id: Long,
    entityId: String,
    sourceId: String = "source",
) = PendingProviderAction(
    id = id,
    sourceId = sourceId,
    actionType = PendingActionReportNowPlaying,
    entityId = entityId,
    createdAtEpochMillis = 0,
)

internal class RecordingProvider(private val failReports: Boolean) : MediaProvider {
    override val id = ProviderId("fake")
    override val displayName = "Fake"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    val nowPlayingReports = mutableListOf<String>()
    val playlistReplacements = mutableListOf<String>()

    override suspend fun validateConnection(): ConnectionValidation = error("Not used")
    override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = error("Not used")
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int): List<Artist> = error("Not used")
    override suspend fun tracks(limit: Int): List<Track> = error("Not used")
    override suspend fun search(query: String, limit: Int): MediaSearchResults = error("Not used")
    override suspend fun streamUrl(request: StreamRequest): String = error("Not used")
    override fun coverArtUrl(coverArtId: String): String = error("Not used")

    override suspend fun reportNowPlaying(trackId: TrackId) {
        if (failReports) error("offline")
        nowPlayingReports += trackId.value
    }

    override suspend fun replacePlaylistTracks(
        playlistId: String,
        currentTrackIds: List<TrackId>,
        trackIds: List<TrackId>,
    ) {
        playlistReplacements += "$playlistId:" +
            currentTrackIds.joinToString(",") { it.value } + ":" +
            trackIds.joinToString(",") { it.value }
    }
}
