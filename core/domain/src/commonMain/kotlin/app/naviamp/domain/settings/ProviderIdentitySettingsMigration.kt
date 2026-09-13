package app.naviamp.domain.settings

import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.queue.PlaybackQueueGroup

/** Versioned shared boundary for provider IDs persisted outside the SQL store. */
interface ProviderIdentitySettingsMigrationRepository {
    fun providerIdentitySettingsVersion(sourceId: String): Long?

    fun migrateProviderIdentitySettings(
        sourceId: String,
        targetVersion: Long,
        transform: (String) -> String,
    ): Boolean
}

fun SavedTrack.migratedProviderIdentities(transform: (String) -> String): SavedTrack = copy(
    id = transform(id),
    artistId = artistId?.let(transform),
    albumId = albumId?.let(transform),
    coverArtId = coverArtId?.let(transform),
    artistCredits = artistCredits.map { credit ->
        SavedArtistCredit(id = credit.id?.let(transform), name = credit.name)
    },
)

fun SavedArtist.migratedProviderIdentities(transform: (String) -> String): SavedArtist =
    copy(id = transform(id))

fun SavedAlbum.migratedProviderIdentities(transform: (String) -> String): SavedAlbum = copy(
    id = transform(id),
    coverArtId = coverArtId?.let(transform),
)

fun SavedInternetRadioStation.migratedProviderIdentities(transform: (String) -> String): SavedInternetRadioStation =
    copy(id = transform(id))

fun PlaybackProfileTarget.migratedProviderIdentities(transform: (String) -> String): PlaybackProfileTarget =
    copy(id = transform(id))

fun PlaybackQueueGroup.migratedProviderIdentities(transform: (String) -> String): PlaybackQueueGroup {
    val migratedTarget = target.migratedProviderIdentities(transform)
    val oldPrefix = "${target.type}:${target.id}"
    val migratedPrefix = "${migratedTarget.type}:${migratedTarget.id}"
    return copy(
        id = if (id.startsWith(oldPrefix)) migratedPrefix + id.removePrefix(oldPrefix) else id,
        target = migratedTarget,
    )
}

fun PlaybackSessionSettings.migratedProviderIdentities(transform: (String) -> String): PlaybackSessionSettings = copy(
    tracks = tracks.map { it.migratedProviderIdentities(transform) },
    queueGroups = queueGroups.map { it.migratedProviderIdentities(transform) },
    internetRadioStation = internetRadioStation?.migratedProviderIdentities(transform),
)

fun RecentRadioStream.migratedProviderIdentities(
    sourceId: String,
    transform: (String) -> String,
): RecentRadioStream {
    if (this.sourceId != sourceId) return this
    return copy(
        id = id.split(':').joinToString(":", transform = transform),
        artist = artist?.migratedProviderIdentities(transform),
        album = album?.migratedProviderIdentities(transform),
        track = track?.migratedProviderIdentities(transform),
        coverArtIds = coverArtIds.map(transform),
        sessionTracks = sessionTracks.map { it.migratedProviderIdentities(transform) },
    )
}

fun SavedInternetRadioStation.migratedProviderIdentities(
    sourceId: String,
    transform: (String) -> String,
): SavedInternetRadioStation =
    if (this.sourceId == sourceId) migratedProviderIdentities(transform) else this
