package app.naviamp.presentation

import app.naviamp.domain.app.cacheDataClearedStatus
import app.naviamp.domain.app.databaseResetStatus
import app.naviamp.domain.app.libraryIndexClearedStatus
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.library.librarySyncCompletedStatus
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.ui.toCacheSettingsUi
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.NaviampDownloadsScreenUi
import app.naviamp.ui.NaviampHomeScreenUi
import app.naviamp.ui.NaviampInternetRadioScreenUi
import app.naviamp.ui.NaviampLibraryScreenUi
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistsScreenUi
import app.naviamp.ui.NaviampSearchScreenUi
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedSonicMixBuilderUi
import app.naviamp.ui.SharedSonicPathBuilderUi

fun interface NaviampCoreInterfaceSettingsStore {
    fun save(settings: InterfaceSettings)
}

fun interface NaviampCoreCacheSettingsPort {
    fun apply(settings: CacheSettings): CacheSettings
}

enum class NaviampCoreMaintenanceOperation {
    ClearCache,
    ClearLibrary,
    RefreshLibrary,
    ResetDatabase,
}

data class NaviampCoreMaintenanceResult(
    val status: String,
    val storageStats: StorageCacheStats? = null,
)

/** Narrow I/O boundary. Core chooses the operation, sequencing, and user-facing result. */
fun interface NaviampCoreMaintenancePort {
    suspend fun run(operation: NaviampCoreMaintenanceOperation): NaviampCoreMaintenanceResult
}

/** Connects portable repositories to Core-owned maintenance sequencing and status policy. */
fun naviampCoreRepositoryMaintenancePort(
    repository: CacheMaintenanceRepository<StorageCacheStats>,
    libraryIndex: LocalLibraryIndexRepository,
    sourceId: () -> String?,
): NaviampCoreMaintenancePort = NaviampCoreMaintenancePort { operation ->
    when (operation) {
        NaviampCoreMaintenanceOperation.ClearCache -> {
            repository.clearCacheData()
            NaviampCoreMaintenanceResult(cacheDataClearedStatus(detailed = true), repository.stats())
        }
        NaviampCoreMaintenanceOperation.ClearLibrary -> {
            libraryIndex.clearLibraryData(sourceId())
            NaviampCoreMaintenanceResult(libraryIndexClearedStatus(detailed = true), repository.stats())
        }
        NaviampCoreMaintenanceOperation.ResetDatabase -> {
            repository.clearAll()
            NaviampCoreMaintenanceResult(databaseResetStatus(savedServersRemoved = true), repository.stats())
        }
        NaviampCoreMaintenanceOperation.RefreshLibrary ->
            error("Library refresh is owned by the Core catalog controller.")
    }
}

