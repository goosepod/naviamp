package app.naviamp.presentation

import app.naviamp.domain.Track
import app.naviamp.domain.media.inAlbumOrder
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.ui.NaviampVehicleDownloadsLabels
import app.naviamp.ui.NaviampVehicleLibraryLabels
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow

internal fun NaviampVehicleLibraryKind.isDownloaded() = this in setOf(
    NaviampVehicleLibraryKind.Downloads, NaviampVehicleLibraryKind.DownloadedArtists,
    NaviampVehicleLibraryKind.DownloadedAlbums, NaviampVehicleLibraryKind.DownloadedTracks,
    NaviampVehicleLibraryKind.DownloadedArtistAlbums, NaviampVehicleLibraryKind.DownloadedAlbumTracks,
)

/** Read-only local catalog. Opening it never starts transfers, reconciles policies or prunes files. */
internal class NaviampVehicleDownloadsAccess(
    private val product: StateFlow<NaviampCoreState>,
    private val storage: NaviampCoreDownloadStoragePort,
    private val canPlay: () -> Boolean,
    private val play: (List<Track>, Int, Boolean, NaviampVehicleLibraryReference) -> Boolean,
    private val libraryLabels: NaviampVehicleLibraryLabels,
    val labels: NaviampVehicleDownloadsLabels,
    private val downloadedQueue: () -> Boolean = { false },
) {
    private var snapshotSource: List<Any?>? = null
    private var snapshot: List<Track>? = null
    private var snapshotKnown = false
    val currentQueueDownloadedOnly: Boolean get() = available && downloadedQueue()
    val unavailableLabel: String get() = libraryLabels.unavailable
    fun invalidate() { snapshotKnown = false; snapshot = null; snapshotSource = null }
    val available: Boolean get() = product.value.shell.let {
        !it.connectionSettings.currentSourceId.isNullOrBlank() && !it.connect.remotePlaybackAuthorityActive &&
            !it.connectionSettings.connection.isConnecting && !it.connectionSettings.connection.restoringConnection && canPlay()
    }
    fun categories(): List<NaviampVehicleLibraryEntry> = if (!available) emptyList() else listOf(
        NaviampVehicleLibraryKind.DownloadedArtists to libraryLabels.artists,
        NaviampVehicleLibraryKind.DownloadedAlbums to libraryLabels.albums,
        NaviampVehicleLibraryKind.DownloadedTracks to libraryLabels.tracks,
    ).map { (kind, title) -> NaviampVehicleLibraryEntry("download-category:${kind.name}", title,
        navigation = NaviampVehicleLibraryReference(kind, title)) }

    private fun artistKey(track: Track) = track.artistId?.value?.let { "id:$it" } ?: "name:${track.artistName}"
    private suspend fun tracks(source: List<Any?>, sourceId: String, revalidate: Boolean = false): List<Track>? {
        if (!revalidate && snapshotKnown && snapshotSource == source) return snapshot
        val downloads = storage.availableDownloads(sourceId)
        currentCoroutineContext().ensureActive()
        check(available && product.value.vehicleSourceKey() == source)
        return downloads?.filter { it.sizeBytes > 0 }?.map { it.track }?.distinctBy { it.id }
            ?.sortedWith(compareBy<Track> { it.title.lowercase() }.thenBy { it.id.value }).also {
                snapshot = it; snapshotSource = source; snapshotKnown = true
            }
    }
    private fun groupTracks(all: List<Track>, reference: NaviampVehicleLibraryReference): List<Track> = when (reference.kind) {
        NaviampVehicleLibraryKind.DownloadedAlbumTracks -> all.filter { it.albumId?.value == reference.id }.inAlbumOrder()
        else -> all
    }

    suspend fun load(reference: NaviampVehicleLibraryReference, request: MediaPageRequest): NaviampVehicleLibraryPage {
        check(available && reference.kind.isDownloaded())
        val source = product.value.vehicleSourceKey()
        val sourceId = checkNotNull(product.value.shell.connectionSettings.currentSourceId)
        if (reference.kind == NaviampVehicleLibraryKind.Downloads) {
            val categories = categories()
            return NaviampVehicleLibraryPage(categories.drop(request.offset).take(request.limit),
                if (request.offset + request.limit < categories.size) request.copy(offset = request.offset + request.limit) else null)
        }
        require(request.limit >= 2)
        val all = tracks(source, sourceId) ?: return NaviampVehicleLibraryPage(emptyList(), unavailable = true)
        val relevant = groupTracks(all, reference)
        fun albumEntries(tracks: List<Track>) = tracks.filter { !it.albumId?.value.isNullOrBlank() && !it.albumTitle.isNullOrBlank() }
            .groupBy { checkNotNull(it.albumId).value }.map { (id, tracks) ->
                val track = tracks.first()
                NaviampVehicleLibraryEntry("download-album:$id", checkNotNull(track.albumTitle), track.artistName,
                    navigation = NaviampVehicleLibraryReference(NaviampVehicleLibraryKind.DownloadedAlbumTracks, checkNotNull(track.albumTitle), id),
                    artwork = NaviampVehicleArtworkReference(app.naviamp.ui.SharedMediaItemKind.Album, coverArtId = track.coverArtId, cacheOnly = true))
            }.sortedWith(compareBy<NaviampVehicleLibraryEntry> { it.title.lowercase() }.thenBy { it.id })
        val body = when (reference.kind) {
            NaviampVehicleLibraryKind.DownloadedArtists -> all.groupBy(::artistKey).map { (id, tracks) ->
                NaviampVehicleLibraryEntry("download-artist:$id", tracks.first().artistName,
                    navigation = NaviampVehicleLibraryReference(NaviampVehicleLibraryKind.DownloadedArtistAlbums, tracks.first().artistName, id),
                    artwork = NaviampVehicleArtworkReference(app.naviamp.ui.SharedMediaItemKind.Artist, cacheOnly = true))
            }.sortedWith(compareBy<NaviampVehicleLibraryEntry> { it.title.lowercase() }.thenBy { it.id })
            NaviampVehicleLibraryKind.DownloadedAlbums -> albumEntries(all)
            NaviampVehicleLibraryKind.DownloadedArtistAlbums -> albumEntries(all.filter { artistKey(it) == reference.id })
            NaviampVehicleLibraryKind.DownloadedTracks, NaviampVehicleLibraryKind.DownloadedAlbumTracks -> buildList {
                if (relevant.isNotEmpty()) {
                    add(NaviampVehicleLibraryEntry(Play, labels.play))
                    add(NaviampVehicleLibraryEntry(Shuffle, labels.shuffle))
                }
                addAll(relevant.map { NaviampVehicleLibraryEntry("download-track:${it.id.value}", it.title, it.artistName,
                    artwork = NaviampVehicleArtworkReference(app.naviamp.ui.SharedMediaItemKind.Track, coverArtId = it.coverArtId, cacheOnly = true)) })
            }
            else -> error("Unsupported downloaded collection")
        }
        val count = (request.limit - 1).coerceAtLeast(1)
        val rows = listOf(NaviampVehicleLibraryEntry("download-refresh:${request.offset}", labels.refresh, refresh = true)) + body.drop(request.offset).take(count)
        return NaviampVehicleLibraryPage(rows,
            if (request.offset + count < body.size) request.copy(offset = request.offset + count) else null,
            status = if (body.isEmpty()) labels.empty else labels.downloads,
            start = { id ->
                val latest = groupTracks(tracks(source, sourceId, revalidate = true).orEmpty(), reference)
                val index = when (id) {
                    Play, Shuffle -> 0
                    else -> latest.indexOfFirst { "download-track:${it.id.value}" == id }
                }
                if (index !in latest.indices || !available || product.value.vehicleSourceKey() != source) false
                else play(latest, index, id == Shuffle, reference)
            })
    }
    private companion object {
        const val Play = "download-play"
        const val Shuffle = "download-shuffle"
    }
}
