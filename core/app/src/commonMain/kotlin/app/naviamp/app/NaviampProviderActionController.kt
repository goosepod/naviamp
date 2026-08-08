package app.naviamp.app

import app.naviamp.domain.AlbumId
import app.naviamp.domain.AlbumInfo
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
    private val applicationStatus: NaviampApplicationStatusController? = null,
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
            override suspend fun albumInfo(albumId: AlbumId): AlbumInfo? =
                provider.albumInfo(albumId)

            override suspend fun replacePlaylistTracks(
                playlistId: String,
                currentTrackIds: List<TrackId>,
                trackIds: List<TrackId>,
            ) {
                provider.replacePlaylistTracks(playlistId, currentTrackIds, trackIds)
            }

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

    suspend fun replay(sourceId: String, provider: MediaProvider): PendingProviderActionSyncResult {
        val result = replayPendingProviderActions(sourceId, provider, repository)
        providerActionSyncStatus(result)?.let { status ->
            applicationStatus?.publish(
                area = NaviampApplicationStatusArea.ProviderActions,
                level = if (result.failed > 0) {
                    NaviampApplicationStatusLevel.Warning
                } else {
                    NaviampApplicationStatusLevel.Information
                },
                message = status,
            )
        }
        return result
    }

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

fun providerActionSyncStatus(result: PendingProviderActionSyncResult): String? =
    when {
        result.attempted == 0 -> null
        result.completed > 0 && result.failed == 0 ->
            "Synced ${result.completed} offline ${result.completed.providerActionLabel()}."
        result.completed > 0 ->
            "Synced ${result.completed} offline ${result.completed.providerActionLabel()}; " +
                "${result.failed} still pending."
        else ->
            "Could not sync ${result.failed} offline ${result.failed.providerActionLabel()}; " +
                if (result.failed == 1) "it remains pending." else "they remain pending."
    }

private fun Int.providerActionLabel(): String = if (this == 1) "action" else "actions"
