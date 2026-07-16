package app.naviamp.android

import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PendingActionAlbumFavorite
import app.naviamp.domain.provider.PendingActionArtistFavorite
import app.naviamp.domain.provider.PendingActionReportNowPlaying
import app.naviamp.domain.provider.PendingActionReportPlayed
import app.naviamp.domain.provider.PendingActionTrackFavorite
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.provider.replayPendingProviderActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MediaProvider.withAndroidPendingActions(
    sourceId: String?,
    repository: PendingProviderActionRepository,
): MediaProvider {
    val delegate = this
    return object : MediaProvider by delegate {
        override suspend fun reportNowPlaying(trackId: TrackId) {
            runCatching {
                delegate.reportNowPlaying(trackId)
            }.onFailure {
                sourceId?.let { activeSourceId ->
                    repository.enqueuePendingProviderAction(
                        sourceId = activeSourceId,
                        actionType = PendingActionReportNowPlaying,
                        entityId = trackId.value,
                    )
                } ?: throw it
            }
        }

        override suspend fun reportPlayed(trackId: TrackId, playedAtEpochMillis: Long, positionSeconds: Double?) {
            runCatching {
                delegate.reportPlayed(trackId, playedAtEpochMillis, positionSeconds)
            }.onFailure {
                sourceId?.let { activeSourceId ->
                    repository.enqueuePendingProviderAction(
                        sourceId = activeSourceId,
                        actionType = PendingActionReportPlayed,
                        entityId = trackId.value,
                        longValue = playedAtEpochMillis,
                    )
                } ?: throw it
            }
        }

        override suspend fun setTrackFavorite(trackId: TrackId, favorite: Boolean) {
            runCatching {
                delegate.setTrackFavorite(trackId, favorite)
            }.onFailure {
                sourceId?.let { activeSourceId ->
                    repository.enqueuePendingProviderAction(
                        sourceId = activeSourceId,
                        actionType = PendingActionTrackFavorite,
                        entityId = trackId.value,
                        boolValue = favorite,
                        replaceMatchingEntityAction = true,
                    )
                } ?: throw it
            }
        }

        override suspend fun setArtistFavorite(artistId: ArtistId, favorite: Boolean) {
            runCatching {
                delegate.setArtistFavorite(artistId, favorite)
            }.onFailure {
                sourceId?.let { activeSourceId ->
                    repository.enqueuePendingProviderAction(
                        sourceId = activeSourceId,
                        actionType = PendingActionArtistFavorite,
                        entityId = artistId.value,
                        boolValue = favorite,
                        replaceMatchingEntityAction = true,
                    )
                } ?: throw it
            }
        }

        override suspend fun setAlbumFavorite(albumId: AlbumId, favorite: Boolean) {
            runCatching {
                delegate.setAlbumFavorite(albumId, favorite)
            }.onFailure {
                sourceId?.let { activeSourceId ->
                    repository.enqueuePendingProviderAction(
                        sourceId = activeSourceId,
                        actionType = PendingActionAlbumFavorite,
                        entityId = albumId.value,
                        boolValue = favorite,
                        replaceMatchingEntityAction = true,
                    )
                } ?: throw it
            }
        }
    }
}

internal fun syncAndroidPendingProviderActions(
    scope: CoroutineScope,
    sourceId: String,
    provider: MediaProvider,
    repository: PendingProviderActionRepository,
    setStatus: (String) -> Unit = {},
) {
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            replayPendingProviderActions(
                sourceId = sourceId,
                provider = provider,
                repository = repository,
            )
        }
        when {
            result.completed > 0 && result.failed == 0 -> setStatus("Synced ${result.completed} offline actions.")
            result.completed > 0 -> setStatus("Synced ${result.completed} offline actions; ${result.failed} still pending.")
        }
    }
}
