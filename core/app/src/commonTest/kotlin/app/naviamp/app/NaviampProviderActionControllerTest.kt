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
}

private class RecordingPendingActions : PendingProviderActionRepository {
    val enqueued = mutableListOf<String>()
    val pending = mutableListOf<PendingProviderAction>()

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
    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) = Unit
}

private fun pendingAction(id: Long, entityId: String) = PendingProviderAction(
    id = id,
    sourceId = "source",
    actionType = PendingActionReportNowPlaying,
    entityId = entityId,
    createdAtEpochMillis = 0,
)

private class RecordingProvider(private val failReports: Boolean) : MediaProvider {
    override val id = ProviderId("fake")
    override val displayName = "Fake"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)

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
    }
}
