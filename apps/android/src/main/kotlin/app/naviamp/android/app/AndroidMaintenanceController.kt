package app.naviamp.android

import app.naviamp.domain.cache.StorageCacheStats

import android.content.Context
import app.naviamp.domain.app.NaviampContentState
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.app.cacheDataClearedStatus
import app.naviamp.domain.app.databaseResetStatus
import app.naviamp.domain.app.libraryIndexClearedStatus
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.Track
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.PlaybackSettingsMaintenanceController
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.android.playback.AndroidPlaybackEngine
import java.io.File

fun clearAndroidDerivedMediaState(state: AndroidAppState) {
    state.waveformByTrackId = emptyMap()
    state.audioTagsByTrackId = emptyMap()
    state.lyricsByTrackId = emptyMap()
    state.lyricsStatusByTrackId = emptyMap()
    state.radioTrackArtworkByKey = emptyMap()
    state.relatedTracks = emptyList()
    state.relatedTracksSource = RelatedTracksSource.None
    state.relatedSimilarityByTrackId = emptyMap()
    state.artistPopularTracksByArtistId = emptyMap()
    state.artistPopularTracksStatusByArtistId = emptyMap()
    state.artistSimilarArtistsByArtistId = emptyMap()
    state.artistSimilarArtistsStatusByArtistId = emptyMap()
    state.playlistTracksById = emptyMap()
}

fun clearAndroidFileCaches(context: Context) {
    deleteDirectoryContents(File(context.cacheDir, "cover-art"))
    deleteDirectoryContents(File(context.cacheDir, "waveforms"))
}

fun resetAndroidPlaybackState(
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine,
    queueController: PlaybackQueueController,
) {
    playbackEngine.stop()
    state.audioPrefetchJob?.cancel()
    state.audioPrefetchJob = null
    state.sidecarPrepJob?.cancel()
    state.sidecarPrepJob = null
    state.playbackSessionToken += 1
    state.playbackState = PlaybackState.Idle
    state.playbackProgress = PlaybackProgress.Unknown
    state.nowPlaying = null
    state.nowPlayingStation = null
    state.nowPlayingStreamMetadata = PlaybackStreamMetadata()
    state.nowPlayingOpen = false
    state.visualizerFrame = null
    state.visualizerRequestedVisible = false
    queueController.clear()
    state.playbackQueue = queueController.queue
    state.shuffledUpNextSnapshot = null
    state.restoredStartPositionSeconds = null
}

fun handleAndroidClearCache(
    context: Context,
    state: AndroidAppState,
    cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
) {
    cacheMaintenanceRepository.clearCacheData()
    clearAndroidFileCaches(context)
    clearAndroidDerivedMediaState(state)
    state.status = cacheDataClearedStatus()
}

fun handleAndroidClearLibrary(
    state: AndroidAppState,
    libraryIndexRepository: LocalLibraryIndexRepository,
) {
    libraryIndexRepository.clearLibraryData(state.activeSourceId)
    state.homeState = app.naviamp.domain.home.HomeContent()
    state.contentState = NaviampContentState()
    state.tracks = emptyList()
    state.recentPlaylistIds = emptyList()
    clearAndroidDerivedMediaState(state)
    state.status = libraryIndexClearedStatus()
}

fun handleAndroidResetDatabase(
    context: Context,
    state: AndroidAppState,
    cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
    settingsStore: AndroidSettingsStore,
    playbackEngine: AndroidPlaybackEngine,
    queueController: PlaybackQueueController,
) {
    resetAndroidPlaybackState(state, playbackEngine, queueController)
    cacheMaintenanceRepository.clearAll()
    settingsStore.clear()
    clearAndroidFileCaches(context)
    state.provider = null
    state.activeSourceId = null
    state.validation = null
    state.activeTlsSettings = NavidromeTlsSettings()
    state.homeState = app.naviamp.domain.home.HomeContent()
    state.contentState = NaviampContentState()
    state.tracks = emptyList()
    state.recentPlaylistIds = emptyList()
    state.connectionName = ""
    state.serverUrl = ""
    state.username = ""
    state.password = ""
    state.skipTlsVerification = false
    state.customCertificatePath = ""
    state.clientCertificatePath = ""
    state.clientCertificatePassword = ""
    state.editingConnection = true
    state.restoringConnection = false
    state.navigationState = NaviampNavigationState(route = NaviampRoute.Settings)
    clearAndroidDerivedMediaState(state)
    state.status = databaseResetStatus()
}

internal class AndroidSettingsMaintenanceController(
    private val context: Context,
    private val state: AndroidAppState,
    private val storage: AndroidStorageDependencies,
    private val settingsStore: AndroidSettingsStore,
    private val playbackEngine: AndroidPlaybackEngine,
    private val queueController: PlaybackQueueController,
    private val reloadVisibleLyrics: () -> Unit,
    private val redownloadTracks: (List<Track>, String) -> Unit,
    private val onSyncedSettingsChanged: () -> Unit = {},
) {
    private val playbackSettingsMaintenanceController = PlaybackSettingsMaintenanceController(
        playbackEngine = playbackEngine,
        playbackSettings = { state.playbackSettings },
        setPlaybackSettings = { settings -> state.playbackSettings = settings },
        savePlaybackSettings = settingsStore::savePlaybackSettings,
        reloadLyricsSidecars = reloadVisibleLyrics,
        radioDjPresetRepository = storage,
        downloadedTracks = { state.downloadedTracks.map { it.track } },
        redownloadTracks = redownloadTracks,
    )

    fun handleConnectionFormChanged(form: ConnectionFormState) {
        state.applyConnectionForm(form)
    }

    fun handlePlaybackSettingsChanged(settings: PlaybackSettings) {
        playbackSettingsMaintenanceController.applyPlaybackSettings(settings)
        onSyncedSettingsChanged()
    }

    fun handlePlaybackSettingsChangedAndRedownload(settings: PlaybackSettings) {
        playbackSettingsMaintenanceController.applyPlaybackSettingsAndRedownload(settings)
        onSyncedSettingsChanged()
    }

    fun handleCacheSettingsChanged(settings: CacheSettings) {
        state.cacheSettings = settings.normalized()
        settingsStore.saveCacheSettings(state.cacheSettings)
        storage.updateAudioCacheLimit(state.cacheSettings.maxAudioCacheBytes)
        state.storageStats = storage.stats()
    }

    fun handleClearCache() {
        handleAndroidClearCache(context, state, storage)
    }

    fun handleClearLibrary() {
        handleAndroidClearLibrary(state, storage)
    }

    fun handleResetDatabase() {
        handleAndroidResetDatabase(
            context = context,
            state = state,
            cacheMaintenanceRepository = storage,
            settingsStore = settingsStore,
            playbackEngine = playbackEngine,
            queueController = queueController,
        )
    }
}
