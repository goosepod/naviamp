package app.naviamp.desktop

import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.cache.downloadedAudioQualityLabel
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.NaviampAboutUi
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.NaviampDownloadsScreenUi
import app.naviamp.ui.NaviampHomeScreenUi
import app.naviamp.ui.NaviampInternetRadioScreenUi
import app.naviamp.ui.NaviampLibraryScreenUi
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampOfflineDashboardUi
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistsScreenUi
import app.naviamp.ui.NaviampSearchScreenUi
import app.naviamp.ui.NaviampShellCapabilitiesUi
import app.naviamp.ui.NaviampShellChromeUi
import app.naviamp.ui.NaviampShellConnectionUi
import app.naviamp.ui.toCacheSettingsUi
import app.naviamp.ui.toConnectionSettingsUi
import app.naviamp.ui.toDownloadJobUi
import app.naviamp.ui.toDownloadedTrackUi
import app.naviamp.ui.toGeneralSettingsUi
import app.naviamp.ui.toInternetRadioStationUi
import app.naviamp.ui.toPlaybackSettingsUi
import app.naviamp.ui.toSharedAlbumDetailUi
import app.naviamp.ui.toSharedArtistDetailUi
import app.naviamp.ui.toSharedHomeUi
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedPlaylistDetailUi
import app.naviamp.ui.toSharedSearchResultsUi
import app.naviamp.ui.totalDownloadBytes

internal data class DesktopAppShellStateContext(
    val capabilities: NaviampShellCapabilitiesUi,
    val connection: NaviampShellConnectionUi,
    val connectedSourceId: String?,
    val provider: NavidromeProvider?,
    val route: NaviampRoute,
    val about: NaviampAboutUi,
    val playbackEngine: PlaybackEngine,
    val interfaceSettings: InterfaceSettings,
    val playbackSettings: PlaybackSettings,
    val cacheSettings: CacheSettings,
    val cacheStats: StorageCacheStats,
    val homeContent: HomeContent,
    val homeController: DesktopHomeController,
    val downloadsController: DesktopDownloadsController,
    val downloadedTracks: List<DownloadedTrack>,
    val mixBuilderController: DesktopMixBuilderController,
    val sonicPathController: DesktopSonicPathController,
    val sonicMixController: DesktopSonicMixController,
    val sonicHomeDiscoveryController: DesktopSonicHomeDiscoveryController,
    val albumController: DesktopAlbumController,
    val artistController: DesktopArtistController,
    val playlistsController: DesktopPlaylistsController,
    val libraryController: DesktopLibraryController,
    val searchController: DesktopSearchController,
    val internetRadioController: DesktopInternetRadioController,
)

