package app.naviamp.presentation

import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.media.loadArtistPopularTracksUpdate
import app.naviamp.domain.media.ArtistDiscographyAppearances
import app.naviamp.domain.media.loadSimilarArtistsUpdate
import app.naviamp.domain.media.isNameOnlyArtistCredit
import app.naviamp.domain.media.loadNameOnlyArtistCreditDetails
import app.naviamp.domain.media.nameOnlyArtistCredit
import app.naviamp.domain.media.reconciledAppearances
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import app.naviamp.domain.media.loadArtistPlaylists
import app.naviamp.ui.SharedArtistPlaylistsUi
import app.naviamp.ui.toSharedTrackRowUi

data class NaviampCoreArtistDiscoveryServices(
    val sourceId: () -> String? = { null },
    val popularTracks: suspend (String, Artist, Int) -> List<ArtistPopularTrackMatch> = { _, _, _ -> emptyList() },
    val similarArtists: suspend (Artist, Int) -> List<SimilarArtistMatch> = { _, _ -> emptyList() },
    val discographyAppearances: (
        sourceId: String,
        artistId: ArtistId,
        primaryAlbumIds: Set<AlbumId>,
    ) -> ArtistDiscographyAppearances = { _, _, _ -> ArtistDiscographyAppearances() },
    val artistTopTracks: suspend (String, Artist, Int) -> List<ArtistPopularTrackMatch> = popularTracks,
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
        artistTopTracks = { source, artist, limit ->
            popular.popularTracks(source, artist, limit, ensureRequestedLimit = true)
        },
        similarArtists = similar::similarArtists,
        discographyAppearances = { activeSourceId, artistId, primaryAlbumIds ->
            libraryIndex.artistDiscographyAppearances(activeSourceId, artistId, primaryAlbumIds)
        },
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
    private val playbackProfiles: NaviampCorePlaybackProfileController =
        NaviampCorePlaybackProfileController(stateStore),
) : NaviampCoreCommandController, NaviampCoreArtistNavigator {
    private var albumGeneration = 0L
    private var artistGeneration = 0L
    private var artistPlaylistsJob: Job? = null
    private var artistPlaylistsGeneration = 0L

    private fun cancelArtistPlaylists() {
        artistPlaylistsGeneration++
        artistPlaylistsJob?.cancel()
        artistPlaylistsJob = null
    }

    private fun refreshArtistPlaylists(artistId: String) {
        val detail = stateStore.state.value.shell.artistDetail.detail ?: return
        if (detail.artist.id != artistId || !detail.playlistsVisible) return
        val provider = providerSource.current() ?: return
        cancelArtistPlaylists()
        val generation = artistPlaylistsGeneration
        val artist = mediaRegistry.artistDetails?.artist ?: return
        artistPlaylistsJob = scope.launch {
            loadArtistPlaylists(provider, artist, publish = { result ->
                if (generation == artistPlaylistsGeneration && providerSource.isCurrent(provider)) {
                    stateStore.updateShell { shell ->
                        val current = shell.artistDetail.detail
                        if (current?.artist?.id != artistId || !current.playlistsVisible) shell
                        else shell.copy(artistDetail = shell.artistDetail.copy(detail = current.copy(
                            playlists = SharedArtistPlaylistsUi(
                                items = result.playlists.map { it.toSharedMediaItemUi(coverArtUrl = { id -> id?.let(provider::coverArtUrl) }) },
                                loading = result.loading, failed = result.failed, truncated = result.truncated,
                            ),
                        )))
                    }
                }
            }, isCurrent = {
                generation == artistPlaylistsGeneration && providerSource.isCurrent(provider) &&
                    stateStore.state.value.shell.artistDetail.detail?.let { it.artist.id == artistId && it.playlistsVisible } == true
            })
        }
    }

    private var topTracksGeneration = 0L
    private var topTracksJob: Job? = null
    private var topTracksPreferences = stateStore.state.value.shell.general.interfaceSettings.normalized().let {
        it.showArtistTopTracks to it.artistTopTracksLimit
    }

    private fun cancelArtistTopTracks() {
        topTracksGeneration++
        topTracksJob?.cancel()
        topTracksJob = null
    }

    private fun refreshArtistTopTracks() {
        cancelArtistTopTracks()
        val settings = stateStore.state.value.shell.general.interfaceSettings.normalized()
        topTracksPreferences = settings.showArtistTopTracks to settings.artistTopTracksLimit
        if (!settings.showArtistTopTracks) return
        val provider = providerSource.current() ?: return
        val artist = mediaRegistry.artistDetails?.artist ?: return
        val generation = topTracksGeneration
        val artistLoad = artistGeneration
        topTracksJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val popular = loadArtistPopularTracksUpdate(
                sourceId = discovery.sourceId(), artist = artist,
                fetchLimit = maxOf(app.naviamp.domain.media.ArtistDetailPopularTracksFetchLimit, settings.artistTopTracksLimit),
                displayLimit = settings.artistTopTracksLimit, loadPopularTracks = discovery.artistTopTracks,
            )
            if (generation != topTracksGeneration || artistLoad != artistGeneration || !providerSource.isCurrent(provider)) return@launch
            mediaRegistry.updateArtistPopularTracks(popular.tracks)
            stateStore.updateShell { shell ->
                val current = shell.artistDetail.detail
                if (current?.artist?.id != artist.id.value || !current.popularTracksVisible) shell
                else shell.copy(artistDetail = shell.artistDetail.copy(detail = current.copy(
                    popularTracks = popular.tracks.map { it.toSharedTrackRowUi(coverArtUrl = { id -> id?.let(provider::coverArtUrl) }).copy(hasArtist = false) },
                    popularTracksStatus = popular.status,
                )))
            }
        }
    }

    fun resetForSourceChange() {
        cancelArtistPlaylists()
        cancelArtistTopTracks()
        albumGeneration++
        artistGeneration++
        mediaRegistry.updateAlbum(null)
        mediaRegistry.updateArtist(null)
        stateStore.updateShell { shell -> shell.copy(
            albumDetail = app.naviamp.ui.NaviampAlbumDetailScreenUi(),
            artistDetail = app.naviamp.ui.NaviampArtistDetailScreenUi(),
        ) }
    }

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.Detail.RefreshArtistPlaylists -> NaviampCoreImmediateCommandResult.Deferred
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
            is NaviampCoreCommand.Detail.RefreshArtistPlaylists -> refreshArtistPlaylists(command.artistId)
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

    internal fun interfaceSettingsChanged(settings: app.naviamp.domain.settings.InterfaceSettings) {
        val previousVisibility = stateStore.state.value.shell.artistDetail.detail?.playlistsVisible
        if (!settings.showArtistPlaylists) cancelArtistPlaylists()
        val normalized = settings.normalized()
        val nextTopTracksPreferences = normalized.showArtistTopTracks to normalized.artistTopTracksLimit
        val reloadTopTracks = nextTopTracksPreferences != topTracksPreferences
        topTracksPreferences = nextTopTracksPreferences
        if (reloadTopTracks) cancelArtistTopTracks()
        val provider = providerSource.current() ?: return
        stateStore.updateShell { shell ->
            val coverArtUrl = { id: String? -> id?.let(provider::coverArtUrl) }
            val albumDetail = mediaRegistry.albumDetails
                ?.takeIf { shell.albumDetail.selectedAlbum != null }
                ?.let { details ->
                val popularTrackIds = shell.albumDetail.detail?.tracks
                    .orEmpty()
                    .filter { it.popular }
                    .mapTo(mutableSetOf()) { it.id }
                shell.albumDetail.copy(
                    selectedAlbum = details.album.toSharedMediaItemUi(
                        coverArtUrl = coverArtUrl,
                        canFavorite = provider.capabilities.supportsAlbumFavorites,
                    ),
                    detail = details.toSharedAlbumDetailUi(
                        coverArtUrl = coverArtUrl,
                        popularTrackIds = popularTrackIds,
                        canFavoriteAlbum = provider.capabilities.supportsAlbumFavorites,
                        showAlbumInformation = settings.showAlbumInformation,
                    ),
                )
            } ?: shell.albumDetail
            val artistDetail = mediaRegistry.artistDetails
                ?.takeIf { shell.artistDetail.selectedArtist != null }
                ?.let { details ->
                val current = shell.artistDetail.detail
                val nameOnlyCredit = details.artist.isNameOnlyArtistCredit()
                val artistCoverArtUrl = { id: String? ->
                    id?.takeUnless { nameOnlyCredit && it == details.artist.id.value }?.let(provider::coverArtUrl)
                }
                shell.artistDetail.copy(
                    detail = details.toSharedArtistDetailUi(
                        coverArtUrl = artistCoverArtUrl,
                        appearanceAlbums = mediaRegistry.artistAppearanceAlbums,
                        appearanceTracks = mediaRegistry.artistAppearanceTracks,
                        appearanceLoadFailed = current?.appearanceLoadFailed == true,
                        appearancesTruncated = current?.appearancesTruncated == true,
                        popularTracks = mediaRegistry.artistPopularTracks,
                        showArtistTopTracks = normalized.showArtistTopTracks,
                        artistTopTracksLimit = normalized.artistTopTracksLimit,
                        popularTracksStatus = current?.popularTracksStatus,
                        similarArtists = mediaRegistry.artistSimilarArtists,
                        similarArtistsStatus = current?.similarArtistsStatus,
                        similarArtistsExpanded = current?.similarArtistsExpanded == true,
                        canFavoriteArtist = provider.capabilities.supportsArtistFavorites && !nameOnlyCredit,
                        canFavoriteAlbums = provider.capabilities.supportsAlbumFavorites,
                        showArtistInformation = settings.showArtistInformation,
                    ).copy(
                        playlistsVisible = settings.showArtistPlaylists,
                        playlists = if (settings.showArtistPlaylists) current?.playlists ?: SharedArtistPlaylistsUi() else SharedArtistPlaylistsUi(),
                    ),
                )
            } ?: shell.artistDetail
            shell.copy(albumDetail = albumDetail, artistDetail = artistDetail)
        }
        if (settings.showArtistPlaylists && previousVisibility == false) {
            stateStore.state.value.shell.artistDetail.detail?.artist?.id?.let(::refreshArtistPlaylists)
        }
        if (reloadTopTracks) refreshArtistTopTracks()
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
                mediaRegistry.artistAppearanceAlbums,
                mediaRegistry.artistAppearanceTracks,
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
            mediaRegistry.artistAppearanceAlbums,
            mediaRegistry.artistAppearanceTracks,
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
        val albumId = AlbumId(item.id)
        runCatching { provider.album(albumId) }
            .onSuccess { detail ->
                if (generation != albumGeneration || !providerSource.isCurrent(provider)) return@onSuccess
                val albumArtist = detail.tracks.firstOrNull()?.let { track ->
                    track.artistId?.let { Artist(it, track.artistName) }
                        ?: track.artistName.takeIf(String::isNotBlank)?.let(::nameOnlyArtistCredit)
                }
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
                                popularTrackIds = emptySet(),
                                canFavoriteAlbum = provider.capabilities.supportsAlbumFavorites,
                                showAlbumInformation = shell.general.interfaceSettings.showAlbumInformation,
                            ),
                            status = "Connected.",
                            playbackProfile = playbackProfiles.albumProfile(detail.album.id.value),
                            playbackProfileStatus = null,
                        ),
                    )
                }
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val info = runCatching { provider.albumInfo(albumId) }.getOrNull() ?: return@launch
                    if (generation != albumGeneration || !providerSource.isCurrent(provider)) return@launch
                    val enrichedDetail = detail.copy(info = info)
                    mediaRegistry.updateAlbum(enrichedDetail)
                    stateStore.updateShell { shell ->
                        if (generation != albumGeneration || !providerSource.isCurrent(provider)) {
                            shell
                        } else {
                            val popularTrackIds = shell.albumDetail.detail?.tracks
                                .orEmpty()
                                .filter { it.popular }
                                .mapTo(mutableSetOf()) { it.id }
                            shell.copy(
                                albumDetail = shell.albumDetail.copy(
                                    detail = enrichedDetail.toSharedAlbumDetailUi(
                                        coverArtUrl = coverArtUrl,
                                        popularTrackIds = popularTrackIds,
                                        canFavoriteAlbum = provider.capabilities.supportsAlbumFavorites,
                                        showAlbumInformation = shell.general.interfaceSettings.showAlbumInformation,
                                    ),
                                ),
                            )
                        }
                    }
                }
                val popularTrackIds = albumArtist?.let { artist ->
                    loadArtistPopularTracksUpdate(
                        sourceId = discovery.sourceId(),
                        artist = artist,
                        loadPopularTracks = discovery.popularTracks,
                    ).tracks.mapTo(mutableSetOf()) { track -> track.id.value }
                }.orEmpty()
                if (generation != albumGeneration || !providerSource.isCurrent(provider)) return@onSuccess
                val latestDetail = mediaRegistry.albumDetails
                    ?.takeIf { loaded -> loaded.album.id == detail.album.id }
                    ?: detail
                stateStore.updateShell { shell ->
                    shell.copy(
                        albumDetail = shell.albumDetail.copy(
                            detail = latestDetail.toSharedAlbumDetailUi(
                                coverArtUrl = coverArtUrl,
                                popularTrackIds = popularTrackIds,
                                canFavoriteAlbum = provider.capabilities.supportsAlbumFavorites,
                                showAlbumInformation = shell.general.interfaceSettings.showAlbumInformation,
                            ),
                        ),
                    )
                }
            }
            .onFailure { cause ->
                if (generation == albumGeneration && providerSource.isCurrent(provider)) {
                    publishAlbumFailure(item, cause.message ?: "Could not load album.")
                }
            }
    }

    private suspend fun loadArtist(
        item: SharedMediaItemUi,
        pushCurrentArtist: Boolean = true,
        loadSimilar: Boolean = false,
    ) {
        cancelArtistPlaylists()
        cancelArtistTopTracks()
        val generation = ++artistGeneration
        mediaRegistry.updateArtist(null)
        val artist = Artist(ArtistId(item.id), item.title)
        publishArtistLoading(item)
        navigationController.openArtistDetail(artist, pushCurrentArtist = pushCurrentArtist)
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
            if (nameOnlyCredit) {
                app.naviamp.domain.media.ArtistDiscography(primary = loadNameOnlyArtistCreditDetails(provider, artist))
            } else {
                provider.artistDiscography(artist.id)
            }
        }.map { providerDiscography ->
            if (generation != artistGeneration || !providerSource.isCurrent(provider)) return@map providerDiscography
            val combined = if (nameOnlyCredit || provider.capabilities.supportsArtistDiscography) {
                providerDiscography
            } else {
                val stored = discovery.sourceId()?.let { sourceId ->
                    runCatching {
                        discovery.discographyAppearances(sourceId, artist.id,
                            providerDiscography.primary.albums.mapTo(mutableSetOf()) { it.id })
                    }.getOrElse { ArtistDiscographyAppearances(failed = true) }
                } ?: ArtistDiscographyAppearances()
                providerDiscography.copy(
                    appearanceAlbums = stored.albums,
                    appearanceTracks = stored.tracks,
                    appearanceLoadFailed = stored.failed,
                    appearancesTruncated = stored.truncated,
                )
            }
            combined.reconciledAppearances()
        }
            .onSuccess { discography ->
                val detail = discography.primary
                if (generation != artistGeneration || !providerSource.isCurrent(provider)) return@onSuccess
                navigationController.updateActiveArtist(detail.artist)
                val popular = app.naviamp.domain.media.ArtistPopularTracksUpdate(emptyList(), null)
                val similar = if (loadSimilar) {
                    loadSimilarArtistsUpdate(detail.artist, loadSimilarArtists = discovery.similarArtists)
                } else {
                    app.naviamp.domain.media.SimilarArtistsUpdate(emptyList(), null)
                }
                if (generation != artistGeneration || !providerSource.isCurrent(provider)) return@onSuccess
                mediaRegistry.updateArtist(
                    detail,
                    popular.tracks,
                    similar.artists,
                    discography.appearanceAlbums,
                    discography.appearanceTracks,
                )
                stateStore.updateShell { shell ->
                    shell.copy(
                        artistDetail = shell.artistDetail.copy(
                            selectedArtist = detail.artist.toSharedMediaItemUi(
                                coverArtUrl = coverArtUrl,
                                canFavorite = provider.capabilities.supportsArtistFavorites && !nameOnlyCredit,
                            ),
                            detail = detail.toSharedArtistDetailUi(
                                coverArtUrl = coverArtUrl,
                                appearanceAlbums = discography.appearanceAlbums,
                                appearanceTracks = discography.appearanceTracks,
                                appearanceLoadFailed = discography.appearanceLoadFailed,
                                appearancesTruncated = discography.appearancesTruncated,
                                popularTracks = popular.tracks,
                                showArtistTopTracks = shell.general.interfaceSettings.showArtistTopTracks,
                                artistTopTracksLimit = shell.general.interfaceSettings.artistTopTracksLimit,
                                popularTracksStatus = popular.status,
                                similarArtists = similar.artists,
                                similarArtistsStatus = similar.status,
                                similarArtistsExpanded = loadSimilar,
                                canFavoriteArtist = provider.capabilities.supportsArtistFavorites && !nameOnlyCredit,
                                canFavoriteAlbums = provider.capabilities.supportsAlbumFavorites,
                                showArtistInformation = shell.general.interfaceSettings.showArtistInformation,
                            ).copy(playlistsVisible = shell.general.interfaceSettings.showArtistPlaylists),
                            status = if (detail.albums.isEmpty()) {
                                "No albums found for ${detail.artist.name}."
                            } else {
                                "Connected."
                            },
                        ),
                    )
                }
                refreshArtistPlaylists(detail.artist.id.value)
                refreshArtistTopTracks()
            }
            .onFailure { cause ->
                if (generation == artistGeneration && providerSource.isCurrent(provider)) {
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
