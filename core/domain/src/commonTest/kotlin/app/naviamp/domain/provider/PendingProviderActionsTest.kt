package app.naviamp.domain.provider

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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingProviderActionsTest {
    @Test
    fun replayAppliesPendingActionsAndDeletesSuccessfulRows() = runTest {
        val repository = FakePendingProviderActionRepository(
            pendingActions = listOf(
                pendingAction(1, PendingActionReportNowPlaying, "track-1"),
                pendingAction(3, PendingActionTrackFavorite, "track-1", boolValue = true),
                pendingAction(4, PendingActionArtistFavorite, "artist-1", boolValue = false),
                pendingAction(5, PendingActionAlbumFavorite, "album-1", boolValue = true),
            ),
        )
        val provider = RecordingMediaProvider()

        val result = replayPendingProviderActions("source", provider, repository)

        assertEquals(PendingProviderActionSyncResult(attempted = 4, completed = 4, failed = 0), result)
        assertEquals(
            listOf(
                "now-playing:track-1",
                "track-favorite:track-1:true",
                "artist-favorite:artist-1:false",
                "album-favorite:album-1:true",
            ),
            provider.calls,
        )
        assertEquals(listOf(1L, 3L, 4L, 5L), repository.deletedActionIds)
        assertEquals(emptyList(), repository.failedActionIds)
    }

    @Test
    fun replayKeepsFailedRowsPending() = runTest {
        val repository = FakePendingProviderActionRepository(
            pendingActions = listOf(
                pendingAction(1, PendingActionTrackFavorite, "track-1", boolValue = true),
                pendingAction(2, PendingActionAlbumFavorite, "album-1", boolValue = true),
            ),
        )
        val provider = RecordingMediaProvider(failAlbumFavorite = true)

        val result = replayPendingProviderActions("source", provider, repository)

        assertEquals(PendingProviderActionSyncResult(attempted = 2, completed = 1, failed = 1), result)
        assertEquals(listOf(1L), repository.deletedActionIds)
        assertEquals(listOf(2L), repository.failedActionIds)
    }
}

private fun pendingAction(
    id: Long,
    actionType: String,
    entityId: String,
    boolValue: Boolean? = null,
    longValue: Long? = null,
): PendingProviderAction =
    PendingProviderAction(
        id = id,
        sourceId = "source",
        actionType = actionType,
        entityId = entityId,
        boolValue = boolValue,
        longValue = longValue,
        createdAtEpochMillis = id,
        lastAttemptAtEpochMillis = null,
        attemptCount = 0,
        lastError = null,
    )

private class FakePendingProviderActionRepository(
    private val pendingActions: List<PendingProviderAction>,
) : PendingProviderActionRepository {
    val deletedActionIds = mutableListOf<Long>()
    val failedActionIds = mutableListOf<Long>()

    override fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean?,
        longValue: Long?,
        replaceMatchingEntityAction: Boolean,
    ) = Unit

    override fun pendingProviderActions(sourceId: String, limit: Int): List<PendingProviderAction> =
        pendingActions.take(limit)

    override fun deletePendingProviderAction(id: Long) {
        deletedActionIds += id
    }

    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) {
        failedActionIds += id
    }
}

private class RecordingMediaProvider(
    private val failAlbumFavorite: Boolean = false,
) : MediaProvider {
    val calls = mutableListOf<String>()

    override val id = ProviderId("fake")
    override val displayName = "Fake"
    override val capabilities = ProviderCapabilities(
        supportsStreamingTranscode = false,
        supportsDownloadTranscode = false,
        supportsArtistRadio = false,
        supportsAlbumRadio = false,
        supportsTrackRadio = false,
        supportsTrackFavorites = true,
        supportsArtistFavorites = true,
        supportsAlbumFavorites = true,
        supportsPlayReporting = true,
    )

    override suspend fun validateConnection(): ConnectionValidation = error("Not used.")
    override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = error("Not used.")
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used.")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used.")
    override suspend fun artists(limit: Int): List<Artist> = error("Not used.")
    override suspend fun tracks(limit: Int): List<Track> = error("Not used.")
    override suspend fun search(query: String, limit: Int): MediaSearchResults = error("Not used.")
    override suspend fun streamUrl(request: StreamRequest): String = error("Not used.")
    override fun coverArtUrl(coverArtId: String): String = error("Not used.")

    override suspend fun reportNowPlaying(trackId: TrackId) {
        calls += "now-playing:${trackId.value}"
    }

    override suspend fun setTrackFavorite(trackId: TrackId, favorite: Boolean) {
        calls += "track-favorite:${trackId.value}:$favorite"
    }

    override suspend fun setArtistFavorite(artistId: ArtistId, favorite: Boolean) {
        calls += "artist-favorite:${artistId.value}:$favorite"
    }

    override suspend fun setAlbumFavorite(albumId: AlbumId, favorite: Boolean) {
        if (failAlbumFavorite) {
            error("Album favorite failed.")
        }
        calls += "album-favorite:${albumId.value}:$favorite"
    }
}