internal fun desktopAppShellUiState(context: DesktopAppShellStateContext): NaviampAppShellUiState =
    with(context) {
        val coverArtUrl: (String?) -> String? = { id -> id?.let { provider?.coverArtUrl(it) } }
        val connectionPageStatus = connection.status?.takeUnless { status ->
            status.startsWith("Connected to Navidrome", ignoreCase = true) ||
                status.startsWith("Connected to ", ignoreCase = true)
        }
        val downloadItems = downloadedTracks.map { download ->
            download.track.toDownloadedTrackUi(
                id = download.path.toString(),
                sizeBytes = download.sizeBytes,
                qualityLabel = downloadedAudioQualityLabel(
                    download.qualityKey,
                    download.track.audioInfo,
                    download.contentType,
                ),
                coverArtUrl = coverArtUrl,
            )
        }
        val keepDownloadedIds = downloadsController.keepDownloadedPolicies
            .mapTo(mutableSetOf()) { it.collectionId }
        val selectedPlaylistKeepDownloaded = playlistsController.selectedPlaylist?.id in keepDownloadedIds

        NaviampAppShellUiState(
            capabilities = capabilities,
            connectionSettings = connection.toConnectionSettingsUi(
                capabilities = capabilities,
                currentSourceId = connectedSourceId,
            ),
            general = interfaceSettings.toGeneralSettingsUi(about),
            playback = playbackSettings.toPlaybackSettingsUi(
                capabilities = capabilities,
                audioOutputDeviceSelectionAvailable =
                    (playbackEngine as? AudioOutputDevicePlaybackEngine)
                        ?.supportsAudioOutputDeviceSelection == true,
                audioOutputDevices =
                    (playbackEngine as? AudioOutputDevicePlaybackEngine)?.outputDevices().orEmpty(),
                downloadBytes = cacheStats.downloadBytes,
            ),
            cache = cacheSettings.toCacheSettingsUi(cacheStats, capabilities),
            shellChrome = NaviampShellChromeUi(
                selectedRoute = route.toSharedRoute(),
                supportsDownloads = capabilities.downloads,
                supportsApplicationUpdates = capabilities.applicationUpdates,
            ),
            home = NaviampHomeScreenUi(
                content = homeContent.toSharedHomeUi(
                    coverArtUrl = coverArtUrl,
                    playlistTracksById = playlistsController.playlistTracksById,
                    sonicDiscoveryRows = sonicHomeDiscoveryController.rows,
                    canFavoriteAlbums = true,
                    showSonicPathBuilder =
                        playbackSettings.sonicSimilarityEnabled && capabilities.sonicSimilarity,
                    showSonicMixBuilder =
                        playbackSettings.sonicSimilarityEnabled && capabilities.sonicSimilarity,
                ),
                refreshing = homeController.refreshing,
            ),
            downloads = NaviampDownloadsScreenUi(
                downloads = downloadItems,
                status = downloadsController.status,
                jobs = downloadsController.downloadJobs.map { it.toDownloadJobUi() },
                downloadBytes = downloadItems.totalDownloadBytes(),
                maxDownloadBytes = cacheSettings.maxDownloadBytes,
                offlineDashboard = NaviampOfflineDashboardUi(
                    audioCacheCount = cacheStats.audioCount,
                    audioCacheBytes = cacheStats.audioBytes,
                    maxAudioCacheBytes = cacheSettings.maxAudioCacheBytes,
                ),
                keepFavoritesDownloaded = downloadsController.keepDownloadedPolicies.any {
                    it.kind == app.naviamp.domain.cache.KeepDownloadedCollectionKind.Favorites
                },
            ),
            artistMixBuilder = mixBuilderController.artistUi(coverArtUrl),
            albumMixBuilder = mixBuilderController.albumUi(coverArtUrl),
            genreMixBuilder = mixBuilderController.genreUi(),
            sonicPathBuilder = sonicPathController.ui(coverArtUrl),
            sonicMixBuilder = sonicMixController.ui(coverArtUrl),
            albumDetail = NaviampAlbumDetailScreenUi(
                selectedAlbum = albumController.selectedAlbum?.toSharedMediaItemUi(coverArtUrl, canFavorite = true),
                detail = albumController.selectedAlbumDetails?.toSharedAlbumDetailUi(
                    coverArtUrl = coverArtUrl,
                    popularTrackIds = artistController.selectedArtistPopularTracks
                        .mapTo(mutableSetOf()) { it.id.value },
                    canFavoriteAlbum = true,
                ),
                status = albumController.selectedAlbumStatus,
            ),
            artistDetail = NaviampArtistDetailScreenUi(
                selectedArtist = artistController.selectedArtist?.toSharedMediaItemUi(coverArtUrl, canFavorite = true),
                detail = artistController.selectedArtistDetails?.toSharedArtistDetailUi(
                    coverArtUrl = coverArtUrl,
                    popularTracks = artistController.selectedArtistPopularTracks,
                    popularTracksStatus = artistController.selectedArtistPopularTracksStatus,
                    similarArtists = artistController.selectedArtistSimilarArtists,
                    similarArtistsStatus = artistController.selectedArtistSimilarArtistsStatus,
                    canFavoriteArtist = true,
                    canFavoriteAlbums = true,
                ),
                status = artistController.selectedArtistStatus,
            ),
            playlists = NaviampPlaylistsScreenUi(
                playlists = playlistsController.playlists.map { playlist ->
                    playlist.toSharedMediaItemUi(
                        coverArtUrl = coverArtUrl,
                        tracks = playlistsController.playlistTracksById[playlist.id].orEmpty(),
                        keepDownloadedActive = playlist.id in keepDownloadedIds,
                    )
                },
                recentPlaylistIds = playlistsController.recentPlaylistIds,
                sortMode = playlistsController.sortMode,
                status = playlistsController.status ?: connectionPageStatus,
                availableLibraries = connection.availableMusicFolders,
                selectedConnectionLibraryIds = connection.form.selectedMusicFolderIds,
            ),
            playlistDetail = NaviampPlaylistDetailScreenUi(
                selectedPlaylist = playlistsController.selectedPlaylist?.toSharedMediaItemUi(
                    coverArtUrl = coverArtUrl,
                    tracks = playlistsController.selectedPlaylistTracks,
                    keepDownloadedActive = selectedPlaylistKeepDownloaded,
                ),
                detail = playlistsController.selectedPlaylist?.toSharedPlaylistDetailUi(
                    tracks = playlistsController.selectedPlaylistTracks,
                    coverArtUrl = coverArtUrl,
                    keepDownloadedActive = selectedPlaylistKeepDownloaded,
                ),
                status = playlistsController.selectedPlaylistStatus
                    ?: playlistsController.status
                    ?: connectionPageStatus,
                availableLibraries = connection.availableMusicFolders,
                selectedConnectionLibraryIds = connection.form.selectedMusicFolderIds,
            ),
            library = NaviampLibraryScreenUi(
                artists = libraryController.snapshot.artists.map { artist ->
                    artist.toSharedMediaItemUi(coverArtUrl, canFavorite = true)
                },
                query = libraryController.query,
                syncStatus = NaviampLibrarySyncStatusUi(
                    message = libraryController.status ?: connectionPageStatus,
                    isSyncing = libraryController.syncing,
                ),
            ),
            search = NaviampSearchScreenUi(
                query = searchController.query,
                results = searchController.results.toSharedSearchResultsUi(
                    coverArtUrl = coverArtUrl,
                    canFavoriteArtists = true,
                    canFavoriteAlbums = true,
                ),
                status = searchController.status,
                searching = searchController.searching,
            ),
            radio = NaviampInternetRadioScreenUi(
                stations = internetRadioController.stations.map { it.toInternetRadioStationUi() },
                status = internetRadioController.status ?: connectionPageStatus,
            ),
        )
    }
