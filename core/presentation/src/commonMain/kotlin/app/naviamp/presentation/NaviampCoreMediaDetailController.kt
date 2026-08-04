package app.naviamp.presentation

import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.media.loadArtistPopularTracksUpdate
import app.naviamp.domain.media.loadSimilarArtistsUpdate
import app.naviamp.domain.media.isNameOnlyArtistCredit
import app.naviamp.domain.media.loadNameOnlyArtistCreditDetails
import app.naviamp.domain.media.nameOnlyArtistCredit
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.ProviderArtistPopularTracksClient
import app.naviamp.domain.popular.ProviderSimilarArtistsClient
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampArtistAlbumCommand
import app.naviamp.ui.NaviampArtistDetailCommand
import app.naviamp.ui.NaviampArtistMediaCommand
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.toSharedAlbumDetailUi
import app.naviamp.ui.toSharedArtistDetailUi
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedSimilarArtistUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class NaviampCoreArtistDiscoveryServices(
    val sourceId: () -> String? = { null },
    val popularTracks: suspend (String, Artist, Int) -> List<ArtistPopularTrackMatch> = { _, _, _ -> emptyList() },
    val similarArtists: suspend (Artist, Int) -> List<SimilarArtistMatch> = { _, _ -> emptyList() },
)

/** Builds provider-backed discovery once for every host; platforms supply only durable storage. */
fun providerArtistDiscoveryServices(
    providerSource: NaviampCoreMediaProviderSource,
    sourceId: () -> String?,
    libraryIndex: LocalLibraryIndexRepository,
    nowEpochMillis: () -> Long,
): NaviampCoreArtistDiscoveryServices {
    val popular = ArtistPopularTracksService(
        repository = libraryIndex,
        libraryTracksForArtist = { artist, limit ->
            sourceId()?.let { activeSourceId ->
                libraryIndex.libraryTracksForArtist(activeSourceId, artist.id, limit)
            }.orEmpty()
        },
        client = ProviderArtistPopularTracksClient(
            clientProvider = {
                providerSource.current() as? app.naviamp.domain.popular.ArtistPopularTracksClient
            },
        ),
        nowMillis = nowEpochMillis,
    )
    val similar = SimilarArtistsService(
        libraryArtistsSearch = { query, limit ->
            sourceId()?.let { activeSourceId ->
                libraryIndex.searchLibrary(activeSourceId, query, limit, 0).artists
            }.orEmpty()
        },
        client = ProviderSimilarArtistsClient {
            providerSource.current() as? app.naviamp.domain.popular.SimilarArtistsClient
        },
        fallbackArtistsSearch = { query, limit ->
            providerSource.current()?.search(query, limit.coerceAtMost(500).toInt())?.artists.orEmpty()
        },
    )
    return NaviampCoreArtistDiscoveryServices(
        sourceId = sourceId,
        popularTracks = popular::popularTracks,
        similarArtists = similar::similarArtists,
    )
}