/** Owns settings normalization, persistence intent, consequences, overlays, and maintenance state. */
class NaviampCoreSettingsController(
    private val stateStore: NaviampCoreStateStore,
    private val interfaceStore: NaviampCoreInterfaceSettingsStore,
    private val playbackSettings: NaviampCorePlaybackSettingsPort,
    private val cacheSettings: NaviampCoreCacheSettingsPort,
    private val maintenancePort: NaviampCoreMaintenancePort,
    private val refreshLibrary: suspend () -> Unit = {},
    private val onDatabaseReset: suspend () -> Unit = {},
    private val onLocalSettingsChanged: () -> Unit = {},
) : NaviampCoreCommandController {
    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        val settings = command as? NaviampCoreCommand.Settings
            ?: return NaviampCoreImmediateCommandResult.Unhandled
        when (settings) {
            is NaviampCoreCommand.Settings.ChangeInterface -> changeInterface(settings)
            is NaviampCoreCommand.Settings.ChangePlayback -> changePlayback(settings)
            is NaviampCoreCommand.Settings.ChangeCache -> changeCache(settings.settings)
            is NaviampCoreCommand.Settings.ChangeDownloadLocation -> {
                val current = stateStore.state.value.shell.cache.settings
                changeCache(current.copy(customDownloadDirectory = settings.location.path))
            }
            is NaviampCoreCommand.Settings.ChangeAudioCacheLocation -> {
                val current = stateStore.state.value.shell.cache.settings
                changeCache(current.copy(customAudioCacheDirectory = settings.location.path))
            }
            NaviampCoreCommand.Settings.OpenStats -> updateStatsVisibility(true)
            NaviampCoreCommand.Settings.CloseStats -> updateStatsVisibility(false)
            NaviampCoreCommand.Settings.ClearCache,
            NaviampCoreCommand.Settings.ClearLibrary,
            NaviampCoreCommand.Settings.RefreshLibrary,
            NaviampCoreCommand.Settings.ResetDatabase,
            -> return NaviampCoreImmediateCommandResult.Deferred
        }
        return NaviampCoreImmediateCommandResult.Handled()
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        if (command == NaviampCoreCommand.Settings.RefreshLibrary) {
            refreshLibrary()
            stateStore.update { state ->
                state.copy(overlays = state.overlays.copy(status = librarySyncCompletedStatus()))
            }
            return NaviampCoreCommandResult.Completed
        }
        val operation = when (command) {
            NaviampCoreCommand.Settings.ClearCache -> NaviampCoreMaintenanceOperation.ClearCache
            NaviampCoreCommand.Settings.ClearLibrary -> NaviampCoreMaintenanceOperation.ClearLibrary
            NaviampCoreCommand.Settings.ResetDatabase -> NaviampCoreMaintenanceOperation.ResetDatabase
            else -> return null
        }
        if (operation == NaviampCoreMaintenanceOperation.ResetDatabase) onDatabaseReset()
        val result = maintenancePort.run(operation)
        stateStore.update { state ->
            val shell = result.storageStats?.let { stats ->
                val previousCache = state.shell.cache
                val refreshedCache = previousCache.settings.toCacheSettingsUi(stats, state.shell.capabilities).copy(
                    diagnostics = previousCache.diagnostics,
                    downloadLocations = previousCache.downloadLocations,
                    audioCacheLocations = previousCache.audioCacheLocations,
                    selectedDownloadLocationId = previousCache.selectedDownloadLocationId,
                    selectedAudioCacheLocationId = previousCache.selectedAudioCacheLocationId,
                )
                state.shell.copy(
                    cache = refreshedCache,
                    playback = state.shell.playback.copy(downloadBytes = stats.downloadBytes),
                    downloads = state.shell.downloads.copy(
                        downloads = if (operation == NaviampCoreMaintenanceOperation.ResetDatabase) {
                            emptyList()
                        } else {
                            state.shell.downloads.downloads
                        },
                        downloadBytes = stats.downloadBytes,
                        offlineDashboard = state.shell.downloads.offlineDashboard.copy(
                            audioCacheCount = stats.audioCount,
                            audioCacheBytes = stats.audioBytes,
                            pendingProviderActionCount = stats.pendingProviderActionCount,
                        ),
                    ),
                )
            } ?: state.shell
            val reconciledShell = if (operation == NaviampCoreMaintenanceOperation.ResetDatabase) {
                shell.copy(
                    search = NaviampSearchScreenUi(),
                    home = NaviampHomeScreenUi(),
                    artistMixBuilder = SharedArtistMixBuilderUi(),
                    albumMixBuilder = SharedAlbumMixBuilderUi(),
                    genreMixBuilder = SharedGenreMixBuilderUi(),
                    sonicPathBuilder = SharedSonicPathBuilderUi(),
                    sonicMixBuilder = SharedSonicMixBuilderUi(),
                    library = NaviampLibraryScreenUi(),
                    downloads = NaviampDownloadsScreenUi(
                        maxDownloadBytes = shell.cache.settings.maxDownloadBytes,
                        offlineDashboard = shell.downloads.offlineDashboard.copy(
                            maxAudioCacheBytes = shell.cache.settings.maxAudioCacheBytes,
                        ),
                    ),
                    playlists = NaviampPlaylistsScreenUi(),
                    playlistChoices = emptyList(),
                    radio = NaviampInternetRadioScreenUi(),
                    albumDetail = NaviampAlbumDetailScreenUi(),
                    artistDetail = NaviampArtistDetailScreenUi(),
                    playlistDetail = NaviampPlaylistDetailScreenUi(),
                    nowPlaying = null,
                )
            } else {
                shell
            }
            state.copy(
                shell = reconciledShell,
                overlays = state.overlays.copy(status = result.status),
            )
        }
        return NaviampCoreCommandResult.Completed
    }

    private fun changeInterface(command: NaviampCoreCommand.Settings.ChangeInterface) {
        val settings = command.settings.normalized()
        interfaceStore.save(settings)
        onLocalSettingsChanged()
        stateStore.updateShell { shell ->
            shell.copy(general = shell.general.copy(interfaceSettings = settings))
        }
    }

    private fun changePlayback(command: NaviampCoreCommand.Settings.ChangePlayback) {
        val settings = playbackSettings.apply(command.settings, command.redownload)
        onLocalSettingsChanged()
        stateStore.updateShell { shell ->
            shell.copy(playback = shell.playback.copy(settings = settings))
        }
    }

    private fun changeCache(requested: app.naviamp.domain.settings.CacheSettings) {
        val settings = cacheSettings.apply(requested)
        stateStore.updateShell { shell ->
            shell.copy(
                cache = shell.cache.copy(settings = settings),
                downloads = shell.downloads.copy(
                    maxDownloadBytes = settings.maxDownloadBytes,
                    offlineDashboard = shell.downloads.offlineDashboard.copy(
                        maxAudioCacheBytes = settings.maxAudioCacheBytes,
                    ),
                ),
            )
        }
    }

    private fun updateStatsVisibility(visible: Boolean) {
        stateStore.update { state ->
            state.copy(overlays = state.overlays.copy(statsForNerdsVisible = visible))
        }
    }
}
