package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionController
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampNavigationController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.playback.AudioOutputDevice
import app.naviamp.ui.NaviampShellCapabilitiesUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NaviampCoreInitialState(
    val product: NaviampCoreState = NaviampCoreState(),
    val navigation: NaviampNavigationState = NaviampNavigationState(),
    val playback: NaviampLivePlaybackState = NaviampLivePlaybackState(),
    val connection: NaviampConnectionRuntimeState = NaviampConnectionRuntimeState(),
    val connectionInventory: NaviampCoreConnectionInventory = NaviampCoreConnectionInventory(),
)

/**
 * Applies host capability facts to every derived shared UI slice in one Core-owned operation.
 * Hosts must never set individual feature rows or menus themselves.
 */
fun NaviampCoreInitialState.withShellCapabilities(
    capabilities: NaviampShellCapabilitiesUi,
    audioOutputDeviceSelectionAvailable: Boolean = false,
    audioOutputDevices: List<AudioOutputDevice> = emptyList(),
): NaviampCoreInitialState = copy(
    product = product.copy(
        shell = product.shell.copy(
            capabilities = capabilities,
            connectionSettings = product.shell.connectionSettings.copy(
                capabilities = capabilities.connection,
            ),
            playback = product.shell.playback.copy(
                replayGainAvailable = capabilities.replayGain,
                gaplessAvailable = capabilities.gapless,
                crossfadeAvailable = capabilities.crossfade,
                equalizerAvailable = capabilities.equalizer,
                audioOutputDeviceSelectionAvailable = audioOutputDeviceSelectionAvailable,
                audioOutputDevices = audioOutputDevices,
                sonicSimilarityAvailable = capabilities.sonicSimilarity,
                showMobileNetworkQuality = capabilities.showMobileNetworkQuality,
            ),
            cache = product.shell.cache.copy(fileSelectionAvailable = capabilities.fileSelection),
            shellChrome = product.shell.shellChrome.copy(
                supportsDownloads = capabilities.downloads,
                supportsApplicationUpdates = capabilities.applicationUpdates,
            ),
        ),
        settingsSync = product.settingsSync.copy(
            available = capabilities.settingsImportExport && capabilities.fileSelection,
        ),
    ),
)

/**
 * One host-neutral Naviamp product composition.
 *
 * Hosts construct [NaviampCoreServices], mount the shared application, and forward lifecycle or
 * native playback observations through this facade. They never assemble product controllers.
 */
