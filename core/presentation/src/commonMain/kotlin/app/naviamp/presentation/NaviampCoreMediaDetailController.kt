package app.naviamp.presentation

import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.media.loadArtistPopularTracksUpdate
import app.naviamp.domain.media.loadSimilarArtistsUpdate
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.ui.NaviampArtistAlbumCommand
import app.naviamp.ui.NaviampArtistDetailCommand
import app.naviamp.ui.NaviampArtistMediaCommand
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.toSharedAlbumDetailUi
import app.naviamp.ui.toSharedArtistDetailUi
import app.naviamp.ui.toSharedMediaItemUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class NaviampCoreArtistDiscoveryServices(
    val sourceId: () -> String? = { null },
    val popularTracks: suspend (String, Artist, Int) -> List<ArtistPopularTrackMatch> = { _, _, _ -> emptyList() },
    val similarArtists: suspend (Artist, Int) -> List<SimilarArtistMatch> = { _, _ -> emptyList() },
)

/** Owns album/artist selection, loading, enrichment, navigation, mapping, and stale-result policy. */
class NaviampCoreMediaDetailController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val navigationController: NaviampCoreNavigationController,
    private val scope: CoroutineScope,
    private val discovery: NaviampCoreArtistDiscoveryServices = NaviampCoreArtistDiscoveryServices(),
) : NaviampCoreCommandController, NaviampCoreArtistNavigator {
    private var albumGeneration = 0L
    private var artistGeneration = 0L

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.Media.SelectAlbum,
        is NaviampCoreCommand.Media.SelectArtist,
        -> NaviampCoreImmediateCommandResult.Deferred
        is NaviampCoreCommand.Media.ItemAction -> when (val itemCommand = command.request.command) {
            is NaviampMediaItemCommand.Album -> if (itemCommand.command == NaviampArtistAlbumCommand.Select) {
                NaviampCoreImmediateCommandResult.Deferred
            } else {
                NaviampCoreImmediateCommandResult.Unhandled
            }
            is NaviampMediaItemCommand.Artist -> if (itemCommand.command == NaviampArtistMediaCommand.Select) {
                NaviampCoreImmediateCommandResult.Deferred
            } else {
                NaviampCoreImmediateCommandResult.Unhandled
            }
            is NaviampMediaItemCommand.Playlist -> NaviampCoreImmediateCommandResult.Unhandled
        }
        is NaviampCoreCommand.Detail.ArtistAlbum ->
            if (command.request.command == NaviampArtistAlbumCommand.Select) {
                NaviampCoreImmediateCommandResult.Deferred
            } else {
                NaviampCoreImmediateCommandResult.Unhandled
            }
        is NaviampCoreCommand.Detail.Artist ->
            if (command.request.command is NaviampArtistDetailCommand.SelectSimilar) {
                NaviampCoreImmediateCommandResult.Deferred
            } else {
                NaviampCoreImmediateCommandResult.Unhandled
            }
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            is NaviampCoreCommand.Media.SelectAlbum -> loadAlbum(command.album)
            is NaviampCoreCommand.Media.SelectArtist -> loadArtist(command.artist)
            is NaviampCoreCommand.Media.ItemAction -> when (val itemCommand = command.request.command) {
                is NaviampMediaItemCommand.Album -> {
                    if (itemCommand.command != NaviampArtistAlbumCommand.Select) return null
                    loadAlbum(command.request.item)
                }
                is NaviampMediaItemCommand.Artist -> {
                    if (itemCommand.command != NaviampArtistMediaCommand.Select) return null
                    loadArtist(command.request.item)
                }
                is NaviampMediaItemCommand.Playlist -> return null
            }
            is NaviampCoreCommand.Detail.ArtistAlbum -> {
                if (command.request.command != NaviampArtistAlbumCommand.Select) return null
                loadAlbum(command.request.album)
            }
            is NaviampCoreCommand.Detail.Artist -> {
                val selection = command.request.command as? NaviampArtistDetailCommand.SelectSimilar ?: return null
                loadArtist(
                    SharedMediaItemUi(
                        id = selection.artist.localArtistId ?: selection.artist.id,
                        title = selection.artist.title,
                        subtitle = selection.artist.subtitle,
                    ),
                )
            }
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    /** Handles a prior-artist destination returned synchronously by common navigation history. */
    override fun openArtist(artist: Artist) {
        scope.launch {
            loadArtist(
                item = artist.toSharedMediaItemUi(),
                pushCurrentArtist = false,
            )
        }
    }

    private suspend fun loadAlbum(item: SharedMediaItemUi) {
        val generation = ++albumGeneration
        navigationController.openAlbumDetail()
        stateStore.updateShell { shell ->
            shell.copy(
                albumDetail = shell.albumDetail.copy(
                    selectedAlbum = item,
                    detail = null,
                    status = "Loading ${item.title}...",
                ),
            )
        }
        val provider = providerSource.current()
        if (provider == null) {
            publishAlbumFailure(item, "Connect to Navidrome to load an album.")
            return
        }
        val coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) }
        runCatching { provider.album(AlbumId(item.id)) }
            .onSuccess { detail ->
                if (generation != albumGeneration) return@onSuccess
                stateStore.updateShell { shell ->
                    shell.copy(
                        albumDetail = shell.albumDetail.copy(
                            selectedAlbum = detail.album.toSharedMediaItemUi(
                                coverArtUrl = coverArtUrl,
                                canFavorite = provider.capabilities.supportsAlbumFavorites,
                            ),
                            detail = detail.toSharedAlbumDetailUi(
                                coverArtUrl = coverArtUrl,
                                canFavoriteAlbum = provider.capabilities.supportsAlbumFavorites,
                            ),
                            status = "Connected.",
                        ),
                    )
                }
            }
            .onFailure { cause ->
                if (generation == albumGeneration) {
                    publishAlbumFailure(item, cause.message ?: "Could not load album.")
                }
            }
    }

    private suspend fun loadArtist(
        item: SharedMediaItemUi,
        pushCurrentArtist: Boolean = true,
    ) {
        val generation = ++artistGeneration
        val artist = Artist(ArtistId(item.id), item.title)
        navigationController.openArtistDetail(artist, pushCurrentArtist = pushCurrentArtist)
        publishArtistLoading(item)
        val provider = providerSource.current()
        if (provider == null) {
            publishArtistFailure(item, "Connect to Navidrome to load an artist.")
            return
        }
        val coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) }
        runCatching { provider.artist(artist.id) }
            .onSuccess { detail ->
                if (generation != artistGeneration) return@onSuccess
                navigationController.updateActiveArtist(detail.artist)
                val popular = loadArtistPopularTracksUpdate(
                    sourceId = discovery.sourceId(),
                    artist = detail.artist,
                    loadPopularTracks = discovery.popularTracks,
                )
                val similar = loadSimilarArtistsUpdate(
                    artist = detail.artist,
                    loadSimilarArtists = discovery.similarArtists,
                )
                if (generation != artistGeneration) return@onSuccess
                stateStore.updateShell { shell ->
                    shell.copy(
                        artistDetail = shell.artistDetail.copy(
                            selectedArtist = detail.artist.toSharedMediaItemUi(
                                coverArtUrl = coverArtUrl,
                                canFavorite = provider.capabilities.supportsArtistFavorites,
                            ),
                            detail = detail.toSharedArtistDetailUi(
                                coverArtUrl = coverArtUrl,
                                popularTracks = popular.tracks,
                                popularTracksStatus = popular.status,
                                similarArtists = similar.artists,
                                similarArtistsStatus = similar.status,
                                canFavoriteArtist = provider.capabilities.supportsArtistFavorites,
                                canFavoriteAlbums = provider.capabilities.supportsAlbumFavorites,
                            ),
                            status = if (detail.albums.isEmpty()) {
                                "No albums found for ${detail.artist.name}."
                            } else {
                                "Connected."
                            },
                        ),
                    )
                }
            }
            .onFailure { cause ->
                if (generation == artistGeneration) {
                    publishArtistFailure(item, cause.message ?: "Could not load artist.")
                }
            }
    }

    private fun publishAlbumFailure(item: SharedMediaItemUi, status: String) {
        stateStore.updateShell { shell ->
            shell.copy(albumDetail = shell.albumDetail.copy(selectedAlbum = item, detail = null, status = status))
        }
    }

    private fun publishArtistLoading(item: SharedMediaItemUi) {
        stateStore.updateShell { shell ->
            shell.copy(
                artistDetail = shell.artistDetail.copy(
                    selectedArtist = item,
                    detail = null,
                    status = "Loading ${item.title}...",
                ),
            )
        }
    }

    private fun publishArtistFailure(item: SharedMediaItemUi, status: String) {
        stateStore.updateShell { shell ->
            shell.copy(artistDetail = shell.artistDetail.copy(selectedArtist = item, detail = null, status = status))
        }
    }
}
