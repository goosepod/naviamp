package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionController
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampNavigationController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampRecentRadioStreamController
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.playback.AudioOutputDevice
import app.naviamp.domain.settings.toConnectionFormState
import app.naviamp.domain.settings.toSettingsSyncServerProfile
import app.naviamp.ui.NaviampShellCapabilitiesUi
import app.naviamp.ui.SharedRoute
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
    private val providerSessionLifecycle: NaviampCoreProviderSessionLifecycle,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val sidecars: NaviampCoreNowPlayingSidecarPort,
    private val diagnostics: NaviampCoreDiagnosticsPort,
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

    fun statsForNerdsDiagnostics() = naviampCoreDiagnostics(
        shell = state.value.shell,
        provider = providerSource.current(),
        sidecars = sidecars.snapshot(),
        playbackEngineRows = playbackDiagnostics(),
        external = diagnostics.snapshot(),
    )

    fun tickSleepTimer(nowEpochMillis: Long) {
        playbackController.tickSleepTimer(nowEpochMillis)
    }

    fun expireSleepTimer() {
        playbackController.expireSleepTimer()
    }

    /** Runs the shared sliding-session heartbeat until the mounted Core application is disposed. */
    suspend fun maintainProviderSession() {
        providerSessionLifecycle.maintainWhileMounted()
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
                persistNowPlayingOpen = { open ->
                    val sourceId = stateStore.state.value.shell.connectionSettings.currentSourceId
                    services.playback.sessions.updateNowPlayingOpen(open, sourceId)
                },
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

            val catalog = NaviampCoreCatalogController(
                stateStore,
                services.content.providerSource,
                mediaRegistry = mediaRegistry,
            )
            var notifyLocalSettingsChanged: () -> Unit = services.settings.sync.controller::markLocalChanged
            var completeDatabaseReset: suspend () -> Unit = {}
            val settings = NaviampCoreSettingsController(
                stateStore,
                services.settings.interfaceSettings,
                services.playback.settings,
                services.settings.cacheSettings,
                services.settings.maintenance,
                refreshLibrary = catalog::refreshAfterConnection,
                onDatabaseReset = { completeDatabaseReset() },
                onLocalSettingsChanged = { notifyLocalSettingsChanged() },
            )
            val home = NaviampCoreHomeController(
                stateStore,
                services.content.providerSource,
                navigation,
                services.content.homeDate,
                services.content.homeSupplement,
                services.content.providerResponses,
                services.content.homeLibrary,
                services.content.sonicHomeDiscovery,
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
            var publishPlaylistQueueUpdate: () -> Unit = {}
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
                queue = NaviampCorePlaylistQueuePort { _, tracks ->
                    val update = queue.appendTracks(tracks, "playlist tracks")
                    if (update.tracksChanged) {
                        services.playback.effects.applyQueue(update.queue, clearPreparedNext = true)
                        publishPlaylistQueueUpdate()
                    }
                },
                NaviampCorePlaylistDownloadPort(downloads::downloadPlaylist),
                services.playlists.history,
                services.connection,
                navigation::openNowPlaying,
                downloads::playlistTracksChanged,
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
                onRecentsChanged = { notifyLocalSettingsChanged() },
            )
            val visualizerSettings = object : NaviampCoreVisualizerSettingsPort {
                override fun save(visualizer: app.naviamp.ui.NaviampVisualizer) {
                    services.playback.visualizerSettings.save(visualizer)
                    notifyLocalSettingsChanged()
                }
            }
            val nowPlayingPresenter = NaviampCoreNowPlayingPresenter(
                stateStore,
                services.content.providerSource,
                livePlayback,
                queue,
                services.playback.effects,
                services.playback.sidecars,
                services.downloads.network,
                radio::stations,
            )
            publishPlaylistQueueUpdate = nowPlayingPresenter::publish
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
                visualizerSettings,
                services.playback.sidecars,
                downloads,
                mediaDetails,
                navigation,
                radio,
                services.favoritedAtIso8601,
                mediaRegistry,
            )
            val generatedRadioRecents = NaviampRecentRadioStreamController(
                load = services.radio.generatedRecents.load,
                save = services.radio.generatedRecents.save,
                onChanged = { notifyLocalSettingsChanged() },
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
                generatedRadioRecents,
                services.content.externalUri,
                services.favoritedAtIso8601,
                { nowPlayingPresenter.publish(playback.currentDisplay()) },
                navigation::openNowPlaying,
            )
            val recentRadio = NaviampCoreRecentRadioController(
                recents = generatedRadioRecents,
                media = mediaTransactions,
            )
            val standardMixes = NaviampCoreStandardMixController(
                stateStore,
                services.content.providerSource,
                services.mixes.artist,
                services.mixes.album,
                services.mixes.genre,
                playback = object : NaviampCoreStandardMixPlaybackPort {
                    override suspend fun playArtistMix(artists: List<app.naviamp.domain.Artist>, seedTracks: List<app.naviamp.domain.Track>) {
                        mediaTransactions.startArtistMix(artists, seedTracks)
                    }

                    override suspend fun playAlbumMix(albums: List<app.naviamp.domain.Album>, seedTracks: List<app.naviamp.domain.Track>) {
                        mediaTransactions.startAlbumMix(albums, seedTracks)
                    }

                    override suspend fun playGenreMix(genres: List<app.naviamp.domain.Genre>) {
                        mediaTransactions.startGenreMix(genres)
                    }
                },
            )
            val sonicBuilders = NaviampCoreSonicBuilderController(
                stateStore,
                services.content.providerSource,
                playlistBrowse,
                playback = NaviampCoreSonicPlaybackPort { tracks, _ -> mediaTransactions.play(tracks) },
                queue = NaviampCoreSonicQueuePort { tracks, _ -> mediaTransactions.addToQueue(tracks) },
            )
            playback.attachNativePlayback()
            val trackActions = NaviampCoreTrackActionController(mediaRegistry, mediaTransactions)
            val collectionActions = NaviampCoreCollectionActionController(
                services.content.providerSource,
                mediaRegistry,
                mediaTransactions,
                mediaDetails,
            )
            val providerSessionLifecycle = NaviampCoreProviderSessionLifecycle(
                sessionPort = services.connection,
            )
            val connection = NaviampCoreConnectionController(
                NaviampConnectionController(initialState.connection),
                stateStore,
                services.connection,
                initialState.connectionInventory,
                onConnected = { sourceId ->
                    scope.launch { providerSessionLifecycle.refreshNow() }
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
                    scope.launch {
                        val reopenNowPlaying = services.playback.sessions.load(sourceId)?.nowPlayingOpen == true
                        if (playback.restoreSession(sourceId) && reopenNowPlaying) {
                            navigation.restoreNowPlayingOpen()
                        }
                    }
                    scope.launch { home.refreshAfterConnection() }
                    scope.launch { catalog.refreshAfterConnection() }
                    scope.launch { playlistBrowse.refreshAfterConnection() }
                    scope.launch { radio.refreshAfterConnection() }
                    scope.launch { downloads.refresh(reconcile = false) }
                },
            )
            completeDatabaseReset = {
                playback.resetAfterDatabaseClear()
                connection.resetAfterDatabaseClear()
                navigation.resetAfterDatabaseClear()
            }
            val settingsSync = NaviampCoreSettingsSyncController(
                stateStore = stateStore,
                services = services.settings.sync,
                onDocumentApplied = { snapshot ->
                    connection.replaceSavedConnections(
                        snapshot.serverProfiles.map { source ->
                            NaviampCoreSavedConnectionRecord(
                                id = source.id,
                                displayName = source.displayName,
                                serverUrl = source.baseUrl,
                                username = source.username,
                                selectedMusicFolderIds = source.selectedMusicFolderIds,
                            )
                        },
                    )
                    snapshot.serverProfiles.firstOrNull()?.let { source ->
                        val form = source.toSettingsSyncServerProfile().toConnectionFormState()
                        stateStore.updateShell { shell ->
                            shell.copy(
                                connectionSettings = shell.connectionSettings.copy(
                                    connection = shell.connectionSettings.connection.copy(
                                        editingConnection = true,
                                        form = form,
                                    ),
                                ),
                            )
                        }
                        navigation.dispatch(NaviampCoreCommand.Navigation.SelectRoute(SharedRoute.Settings))
                    }
                    home.refreshAfterConnection()
                },
            )
            notifyLocalSettingsChanged = {
                scope.launch { settingsSync.localSettingsChanged() }
            }
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
                    recentRadio,
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
                providerSessionLifecycle = providerSessionLifecycle,
                providerSource = services.content.providerSource,
                sidecars = services.playback.sidecars,
                diagnostics = services.diagnostics,
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
