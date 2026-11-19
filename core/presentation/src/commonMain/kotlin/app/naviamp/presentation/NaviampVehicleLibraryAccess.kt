package app.naviamp.presentation

import app.naviamp.domain.*
import app.naviamp.domain.provider.*
import app.naviamp.ui.NaviampVehicleLibraryLabels
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow

internal enum class NaviampVehicleLibraryKind { Root, Favorites, Artists, Albums, Tracks, FavoriteArtists, FavoriteAlbums, FavoriteTracks, ArtistAlbums, Search, SearchResults, SearchArtists, SearchAlbums, SearchTracks, Downloads, DownloadedArtists, DownloadedAlbums, DownloadedTracks, DownloadedArtistAlbums, DownloadedAlbumTracks }
internal data class NaviampVehicleLibraryReference(val kind: NaviampVehicleLibraryKind, val title: String, val id: String = "", val query: String = "")
internal data class NaviampVehicleLibraryEntry(
    val id: String, val title: String, val subtitle: String = "",
    val navigation: NaviampVehicleLibraryReference? = null,
    val album: NaviampVehicleCollectionReference? = null,
    val refresh: Boolean = false,
    val artwork: NaviampVehicleArtworkReference? = null,
)
internal class NaviampVehicleLibraryPage(
    val entries: List<NaviampVehicleLibraryEntry>, val next: MediaPageRequest? = null,
    val unavailable: Boolean = false, val status: String = "", private val start: suspend (String) -> Boolean = { false },
) { suspend fun play(id: String): Boolean = start(id) }

/** Scene-independent catalog contract; no phone Library state or selection is mutated. */
internal interface NaviampVehicleLibraryAccess {
    val labels: NaviampVehicleLibraryLabels
    val available: Boolean
    fun categories(favorites: Boolean = false): List<NaviampVehicleLibraryEntry>
    suspend fun load(reference: NaviampVehicleLibraryReference, request: MediaPageRequest): NaviampVehicleLibraryPage
}

