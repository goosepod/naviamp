package app.naviamp.domain.cache

import app.naviamp.domain.Track

enum class KeepDownloadedCollectionKind {
    Playlist,
    SmartPlaylist,
    Favorites,
}

data class KeepDownloadedCollectionPolicy(
    val sourceId: String,
    val kind: KeepDownloadedCollectionKind,
    val collectionId: String,
    val name: String,
    val removeUnneededFiles: Boolean = false,
)

interface KeepDownloadedRepository {
    fun keepDownloadedPolicies(sourceId: String): List<KeepDownloadedCollectionPolicy>

    fun keepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ): KeepDownloadedCollectionPolicy?

    fun upsertKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy)

    fun deleteKeepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    )

    fun keepDownloadedTrackIds(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ): Set<String>

    fun replaceKeepDownloadedTrackIds(
        policy: KeepDownloadedCollectionPolicy,
        trackIds: Set<String>,
    )

    fun managedKeepDownloadedTrackIds(sourceId: String): Set<String>

    fun markManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>)

    fun unmarkManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>)
}

data class KeepDownloadedReconciliationPlan(
    val tracksToDownload: List<Track>,
    val trackIdsToRemove: Set<String>,
    val nextTrackIds: Set<String>,
)

fun planKeepDownloadedReconciliation(
    tracks: List<Track>,
    previousTrackIds: Set<String>,
    downloadedTrackIds: Set<String>,
    managedTrackIds: Set<String>,
    trackIdsRequiredByOtherPolicies: Set<String>,
    removeUnneededFiles: Boolean,
): KeepDownloadedReconciliationPlan {
    val distinctTracks = tracks.distinctBy { it.id }
    val nextTrackIds = distinctTracks.mapTo(linkedSetOf()) { it.id.value }
    val tracksToDownload = distinctTracks.filterNot { it.id.value in downloadedTrackIds }
    val trackIdsToRemove = if (removeUnneededFiles) {
        (previousTrackIds - nextTrackIds)
            .intersect(managedTrackIds)
            .minus(trackIdsRequiredByOtherPolicies)
    } else {
        emptySet()
    }
    return KeepDownloadedReconciliationPlan(
        tracksToDownload = tracksToDownload,
        trackIdsToRemove = trackIdsToRemove,
        nextTrackIds = nextTrackIds,
    )
}
