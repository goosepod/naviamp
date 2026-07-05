package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.app.shouldRefreshStorageStats
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.provider.PlaylistDetailRefreshIntervalMillis
import app.naviamp.domain.provider.playlistDetailAutoRefreshTarget
import app.naviamp.domain.provider.runPlaylistDetailAutoRefresh
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun DesktopAppControllerEffects(
    nowPlayingController: DesktopNowPlayingController,
    playlistsController: DesktopPlaylistsController,
    searchController: DesktopSearchController,
    libraryController: DesktopLibraryController,
    mixBuilderController: DesktopMixBuilderController,
    hasSavedConnection: Boolean,
    connectToServer: () -> Unit,
    nowPlayingTrack: Track?,
    connectedSourceId: String?,
    connectedProvider: NavidromeProvider?,
    playbackEngine: PlaybackEngine,
    nowPlayingWaveformReloadToken: Int,
    cacheSettings: CacheSettings,
    playbackSettings: PlaybackSettings,
    nowPlayingLyricsVisible: Boolean,
    selectedVisualizer: NaviampVisualizer,
    appRoute: DesktopAppRoute,
    selectedPlaylist: Playlist?,
    homeContent: HomeContent,
    showStatsForNerds: Boolean,
    statsForNerdsRefreshTick: Int,
    incrementStatsForNerdsRefreshTick: () -> Unit,
    downloadRefreshToken: Int,
    mediaSourcesRevision: Int,
    loadStorageStats: suspend () -> StorageCacheStats,
    setCacheStats: (StorageCacheStats) -> Unit,
) {
    LaunchedEffect(
        nowPlayingTrack?.id,
        connectedSourceId,
        connectedProvider,
        playbackEngine,
        nowPlayingWaveformReloadToken,
        cacheSettings.audioCachingEnabled,
        playbackSettings.lrclibLyricsEnabled,
        nowPlayingLyricsVisible,
        selectedVisualizer,
        appRoute,
    ) {
        nowPlayingController.loadNowPlayingAnalysis()
    }

    LaunchedEffect(
        nowPlayingTrack?.id,
        connectedSourceId,
        connectedProvider,
        playbackSettings.sonicSimilarityEnabled,
    ) {
        nowPlayingController.loadRelatedTracks()
    }

    LaunchedEffect(nowPlayingController, nowPlayingTrack?.id, connectedProvider) {
        nowPlayingController.preloadCoverArt()
    }

    LaunchedEffect(connectedProvider, appRoute, selectedPlaylist?.id) {
        val target = playlistDetailAutoRefreshTarget(
            provider = connectedProvider,
            playlist = selectedPlaylist,
            enabled = appRoute == DesktopAppRoute.PlaylistDetail,
        ) ?: return@LaunchedEffect
        runPlaylistDetailAutoRefresh(
            target = target,
            waitForNextRefresh = {
                delay(PlaylistDetailRefreshIntervalMillis)
            },
        ) { provider, playlist ->
            playlistsController.refreshPlaylistDetailsFromServer(
                activeProvider = provider,
                playlist = playlist,
                showLoadingStatus = false,
            )
        }
    }

    LaunchedEffect(Unit) {
        if (hasSavedConnection) {
            connectToServer()
        }
    }

    LaunchedEffect(searchController.query, connectedProvider) {
        searchController.loadSearchResults(searchController.query)
    }

    LaunchedEffect(libraryController.query, connectedSourceId) {
        libraryController.refreshAfterQueryOrSourceChange()
    }

    LaunchedEffect(connectedSourceId, homeContent.artists) {
        if (connectedSourceId != null && mixBuilderController.artistSuggestionsEmpty) {
            mixBuilderController.refreshArtistInitialSuggestions()
        }
    }

    LaunchedEffect(connectedSourceId, homeContent.randomAlbums, homeContent.mixAlbums) {
        if (connectedSourceId != null && mixBuilderController.albumSuggestionsEmpty) {
            mixBuilderController.refreshAlbumInitialSuggestions()
        }
    }

    LaunchedEffect(connectedSourceId, homeContent.genres) {
        if (connectedSourceId != null && mixBuilderController.genreSuggestionsEmpty) {
            mixBuilderController.refreshGenreSuggestions()
        }
    }

    LaunchedEffect(showStatsForNerds) {
        while (showStatsForNerds) {
            delay(1_000)
            incrementStatsForNerdsRefreshTick()
        }
    }

    LaunchedEffect(showStatsForNerds, appRoute, statsForNerdsRefreshTick, downloadRefreshToken, mediaSourcesRevision) {
        if (shouldRefreshStorageStats(appRoute.toNaviampRoute(), diagnosticsVisible = showStatsForNerds)) {
            setCacheStats(withContext(Dispatchers.IO) { loadStorageStats() })
        }
    }
}