class NaviampCore private constructor(
    val stateStore: NaviampCoreStateStore,
    val actions: NaviampCoreActions,
    val commands: NaviampCoreCommandHandler,
    private val playbackController: NaviampCorePlaybackController,
    private val nowPlayingController: NaviampCoreNowPlayingMediaController,
    private val nowPlayingPresenter: NaviampCoreNowPlayingPresenter,
) {
    val state: StateFlow<NaviampCoreState> = stateStore.state

    fun dispatch(command: NaviampCoreCommand) = commands.dispatch(command)

    suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult =
        commands.execute(command)

    fun updateLivePlayback(transform: (NaviampLivePlaybackState) -> NaviampLivePlaybackState) {
        playbackController.updateLiveState(transform)
    }

    suspend fun onTrackChanged(track: Track?) {
        nowPlayingController.onTrackChanged(track)
    }

    fun publishNowPlaying() {
        nowPlayingPresenter.publish(playbackController.currentDisplay())
    }

    fun playbackDiagnostics(): List<Pair<String, String>> = playbackController.diagnostics()

    fun tickSleepTimer(nowEpochMillis: Long) {
        playbackController.tickSleepTimer(nowEpochMillis)
    }

    fun expireSleepTimer() {
        playbackController.expireSleepTimer()
    }

    companion object {
        fun create(
            scope: CoroutineScope,
            services: NaviampCoreServices,
            initialState: NaviampCoreInitialState = NaviampCoreInitialState(),
            actionAvailability: NaviampCoreActionAvailability = NaviampCoreActionAvailability(),
            onAsyncFailure: (NaviampCoreCommand, Throwable) -> Unit = { command, cause ->
                throw IllegalStateException("Core command failed: $command", cause)
            },
        ): NaviampCore {
            val stateStore = NaviampCoreStateStore(initialState.product)
            val navigationState = NaviampNavigationController(initialState.navigation)
            val livePlayback = NaviampLivePlaybackController(initialState.playback)
            val queue = NaviampPlaybackQueueCoordinator(livePlayback)
            val deferredArtistNavigator = DeferredArtistNavigator()
            val mediaRegistry = NaviampCoreMediaRegistry()
            val navigation = NaviampCoreNavigationController(
                navigationState,
                stateStore,
                deferredArtistNavigator,
            )
            val mediaDetails = NaviampCoreMediaDetailController(
                stateStore,
                services.content.providerSource,
                navigation,
                scope,
                services.content.artistDiscovery,
                mediaRegistry,
            )
            deferredArtistNavigator.target = mediaDetails

            val settings = NaviampCoreSettingsController(
                stateStore,
                services.settings.interfaceSettings,
                services.playback.settings,
                services.settings.cacheSettings,
                services.settings.maintenance,
            )
            val settingsSync = NaviampCoreSettingsSyncController(stateStore, services.settings.sync)
            val catalog = NaviampCoreCatalogController(
                stateStore,
                services.content.providerSource,
                mediaRegistry = mediaRegistry,
            )
            val home = NaviampCoreHomeController(
                stateStore,
                services.content.providerSource,
                navigation,
                services.content.homeDate,
                services.content.homeSupplement,
                services.content.providerResponses,
                services.content.homeLibrary,
                mediaRegistry = mediaRegistry,
            )
            val playlistBrowse = NaviampCorePlaylistBrowseController(
                stateStore,
                services.content.providerSource,
                navigation,
                services.content.playlistSupplement,
                mediaRegistry = mediaRegistry,
            )
            val downloads = NaviampCoreDownloadsController(
                scope,
                stateStore,
                services.content.providerSource,
                services.downloads.storage,
                services.downloads.transfer,
                services.downloads.keepDownloaded,
                services.downloads.playback,
                services.downloads.network,
            )
            val playlistTransactions = NaviampCorePlaylistTransactionController(
                stateStore,
                services.content.providerSource,
                playlistBrowse,
                playback = NaviampCorePlaylistPlaybackPort { _, tracks, shuffle ->
                    val ordered = if (shuffle) tracks.shuffled() else tracks
                    val update = queue.startQueue(ordered, 0)
                    livePlayback.updateCurrentTrack(update.queue.current)
                    services.playback.effects.playQueueSelection(update.queue, update.queue.currentIndex)
                },
                services.playlists.queue,
                services.playlists.downloads,
                services.playlists.history,
                services.playlists.smartProviderSource,
                navigation::openNowPlaying,
            )
            val radio = NaviampCoreInternetRadioController(
                stateStore,
                services.content.providerSource,
                services.radio.playback,
                services.radio.recents,
                onPlaybackStarted = { station ->
                    val track = app.naviamp.domain.radio.internetRadioTrack(station)
                    queue.startQueue(listOf(track), 0)
                    livePlayback.updateCurrentTrack(track)
                    livePlayback.updateCurrentStation(station)
                    navigation.openNowPlaying()
                },
            )
            val standardMixes = NaviampCoreStandardMixController(
                stateStore,
                services.content.providerSource,
                services.mixes.artist,
                services.mixes.album,
                services.mixes.genre,
                services.mixes.standardPlayback,
            )
            val sonicBuilders = NaviampCoreSonicBuilderController(
                stateStore,
                services.content.providerSource,
                playlistBrowse,
                services.mixes.sonicPlayback,
                services.mixes.sonicQueue,
            )
            val nowPlayingPresenter = NaviampCoreNowPlayingPresenter(
                stateStore,
                services.content.providerSource,
                livePlayback,
                queue,
                services.playback.effects,
                services.playback.sidecars,
                services.downloads.network,
            )
            val playback = NaviampCorePlaybackController(
                scope,
                stateStore,
                services.content.providerSource,
                livePlayback,
                queue,
                services.playback.effects,
                services.playback.settings,
                services.playback.sidecars,
                services.playback.sessions,
                nowPlayingPresenter,
                services.clockEpochMillis,
            )
            val nowPlaying = NaviampCoreNowPlayingMediaController(
                stateStore,
                services.content.providerSource,
                livePlayback,
                queue,
                services.playback.effects,
                nowPlayingPresenter,
                playback,
                services.playback.settings,
                services.playback.visualizerSettings,
                services.playback.sidecars,
                downloads,
                mediaDetails,
                navigation,
                radio,
                services.favoritedAtIso8601,
                mediaRegistry,
            )
            val mediaTransactions = NaviampCoreMediaTransactions(
                stateStore,
                services.content.providerSource,
                mediaRegistry,
                livePlayback,
                queue,
                services.playback.effects,
                downloads,
                mediaDetails,
                services.content.externalUri,
                services.favoritedAtIso8601,
                { nowPlayingPresenter.publish(playback.currentDisplay()) },
                navigation::openNowPlaying,
            )
            playback.attachNativePlayback()
            val trackActions = NaviampCoreTrackActionController(mediaRegistry, mediaTransactions)
            val collectionActions = NaviampCoreCollectionActionController(
                services.content.providerSource,
                mediaRegistry,
                mediaTransactions,
                mediaDetails,
            )
            val connection = NaviampCoreConnectionController(
                NaviampConnectionController(initialState.connection),
                stateStore,
                services.connection,
                initialState.connectionInventory,
                onConnected = { sourceId ->
                    services.content.providerSource.current()?.capabilities?.let { providerCapabilities ->
                        stateStore.updateShell { shell ->
                            val capabilities = shell.capabilities.copy(
                                sonicSimilarity = providerCapabilities.supportsSonicSimilarity,
                            )
                            shell.copy(
                                capabilities = capabilities,
                                playback = shell.playback.copy(
                                    sonicSimilarityAvailable = capabilities.sonicSimilarity,
                                ),
                            )
                        }
                    }
                    scope.launch { playback.restoreSession(sourceId) }
                    scope.launch { home.refreshAfterConnection() }
                    scope.launch { catalog.refreshAfterConnection() }
                    scope.launch { playlistBrowse.refreshAfterConnection() }
                    scope.launch { radio.refreshAfterConnection() }
                    scope.launch { downloads.refresh(reconcile = false) }
                },
            )
            val router = NaviampCoreCommandRouter(
                scope = scope,
                controllers = listOf(
                    navigation,
                    connection,
                    settings,
                    settingsSync,
                    catalog,
                    home,
                    mediaDetails,
                    playlistBrowse,
                    playlistTransactions,
                    radio,
                    standardMixes,
                    sonicBuilders,
                    downloads,
                    playback,
                    nowPlaying,
                    trackActions,
                    collectionActions,
                ),
                onAsyncFailure = onAsyncFailure,
            )
            nowPlayingPresenter.publish()
            scope.launch { connection.restoreInitialConnection() }
            return NaviampCore(
                stateStore = stateStore,
                actions = createNaviampCoreActions(router, actionAvailability),
                commands = router,
                playbackController = playback,
                nowPlayingController = nowPlaying,
                nowPlayingPresenter = nowPlayingPresenter,
            )
        }
    }
}

private class DeferredArtistNavigator : NaviampCoreArtistNavigator {
    lateinit var target: NaviampCoreArtistNavigator

    override fun openArtist(artist: Artist) {
        check(::target.isInitialized) { "Naviamp Core artist navigation is not initialized." }
        target.openArtist(artist)
    }
}
