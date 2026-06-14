package app.naviamp.domain.provider

import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.TrackId

const val PendingActionReportNowPlaying = "report_now_playing"
const val PendingActionReportPlayed = "report_played"
const val PendingActionTrackFavorite = "track_favorite"
const val PendingActionArtistFavorite = "artist_favorite"
const val PendingActionAlbumFavorite = "album_favorite"

data class PendingProviderAction(
    val id: Long,
    val sourceId: String,
    val actionType: String,
    val entityId: String,
    val boolValue: Boolean? = null,
    val longValue: Long? = null,
    val createdAtEpochMillis: Long,
    val lastAttemptAtEpochMillis: Long? = null,
    val attemptCount: Long = 0,
    val lastError: String? = null,
)

interface PendingProviderActionRepository {
    fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean? = null,
        longValue: Long? = null,
        replaceMatchingEntityAction: Boolean = false,
    )

    fun pendingProviderActions(sourceId: String, limit: Int = 50): List<PendingProviderAction>

    fun deletePendingProviderAction(id: Long)

    fun markPendingProviderActionFailed(id: Long, errorMessage: String?)
}

data class PendingProviderActionSyncResult(
    val attempted: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
)

suspend fun replayPendingProviderActions(
    sourceId: String,
    provider: MediaProvider,
    repository: PendingProviderActionRepository,
    limit: Int = 50,
): PendingProviderActionSyncResult {
    var attempted = 0
    var completed = 0
    var failed = 0
    for (action in repository.pendingProviderActions(sourceId, limit)) {
        attempted++
        runCatching {
            action.applyTo(provider)
        }.onSuccess {
            repository.deletePendingProviderAction(action.id)
            completed++
        }.onFailure { error ->
            repository.markPendingProviderActionFailed(action.id, error.message)
            failed++
        }
    }
    return PendingProviderActionSyncResult(
        attempted = attempted,
        completed = completed,
        failed = failed,
    )
}

private suspend fun PendingProviderAction.applyTo(provider: MediaProvider) {
    when (actionType) {
        PendingActionReportNowPlaying -> provider.reportNowPlaying(TrackId(entityId))
        PendingActionReportPlayed -> provider.reportPlayed(TrackId(entityId), requireNotNull(longValue))
        PendingActionTrackFavorite -> provider.setTrackFavorite(TrackId(entityId), requireNotNull(boolValue))
        PendingActionArtistFavorite -> provider.setArtistFavorite(ArtistId(entityId), requireNotNull(boolValue))
        PendingActionAlbumFavorite -> provider.setAlbumFavorite(AlbumId(entityId), requireNotNull(boolValue))
        else -> throw IllegalArgumentException("Unsupported pending provider action: $actionType")
    }
}
