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
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.settings.toConnectionFormState
import app.naviamp.domain.settings.toSettingsSyncServerProfile
import app.naviamp.domain.settings.GlobalShortcutAction
import app.naviamp.domain.settings.GlobalShortcutVolumeStepPercent
import app.naviamp.ui.GlobalShortcutRegistrationUi
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
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
                softwareVolumeControlAvailable = capabilities.softwareVolumeControl,
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
    val playbackProgress: StateFlow<PlaybackProgress>,
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

    fun handleGlobalShortcut(action: GlobalShortcutAction): NaviampCoreHostShortcutEffect? {
        if (action == GlobalShortcutAction.BringToFront) return NaviampCoreHostShortcutEffect.BringToFront
        val request = when (action) {
            GlobalShortcutAction.PlayPause -> NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.PlayCurrent)
            GlobalShortcutAction.NextTrack -> NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.Next)
            GlobalShortcutAction.Previous -> NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.Previous)
            GlobalShortcutAction.VolumeUp,
            GlobalShortcutAction.VolumeDown,
            -> {
                val current = state.value.shell.playback.settings.volumePercent
                val delta = if (action == GlobalShortcutAction.VolumeUp) {
                    GlobalShortcutVolumeStepPercent
                } else {
                    -GlobalShortcutVolumeStepPercent
                }
                NowPlayingPlaybackActionRequest(
                    NowPlayingPlaybackAction.ChangeVolume,
                    volumePercent = (current + delta).coerceIn(0, 100),
                )
            }
            GlobalShortcutAction.BringToFront -> error("Handled above")
        }
        dispatch(NaviampCoreCommand.NowPlaying.Playback(request))
        return null
    }

    fun updateGlobalShortcutStatuses(statuses: Map<GlobalShortcutAction, GlobalShortcutRegistrationUi>) {
        stateStore.updateShell { shell ->
            shell.copy(general = shell.general.copy(globalShortcutStatuses = statuses))
        }
    }

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
            val busyIndicator = NaviampCoreBusyIndicator(stateStore)
            val providerSource = NaviampCoreMediaProviderSource {
                services.content.providerSource.current()?.let { provider ->
                    services.providerActions.offlineCapable(
                        provider = provider,
                        sourceId = stateStore.state.value.shell.connectionSettings.currentSourceId,
                    )
                }
            }
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
                providerSource,
                navigation,
                scope,
                services.content.artistDiscovery,
                mediaRegistry,
            )
            deferredArtistNavigator.target = mediaDetails

            val catalog = NaviampCoreCatalogController(
                stateStore,
                providerSource,
                libraryGenreRefresh = services.content.libraryGenreRefresh,
                mediaRegistry = mediaRegistry,
            )
            var notifyLocalSettingsChanged: () -> Unit = services.settings.sync.controller::markLocalChanged
            var completeDatabaseReset: suspend () -> Unit = {}
            val home = NaviampCoreHomeController(
                stateStore,
                providerSource,
                navigation,
                services.content.homeDate,
                services.content.homeSupplement,
                services.content.providerResponses,
                services.content.homeLibrary,
                services.content.sonicHomeDiscovery,
                mediaRegistry = mediaRegistry,
            )
            val settings = NaviampCoreSettingsController(
                stateStore,
                services.settings.interfaceSettings,
                services.playback.settings,
                services.settings.cacheSettings,
                services.settings.maintenance,
                refreshLibrary = catalog::refreshAfterConnection,
                onDatabaseReset = { completeDatabaseReset() },
                onLocalSettingsChanged = { notifyLocalSettingsChanged() },
                onInterfaceSettingsChanged = { interfaceSettings ->
                    mediaDetails.interfaceSettingsChanged(interfaceSettings)
                    home.interfaceSettingsChanged(interfaceSettings)
                },
            )
            val playlistBrowse = NaviampCorePlaylistBrowseController(
                stateStore,
                providerSource,
                navigation,
                services.content.playlistSupplement,
                mediaRegistry = mediaRegistry,
            )
            val radio = NaviampCoreInternetRadioController(
                stateStore,
                providerSource,
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
                providerSource,
                livePlayback,
                queue,
                services.playback.effects,
                services.playback.sidecars,
                services.downloads.network,
                radio::stations,
            )
            val queuePlayback = NaviampCoreQueuePlaybackController(
                playback = livePlayback,
                queue = queue,
                effects = services.playback.effects,
                publishNowPlaying = nowPlayingPresenter::publish,
                openNowPlaying = navigation::openNowPlaying,
            )
            val downloads = NaviampCoreDownloadsController(
                scope,
                stateStore,
                providerSource,
                services.downloads.storage,
                services.downloads.transfer,
                services.downloads.keepDownloaded,
                NaviampCoreDownloadedPlaybackPort { tracks, index -> queuePlayback.play(tracks, index) },
                services.downloads.network,
            )
            val publishPlaylistQueueUpdate: () -> Unit = nowPlayingPresenter::publish
            val playlistTransactions = NaviampCorePlaylistTransactionController(
                stateStore,
                providerSource,
                playlistBrowse,
                playback = NaviampCorePlaylistPlaybackPort { _, tracks, shuffle ->
                    queuePlayback.play(tracks, shuffle = shuffle)
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
                services.playlists.preview,
                navigation::openNowPlaying,
                downloads::playlistTracksChanged,
            )
            val playback = NaviampCorePlaybackController(
                scope,
                stateStore,
                providerSource,
                livePlayback,
                queue,
                services.playback.effects,
                services.playback.settings,
                services.playback.sidecars,
                services.playback.sessions,
                nowPlayingPresenter,
                services.clockEpochMillis,
            )
            val generatedRadioRecents = NaviampRecentRadioStreamController(
                load = services.radio.generatedRecents.load,
                save = services.radio.generatedRecents.save,
                onChanged = { notifyLocalSettingsChanged() },
            )
            val mediaTransactions = NaviampCoreMediaTransactions(
                stateStore,
                busyIndicator,
                providerSource,
                mediaRegistry,
                livePlayback,
                queue,
                services.playback.effects,
                queuePlayback,
                downloads,
                mediaDetails,
                generatedRadioRecents,
                services.content.externalUri,
                services.favoritedAtIso8601,
                { nowPlayingPresenter.publish(playback.currentDisplay()) },
                navigation::openNowPlaying,
            )
            val nowPlaying = NaviampCoreNowPlayingMediaController(
                stateStore,
                providerSource,
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
                mediaTransactions,
                services.favoritedAtIso8601,
                mediaRegistry,
            )
            val recentRadio = NaviampCoreRecentRadioController(
                recents = generatedRadioRecents,
                media = mediaTransactions,
            )
            val standardMixes = NaviampCoreStandardMixController(
                stateStore,
                providerSource,
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
                providerSource,
                playlistBrowse,
                playback = NaviampCoreSonicPlaybackPort { tracks, _ -> mediaTransactions.play(tracks) },
                queue = NaviampCoreSonicQueuePort { tracks, _ -> mediaTransactions.addToQueue(tracks) },
            )
            playback.attachNativePlayback()
            val trackActions = NaviampCoreTrackActionController(mediaRegistry, mediaTransactions)
            val collectionActions = NaviampCoreCollectionActionController(
                providerSource,
                mediaRegistry,
                mediaTransactions,
                mediaDetails,
            )
            val providerSessionLifecycle = NaviampCoreProviderSessionLifecycle(
                sessionPort = services.connection,
            )
            val restoreLocalSession: (String) -> Unit = { sourceId ->
                scope.launch {
                    val reopenNowPlaying = services.playback.sessions.load(sourceId)?.nowPlayingOpen == true
                    if (playback.restoreSession(sourceId) && reopenNowPlaying) {
                        navigation.restoreNowPlayingOpen()
                    }
                }
                scope.launch { downloads.refresh(reconcile = false) }
            }
            val connection = NaviampCoreConnectionController(
                NaviampConnectionController(initialState.connection),
                stateStore,
                services.connection,
                initialState.connectionInventory,
                onSourceChanging = { previousSourceId, newSourceId ->
                    playback.resetForSourceChange(previousSourceId, newSourceId)
                    generatedRadioRecents.clear()
                    radio.resetForSourceChange()
                    home.resetForSourceChange()
                },
                onConnected = { sourceId ->
                    scope.launch { providerSessionLifecycle.refreshNow() }
                    scope.launch {
                        services.content.providerSource.current()?.let { provider ->
                            services.providerActions.replay(sourceId, provider)
                        }
                    }
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
                    restoreLocalSession(sourceId)
                    scope.launch { home.refreshAfterConnection() }
                    scope.launch { catalog.refreshAfterConnection() }
                    scope.launch { playlistBrowse.refreshAfterConnection() }
                    scope.launch { radio.refreshAfterConnection() }
                },
                onOfflineRestored = restoreLocalSession,
            )
            if (initialState.connectionInventory.currentSourceId != null) {
                scope.launch { providerSessionLifecycle.refreshNow() }
            }
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
                                providerId = source.providerId,
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
                                        editingSavedConnection = false,
                                        form = form,
                                    ),
                                ),
                            )
                        }
                        navigation.dispatch(NaviampCoreCommand.Navigation.SelectRoute(SharedRoute.Settings))
                    }
                    mediaDetails.interfaceSettingsChanged(snapshot.interfaceSettings)
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
                playbackProgress = livePlayback.progress,
                actions = createNaviampCoreActions(router, actionAvailability),
                commands = router,
                playbackController = playback,
                nowPlayingController = nowPlaying,
                nowPlayingPresenter = nowPlayingPresenter,
                providerSessionLifecycle = providerSessionLifecycle,
                providerSource = providerSource,
                sidecars = services.playback.sidecars,
                diagnostics = services.diagnostics,
            )
        }
    }
}

enum class NaviampCoreHostShortcutEffect { BringToFront }

private class DeferredArtistNavigator : NaviampCoreArtistNavigator {
    lateinit var target: NaviampCoreArtistNavigator

    override fun openArtist(artist: Artist) {
        check(::target.isInitialized) { "Naviamp Core artist navigation is not initialized." }
        target.openArtist(artist)
    }
}