/** Owns album/artist selection, loading, enrichment, navigation, mapping, and stale-result policy. */
class NaviampCoreMediaDetailController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val navigationController: NaviampCoreNavigationController,
    private val scope: CoroutineScope,
    private val discovery: NaviampCoreArtistDiscoveryServices = NaviampCoreArtistDiscoveryServices(),
    private val mediaRegistry: NaviampCoreMediaRegistry = NaviampCoreMediaRegistry(),
) : NaviampCoreCommandController, NaviampCoreArtistNavigator {
    private var albumGeneration = 0L
    private var artistGeneration = 0L

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
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
            NaviampMediaItemCommand.PlayAlbum -> NaviampCoreImmediateCommandResult.Unhandled
            is NaviampMediaItemCommand.Playlist -> NaviampCoreImmediateCommandResult.Unhandled
        }
        is NaviampCoreCommand.Detail.ArtistAlbum ->
            if (command.request.command == NaviampArtistAlbumCommand.Select) {
                NaviampCoreImmediateCommandResult.Deferred
            } else {
                NaviampCoreImmediateCommandResult.Unhandled
            }
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            is NaviampCoreCommand.Media.ItemAction -> when (val itemCommand = command.request.command) {
                is NaviampMediaItemCommand.Album -> {
                    if (itemCommand.command != NaviampArtistAlbumCommand.Select) return null
                    loadAlbum(command.request.item)
                }
                is NaviampMediaItemCommand.Artist -> {
                    if (itemCommand.command != NaviampArtistMediaCommand.Select) return null
                    loadArtist(command.request.item)
                }
                NaviampMediaItemCommand.PlayAlbum -> return null
                is NaviampMediaItemCommand.Playlist -> return null
            }
            is NaviampCoreCommand.Detail.ArtistAlbum -> {
                if (command.request.command != NaviampArtistAlbumCommand.Select) return null
                loadAlbum(command.request.album)
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

    internal suspend fun selectArtist(item: SharedMediaItemUi) {
        loadArtist(item, pushCurrentArtist = true)
    }

    internal suspend fun toggleSimilarArtists(item: SharedMediaItemUi) {
        val current = stateStore.state.value.shell.artistDetail
        if (current.selectedArtist?.id != item.id || current.detail == null) {
            loadArtist(item, pushCurrentArtist = true, loadSimilar = true)
            return
        }
        val currentDetail = current.detail ?: return
        if (currentDetail.similarArtistsExpanded) {
            mediaRegistry.updateArtist(
                mediaRegistry.artistDetails,
                mediaRegistry.artistPopularTracks,
                emptyList(),
            )
            stateStore.updateShell { shell ->
                shell.copy(
                    artistDetail = shell.artistDetail.copy(
                        detail = shell.artistDetail.detail?.copy(
                            similarArtists = emptyList(),
                            similarArtistsStatus = null,
                            similarArtistsExpanded = false,
                        ),
                    ),
                )
            }
            return
        }
        val artist = mediaRegistry.artistDetails?.artist ?: Artist(ArtistId(item.id), item.title)
        stateStore.updateShell { shell ->
            shell.copy(
                artistDetail = shell.artistDetail.copy(
                    detail = shell.artistDetail.detail?.copy(
                        similarArtistsStatus = "Finding similar artists...",
                        similarArtistsExpanded = true,
                    ),
                ),
            )
        }
        val similar = loadSimilarArtistsUpdate(artist, loadSimilarArtists = discovery.similarArtists)
        mediaRegistry.updateArtist(
            mediaRegistry.artistDetails,
            mediaRegistry.artistPopularTracks,
            similar.artists,
        )
        stateStore.updateShell { shell ->
            shell.copy(
                artistDetail = shell.artistDetail.copy(
                    detail = shell.artistDetail.detail?.copy(
                        similarArtists = similar.artists.map { it.toSharedSimilarArtistUi() },
                        similarArtistsStatus = similar.status,
                        similarArtistsExpanded = true,
                    ),
                ),
            )
        }
    }

    private suspend fun loadAlbum(item: SharedMediaItemUi) {
        val generation = ++albumGeneration
        mediaRegistry.updateAlbum(null)
        navigationController.openAlbumDetail()
        stateStore.updateShell { shell ->
            shell.copy(
                albumDetail = shell.albumDetail.copy(
                    selectedAlbum = item,
                    detail = null,
                    status = "Loading ${item.title}...",
                ),
                playlistDetail = NaviampPlaylistDetailScreenUi(),
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
                val albumArtist = detail.tracks.firstOrNull()?.let { track ->
                    track.artistId?.let { Artist(it, track.artistName) }
                        ?: track.artistName.takeIf(String::isNotBlank)?.let(::nameOnlyArtistCredit)
                }
                val popularTrackIds = albumArtist?.let { artist ->
                    loadArtistPopularTracksUpdate(
                        sourceId = discovery.sourceId(),
                        artist = artist,
                        loadPopularTracks = discovery.popularTracks,
                    ).tracks.mapTo(mutableSetOf()) { track -> track.id.value }
                }.orEmpty()
                if (generation != albumGeneration) return@onSuccess
                mediaRegistry.updateAlbum(detail)
                stateStore.updateShell { shell ->
                    shell.copy(
                        albumDetail = shell.albumDetail.copy(
                            selectedAlbum = detail.album.toSharedMediaItemUi(
                                coverArtUrl = coverArtUrl,
                                canFavorite = provider.capabilities.supportsAlbumFavorites,
                            ),
                            detail = detail.toSharedAlbumDetailUi(
                                coverArtUrl = coverArtUrl,
                                popularTrackIds = popularTrackIds,
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
        loadSimilar: Boolean = false,
    ) {
        val generation = ++artistGeneration
        mediaRegistry.updateArtist(null)
        val artist = Artist(ArtistId(item.id), item.title)
        navigationController.openArtistDetail(artist, pushCurrentArtist = pushCurrentArtist)
        publishArtistLoading(item)
        val provider = providerSource.current()
        if (provider == null) {
            publishArtistFailure(item, "Connect to Navidrome to load an artist.")
            return
        }
        val nameOnlyCredit = artist.isNameOnlyArtistCredit()
        val coverArtUrl = { id: String? ->
            id?.takeUnless { nameOnlyCredit && it == artist.id.value }?.let(provider::coverArtUrl)
        }
        runCatching {
            if (nameOnlyCredit) loadNameOnlyArtistCreditDetails(provider, artist) else provider.artist(artist.id)
        }
            .onSuccess { detail ->
                if (generation != artistGeneration) return@onSuccess
                navigationController.updateActiveArtist(detail.artist)
                val popular = loadArtistPopularTracksUpdate(
                    sourceId = discovery.sourceId(),
                    artist = detail.artist,
                    loadPopularTracks = discovery.popularTracks,
                )
                val similar = if (loadSimilar) {
                    loadSimilarArtistsUpdate(detail.artist, loadSimilarArtists = discovery.similarArtists)
                } else {
                    app.naviamp.domain.media.SimilarArtistsUpdate(emptyList(), null)
                }
                if (generation != artistGeneration) return@onSuccess
                mediaRegistry.updateArtist(detail, popular.tracks, similar.artists)
                stateStore.updateShell { shell ->
                    shell.copy(
                        artistDetail = shell.artistDetail.copy(
                            selectedArtist = detail.artist.toSharedMediaItemUi(
                                coverArtUrl = coverArtUrl,
                                canFavorite = provider.capabilities.supportsArtistFavorites && !nameOnlyCredit,
                            ),
                            detail = detail.toSharedArtistDetailUi(
                                coverArtUrl = coverArtUrl,
                                popularTracks = popular.tracks,
                                popularTracksStatus = popular.status,
                                similarArtists = similar.artists,
                                similarArtistsStatus = similar.status,
                                similarArtistsExpanded = loadSimilar,
                                canFavoriteArtist = provider.capabilities.supportsArtistFavorites && !nameOnlyCredit,
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
                albumDetail = NaviampAlbumDetailScreenUi(),
                artistDetail = shell.artistDetail.copy(
                    selectedArtist = item,
                    detail = null,
                    status = "Loading ${item.title}...",
                ),
                playlistDetail = NaviampPlaylistDetailScreenUi(),
            )
        }
    }

    private fun publishArtistFailure(item: SharedMediaItemUi, status: String) {
        stateStore.updateShell { shell ->
            shell.copy(artistDetail = shell.artistDetail.copy(selectedArtist = item, detail = null, status = status))
        }
    }
}
