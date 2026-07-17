package app.naviamp.app

import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PendingActionAlbumFavorite
import app.naviamp.domain.provider.PendingActionArtistFavorite
import app.naviamp.domain.provider.PendingActionReportNowPlaying
import app.naviamp.domain.provider.PendingActionTrackFavorite
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.provider.PendingProviderActionSyncResult
import app.naviamp.domain.provider.replayPendingProviderActions

/** Coordinates provider mutations that can be replayed after an offline or failed request. */
class NaviampProviderActionController(
    private val repository: PendingProviderActionRepository,
) {
    fun enqueueNowPlaying(sourceId: String, trackId: TrackId) {
        repository.enqueuePendingProviderAction(
            sourceId = sourceId,
            actionType = PendingActionReportNowPlaying,
            entityId = trackId.value,
        )
    }

    fun enqueueTrackFavorite(sourceId: String, trackId: TrackId, favorite: Boolean) {
        repository.enqueuePendingProviderAction(
            sourceId = sourceId,
            actionType = PendingActionTrackFavorite,
            entityId = trackId.value,
            boolValue = favorite,
            replaceMatchingEntityAction = true,
        )
    }

    fun offlineCapable(provider: MediaProvider, sourceId: String?): MediaProvider =
        object : MediaProvider by provider {
            override suspend fun reportNowPlaying(trackId: TrackId) {
                runOrEnqueue(sourceId, PendingActionReportNowPlaying, trackId.value) {
                    provider.reportNowPlaying(trackId)
                }
            }

            override suspend fun setTrackFavorite(trackId: TrackId, favorite: Boolean) {
                runOrEnqueue(sourceId, PendingActionTrackFavorite, trackId.value, favorite, true) {
                    provider.setTrackFavorite(trackId, favorite)
                }
            }

            override suspend fun setArtistFavorite(artistId: ArtistId, favorite: Boolean) {
                runOrEnqueue(sourceId, PendingActionArtistFavorite, artistId.value, favorite, true) {
                    provider.setArtistFavorite(artistId, favorite)
                }
            }

            override suspend fun setAlbumFavorite(albumId: AlbumId, favorite: Boolean) {
                runOrEnqueue(sourceId, PendingActionAlbumFavorite, albumId.value, favorite, true) {
                    provider.setAlbumFavorite(albumId, favorite)
                }
            }
        }

    suspend fun replay(sourceId: String, provider: MediaProvider): PendingProviderActionSyncResult =
        replayPendingProviderActions(sourceId, provider, repository)

    private suspend fun runOrEnqueue(
        sourceId: String?,
        actionType: String,
        entityId: String,
        boolValue: Boolean? = null,
        replaceMatchingEntityAction: Boolean = false,
        action: suspend () -> Unit,
    ) {
        runCatching { action() }.onFailure { error ->
            sourceId?.let { activeSourceId ->
                repository.enqueuePendingProviderAction(
                    sourceId = activeSourceId,
                    actionType = actionType,
                    entityId = entityId,
                    boolValue = boolValue,
                    replaceMatchingEntityAction = replaceMatchingEntityAction,
                )
            } ?: throw error
        }
    }
}
