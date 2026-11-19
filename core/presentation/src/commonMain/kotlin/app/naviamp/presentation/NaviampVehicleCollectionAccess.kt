package app.naviamp.presentation

import app.naviamp.domain.AlbumId
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.Track
import app.naviamp.domain.media.inAlbumOrder
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow

internal enum class NaviampVehicleCollectionKind { Album, Playlist }
internal data class NaviampVehicleCollectionReference(
    val kind: NaviampVehicleCollectionKind,
    val id: String,
    val title: String,
)

/** A scene-owned snapshot; loading or browsing it never navigates the phone or replaces its detail. */
internal class NaviampVehicleCollection(
    val tracks: List<Track>,
    private val start: (Int, Boolean) -> Boolean,
) {
    fun play(index: Int = 0, shuffle: Boolean = false): Boolean =
        index in tracks.indices && start(index, shuffle)
}

internal fun interface NaviampVehicleCollectionAccess {
    suspend fun load(reference: NaviampVehicleCollectionReference): NaviampVehicleCollection
}

internal fun NaviampCoreState.vehicleSourceKey(): List<Any?> = shell.connectionSettings.let {
    listOf(sourceGeneration, it.currentSourceId, it.accountSelectionGeneration,
        it.connection.isConnecting, it.connection.restoringConnection, shell.connect.remotePlaybackAuthorityActive)
}

/** Reuses the provider's offline-capable source and the product's queue/profile transaction. */
internal class NaviampCoreVehicleCollectionAccess(
    private val product: StateFlow<NaviampCoreState>,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val queuePlayback: NaviampCoreQueuePlaybackController,
) : NaviampVehicleCollectionAccess {
    override suspend fun load(reference: NaviampVehicleCollectionReference): NaviampVehicleCollection {
        val source = product.value.vehicleSourceKey()
        check(!product.value.shell.connectionSettings.connection.isConnecting)
        check(!product.value.shell.connect.remotePlaybackAuthorityActive)
        val provider = checkNotNull(providerSource.current())
        val tracks = when (reference.kind) {
            NaviampVehicleCollectionKind.Album -> {
                val album = AlbumId(reference.id)
                val firstRequest = MediaPageRequest()
                val first = provider.albumTracksPage(album, firstRequest)
                if (first == null) provider.album(album).tracks.inAlbumOrder()
                else {
                    val tracks = mutableListOf<Track>()
                    var request = firstRequest
                    var page: app.naviamp.domain.provider.MediaPage<Track> = first
                    val visited = mutableSetOf<MediaPageRequest>()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        check(product.value.vehicleSourceKey() == source && providerSource.isCurrent(provider))
                        check(visited.add(request))
                        require(page.offset == request.offset && page.limit == request.limit && page.items.size <= request.limit)
                        require(!page.hasMore || page.items.isNotEmpty())
                        tracks += page.items
                        request = page.nextRequest ?: break
                        page = checkNotNull(provider.albumTracksPage(album, request))
                    }
                    tracks.inAlbumOrder()
                }
            }
            NaviampVehicleCollectionKind.Playlist -> provider.playlistTracks(reference.id).toList()
        }
        currentCoroutineContext().ensureActive()
        check(product.value.vehicleSourceKey() == source)
        return NaviampVehicleCollection(tracks) { index, shuffle ->
            if (product.value.vehicleSourceKey() != source || product.value.shell.connect.remotePlaybackAuthorityActive) false
            else queuePlayback.play(tracks, index, shuffle,
                PlaybackProfileTarget(when (reference.kind) {
                    NaviampVehicleCollectionKind.Album -> PlaybackProfileTargetType.Album
                    NaviampVehicleCollectionKind.Playlist -> PlaybackProfileTargetType.Playlist
                }, reference.id), reference.title)
        }
    }
}