/** Reuses the common provider catalog and queue transaction, including offline-capable providers. */
internal class NaviampCoreVehicleLibraryAccess(
    private val product: StateFlow<NaviampCoreState>,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val play: (Track) -> Boolean,
    override val labels: NaviampVehicleLibraryLabels,
) : NaviampVehicleLibraryAccess {
    override val available: Boolean get() = product.value.shell.let { shell ->
        !shell.connectionSettings.currentSourceId.isNullOrBlank() &&
            !shell.connectionSettings.connection.isConnecting && !shell.connectionSettings.connection.restoringConnection &&
            !shell.connect.remotePlaybackAuthorityActive && providerSource.current() != null
    }

    override fun categories(favorites: Boolean): List<NaviampVehicleLibraryEntry> {
        if (!available) return emptyList()
        val capability = providerSource.current()?.capabilities ?: return emptyList()
        fun entry(kind: NaviampVehicleLibraryKind, title: String) = NaviampVehicleLibraryEntry(
            "category:${kind.name}", title, navigation = NaviampVehicleLibraryReference(kind, title))
        return if (favorites) buildList {
            if (capability.supportsArtistFavorites) add(entry(NaviampVehicleLibraryKind.FavoriteArtists, labels.artists))
            if (capability.supportsAlbumFavorites) add(entry(NaviampVehicleLibraryKind.FavoriteAlbums, labels.albums))
            if (capability.supportsTrackFavorites) add(entry(NaviampVehicleLibraryKind.FavoriteTracks, labels.tracks))
        } else buildList {
            add(entry(NaviampVehicleLibraryKind.Artists, labels.artists))
            add(entry(NaviampVehicleLibraryKind.Albums, labels.albums))
            add(entry(NaviampVehicleLibraryKind.Tracks, labels.tracks))
            if (capability.supportsArtistFavorites || capability.supportsAlbumFavorites || capability.supportsTrackFavorites)
                add(entry(NaviampVehicleLibraryKind.Favorites, labels.favorites))
        }
    }

    override suspend fun load(reference: NaviampVehicleLibraryReference, request: MediaPageRequest): NaviampVehicleLibraryPage {
        check(available)
        val source = product.value.vehicleSourceKey()
        val provider = checkNotNull(providerSource.current())
        val catalog = ApiCatalogService { provider }
        val tracks = mutableMapOf<String, Track>()
        fun artist(value: Artist) = NaviampVehicleLibraryEntry("artist:${value.id.value}", value.name,
            navigation = NaviampVehicleLibraryReference(NaviampVehicleLibraryKind.ArtistAlbums, value.name, value.id.value),
            artwork = NaviampVehicleArtworkReference(app.naviamp.ui.SharedMediaItemKind.Artist, artistId = value.id.value))
        fun album(value: Album) = NaviampVehicleLibraryEntry("album:${value.id.value}", value.title, value.artistName,
            album = NaviampVehicleCollectionReference(NaviampVehicleCollectionKind.Album, value.id.value, value.title),
            artwork = NaviampVehicleArtworkReference(app.naviamp.ui.SharedMediaItemKind.Album, coverArtId = value.coverArtId))
        fun track(value: Track): NaviampVehicleLibraryEntry {
            val id = "track:${value.id.value}"
            tracks[id] = value
            return NaviampVehicleLibraryEntry(id, value.title, value.artistName,
                artwork = NaviampVehicleArtworkReference(app.naviamp.ui.SharedMediaItemKind.Track, coverArtId = value.coverArtId))
        }
        fun <T> mapped(page: MediaPage<T>?, transform: (T) -> NaviampVehicleLibraryEntry): NaviampVehicleLibraryPage {
            if (page == null) return NaviampVehicleLibraryPage(emptyList(), unavailable = true)
            require(page.offset == request.offset && page.limit == request.limit && page.items.size <= request.limit)
            require(!page.hasMore || page.items.isNotEmpty()) { "Catalog continuation did not advance" }
            require(page.hasMore || page.totalItemCount?.let { page.offset + page.items.size >= it } != false) { "Incomplete catalog page" }
            val entries = page.items.map(transform)
            require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate catalog identities" }
            return NaviampVehicleLibraryPage(entries, page.nextRequest, start = { id ->
                if (!available || product.value.vehicleSourceKey() != source || !providerSource.isCurrent(provider)) false
                else tracks[id]?.let(play) ?: false
            })
        }
        val result = when (reference.kind) {
            NaviampVehicleLibraryKind.Root, NaviampVehicleLibraryKind.Favorites -> {
                val entries = categories(reference.kind == NaviampVehicleLibraryKind.Favorites)
                NaviampVehicleLibraryPage(entries.drop(request.offset).take(request.limit),
                    if (entries.size > request.offset + request.limit) request.copy(offset = request.offset + request.limit) else null)
            }
            NaviampVehicleLibraryKind.Downloads, NaviampVehicleLibraryKind.DownloadedArtists,
            NaviampVehicleLibraryKind.DownloadedAlbums, NaviampVehicleLibraryKind.DownloadedTracks,
            NaviampVehicleLibraryKind.DownloadedArtistAlbums, NaviampVehicleLibraryKind.DownloadedAlbumTracks -> error("Downloaded catalogs use verified local storage")
            NaviampVehicleLibraryKind.Search, NaviampVehicleLibraryKind.SearchResults -> error("Search presentation is owned by the vehicle scene")
            NaviampVehicleLibraryKind.SearchArtists -> mapped(catalog.artistsPage(checkNotNull(normalizedSearchQuery(reference.query)), request), ::artist)
            NaviampVehicleLibraryKind.SearchAlbums -> mapped(catalog.albumsPage(checkNotNull(normalizedSearchQuery(reference.query)), request), ::album)
            NaviampVehicleLibraryKind.SearchTracks -> mapped(catalog.tracksPage(checkNotNull(normalizedSearchQuery(reference.query)), request), ::track)
            NaviampVehicleLibraryKind.Artists -> mapped(catalog.artistsPage("", request), ::artist)
            NaviampVehicleLibraryKind.Albums -> mapped(catalog.albumsPage("", request), ::album)
            NaviampVehicleLibraryKind.Tracks -> mapped(catalog.tracksPage("", request), ::track)
            NaviampVehicleLibraryKind.ArtistAlbums -> mapped(provider.artistAlbumsPage(ArtistId(reference.id), request), ::album)
            NaviampVehicleLibraryKind.FavoriteArtists -> mapped(provider.favoriteArtistsPage(request), ::artist)
            NaviampVehicleLibraryKind.FavoriteAlbums -> mapped(provider.favoriteAlbumsPage(request), ::album)
            NaviampVehicleLibraryKind.FavoriteTracks -> mapped(provider.favoriteTracksPage(request), ::track)
        }
        currentCoroutineContext().ensureActive()
        check(available && product.value.vehicleSourceKey() == source && providerSource.isCurrent(provider))
        return result
    }
}
