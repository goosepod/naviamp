package app.naviamp.presentation
import app.naviamp.domain.isInternetRadioTrack

import app.naviamp.app.NaviampConnectionController
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampNavigationController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampPlaybackOutputSelectionController
import app.naviamp.app.NaviampCastMediaEndpointController
import app.naviamp.app.NaviampCastMediaLeaseController
import app.naviamp.app.NaviampCastSessionController
import app.naviamp.app.NaviampRecentRadioStreamController
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.connect.NaviampConnectDeviceCapability
import app.naviamp.domain.playback.AudioOutputDevice
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.settings.hasStoredCredentials
import app.naviamp.domain.settings.toConnectionFormState
import app.naviamp.domain.settings.toSettingsSyncServerProfile
import app.naviamp.domain.settings.GlobalShortcutAction
import app.naviamp.domain.settings.GlobalShortcutVolumeStepPercent
import app.naviamp.ui.GlobalShortcutRegistrationUi
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NaviampShellCapabilitiesUi
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toCacheSettingsUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
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
                stereoDownmixAvailable = capabilities.stereoDownmix,
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
    private val closeCommands: () -> Unit,
    private val playbackController: NaviampCorePlaybackController,
    private val nowPlayingController: NaviampCoreNowPlayingMediaController,
    private val nowPlayingPresenter: NaviampCoreNowPlayingPresenter,
    private val providerSessionLifecycle: NaviampCoreProviderSessionLifecycle,
    /** Starts offline reconnection on the runtime scope; runtime-owned like provider-session renewal. */
    private val startConnectionRecovery: (CoroutineScope) -> Unit = {},
    private val runtimeScope: CoroutineScope,
    private val homeController: NaviampCoreHomeController,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val sidecars: NaviampCoreNowPlayingSidecarPort,
    private val samplePlayback: NaviampCoreSamplePlaybackController,
    private val diagnostics: NaviampCoreDiagnosticsPort,
    private val connectController: NaviampCoreConnectController?,
    private val castController: NaviampCoreCastController?,
    private val castPicker: NaviampCoreCastPickerController?,
    private val showCastRoutePicker: (() -> Unit)?,
    internal val playbackOutputs: NaviampPlaybackOutputSelectionController,
    internal val vehicleArtwork: NaviampVehicleArtworkSource?,
    internal val vehicleCollections: NaviampVehicleCollectionAccess,
    internal val vehicleLibrary: (app.naviamp.ui.NaviampVehicleLibraryLabels) -> NaviampVehicleLibraryAccess,
    internal val vehicleDownloads: (app.naviamp.ui.NaviampVehicleLibraryLabels, app.naviamp.ui.NaviampVehicleDownloadsLabels) -> NaviampVehicleDownloadsAccess,
) {
    val state: StateFlow<NaviampCoreState> = stateStore.state

    /**
     * The progress timeline every host's shared Now Playing UI presents: local engine progress, or
     * the attached Connect target's reported position extrapolated while it plays.
     * [playbackProgress] remains the local engine's progress.
     */
    val presentedPlaybackProgress: StateFlow<PlaybackProgress> = naviampPresentedPlaybackProgress(
        scope = runtimeScope,
        local = playbackProgress,
        remote = state.map { it.shell.connect.remoteNowPlaying }.remoteProgressReports(),
    )

    fun dispatch(command: NaviampCoreCommand) = commands.dispatch(command)

    fun handleGlobalShortcut(action: GlobalShortcutAction): NaviampCoreHostShortcutEffect? {
        if (action == GlobalShortcutAction.BringToFront) return NaviampCoreHostShortcutEffect.BringToFront
        val request = when (action) {
            GlobalShortcutAction.PlayPause -> NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.TogglePlayPause)
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
        playbackProfileRows = playbackController.playbackProfileDiagnostics(),
        external = diagnostics.snapshot(),
    )

    /** Uses the same storage facts as Stats for Nerds, only while Settings is mounted. */
    internal suspend fun maintainSettingsStorage() {
        while (currentCoroutineContext().isActive) {
            val stats = runCatching { diagnostics.snapshot().storage }.getOrNull()
            if (stats != null) {
                stateStore.updateShell { shell ->
                    val refreshed = shell.cache.settings.toCacheSettingsUi(stats, shell.capabilities)
                    shell.copy(
                        cache = shell.cache.copy(
                            audioCacheDiagnostics = refreshed.audioCacheDiagnostics,
                            downloadsDiagnostics = refreshed.downloadsDiagnostics,
                        ),
                        playback = shell.playback.copy(downloadBytes = stats.downloadBytes),
                    )
                }
            }
            delay(2_000L)
        }
    }

    fun tickSleepTimer(nowEpochMillis: Long) {
        playbackController.tickSleepTimer(nowEpochMillis)
    }

    fun expireSleepTimer() {
        playbackController.expireSleepTimer()
    }

    private var closed = false

    suspend fun shutdown() {
        if (closed) return
        castPicker?.close()
        try { castController?.shutdown() } finally { close() }
    }

    fun close() {
        if (closed) return
        closed = true
        closeCommands()
        try {
            providerSessionLifecycle.close()
            samplePlayback.close()
            castPicker?.close()
            castController?.close()
            connectController?.close()
        } finally { runtimeScope.cancel() }
    }

    val castAvailable: Boolean get() = showCastRoutePicker != null || castPicker != null
    val castPickerState: StateFlow<app.naviamp.ui.NaviampCastPickerUi>? get() = castPicker?.state

    fun showCastPicker() { if (castPicker != null) castPicker.show() else showCastRoutePicker?.invoke() }
    fun dismissCastPicker() { castPicker?.dismiss() }
    fun retryCastDiscovery() { castPicker?.retry() }
    fun selectCastTarget(id: String) {
        if (closed) return
        stateStore.update { it.copy(playbackIntentGeneration = it.playbackIntentGeneration + 1L) }
        castPicker?.select(id)
    }

    fun selectLocalPlayback() {
        if (closed) return
        stateStore.update { it.copy(playbackIntentGeneration = it.playbackIntentGeneration + 1L) }
        if (castPicker != null) castPicker.selectLocal() else castController?.selectLocal()
    }

    /** Starts runtime-owned work once, including when an external surface launches without phone UI. */
    fun startRuntimeServices() {
        if (closed) return
        providerSessionLifecycle.start(runtimeScope)
        startConnectionRecovery(runtimeScope)
    }

    /** A mounted surface may await renewal without owning or cancelling the runtime's job. */
    suspend fun maintainProviderSession() {
        if (closed) return
        startConnectionRecovery(runtimeScope)
        providerSessionLifecycle.start(runtimeScope).join()
    }
    suspend fun maintainDiscoveryAcquisitions() = homeController.maintainDiscoveryAcquisitions()

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
            val runtimeScope = CoroutineScope(scope.coroutineContext + kotlinx.coroutines.SupervisorJob(scope.coroutineContext[kotlinx.coroutines.Job]))
            return try { createRuntime(runtimeScope, services, initialState, actionAvailability, onAsyncFailure) }
            catch (failure: Throwable) { runtimeScope.cancel(); throw failure }
        }

        private fun createRuntime(
            scope: CoroutineScope,
            services: NaviampCoreServices,
            initialState: NaviampCoreInitialState,
            actionAvailability: NaviampCoreActionAvailability,
            onAsyncFailure: (NaviampCoreCommand, Throwable) -> Unit,
        ): NaviampCore {
            val stateStore = NaviampCoreStateStore(initialState.product)
            // Native media services may publish text before, or without, any application composition.
            app.naviamp.ui.NaviampResourceLanguage.follow(
                scope,
                stateStore.state.map { it.shell.general.interfaceSettings.language }.distinctUntilChanged(),
            )
            val playbackProfiles = NaviampCorePlaybackProfileController(
                stateStore = stateStore,
                repository = services.playback.profiles,
            )
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
            val playbackOutputs = NaviampPlaybackOutputSelectionController()
            val routedPlaybackEffects = NaviampCoreOutputPlaybackEffects(services.playback.effects, playbackOutputs,
                services.playback.outputFade)
            var pauseMainForSample: () -> Unit = routedPlaybackEffects::pause
            val samplePlayback = NaviampCoreSamplePlaybackController(scope, services.playback.samples,
                providerSource, {
                    stateStore.state.value.shell.connect.remoteNowPlaying?.let {
                        if (it.isPlaying) app.naviamp.domain.playback.PlaybackState.Playing else app.naviamp.domain.playback.PlaybackState.Paused
                    } ?: livePlayback.state.value.playbackState
                }, { pauseMainForSample() }, publish = { sample ->
                    stateStore.updateShell { shell -> shell.copy(home = shell.home.copy(samplePlayback = sample)) }
                }, prepareMain = routedPlaybackEffects::prepareForPreview)
            routedPlaybackEffects.beforeStart = samplePlayback::stop
            val selectionStarted = {
                stateStore.update { it.copy(playbackSelectionGeneration = it.playbackSelectionGeneration + 1L) }
            }
            routedPlaybackEffects.onSelectionStarted = selectionStarted
            val deferredArtistNavigator = DeferredArtistNavigator()
            val mediaRegistry = NaviampCoreMediaRegistry()
            val navigation = NaviampCoreNavigationController(
                navigationState,
                stateStore,
                deferredArtistNavigator,
                onLeaveDiscovery = samplePlayback::stop,
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
                playbackProfiles,
            )
            deferredArtistNavigator.target = mediaDetails

            var persistLibraryAlbumSortOrder: (app.naviamp.domain.settings.LibraryAlbumSortOrder) -> Unit = {}
            val catalog = NaviampCoreCatalogController(
                stateStore,
                providerSource,
                libraryGenreRefresh = services.content.libraryGenreRefresh,
                libraryIndex = services.content.libraryIndex,
                albumIndex = services.content.albumIndex,
                mediaRegistry = mediaRegistry,
                onLibraryAlbumSortOrderChanged = { persistLibraryAlbumSortOrder(it) },
            )
            var notifyLocalSettingsChanged: () -> Unit = services.settings.sync.controller::markLocalChanged
            var completeDatabaseReset: suspend () -> Unit = {}
            var notifyPlaybackSettingsChanged: (app.naviamp.domain.settings.PlaybackSettings, app.naviamp.domain.settings.PlaybackSettings) -> Unit = { _, _ -> }
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
                observedAtIso8601 = services.favoritedAtIso8601,
                externalUri = services.content.externalUri,
                openLibraryArtist = { mediaDetails.selectArtist(it.toSharedMediaItemUi()) },
                openLibraryAlbum = { album -> mediaDetails.selectAlbum(album.toSharedMediaItemUi(coverArtUrl = { id ->
                    id?.let { providerSource.current()?.coverArtUrl(it) }
                })) },
                playDiscoverySample = { samplePlayback.toggle(it) },
                stopDiscoverySample = samplePlayback::stop,
                acquisitionRepository = services.content.discoveryAcquisitions,
                nowMillis = services.clockEpochMillis,
            )
            val similarity = NaviampCoreSimilarityController(stateStore, providerSource) { provider ->
                livePlayback.state.value.currentTrack?.takeUnless { it.isInternetRadioTrack() }
                    ?: mediaRegistry.tracks().firstOrNull { !it.isInternetRadioTrack() }
                    ?: provider.tracks(limit = 1).firstOrNull { !it.isInternetRadioTrack() }
            }
            val settings = NaviampCoreSettingsController(
                stateStore,
                services.settings.interfaceSettings,
                services.playback.settings,
                services.settings.cacheSettings,
                services.settings.maintenance,
                refreshLibrary = catalog::refreshLocalLibraryIndex,
                testSimilarity = similarity::test,
                onDatabaseReset = { completeDatabaseReset() },
                onLocalSettingsChanged = { notifyLocalSettingsChanged() },
                onInterfaceSettingsChanged = { interfaceSettings ->
                    catalog.interfaceSettingsChanged(interfaceSettings)
                    mediaDetails.interfaceSettingsChanged(interfaceSettings)
                    home.interfaceSettingsChanged(interfaceSettings)
                },
                onPlaybackSettingsChanged = { previous, current ->
                    notifyPlaybackSettingsChanged(previous, current)
                },
            )
            persistLibraryAlbumSortOrder = { order ->
                settings.dispatch(
                    NaviampCoreCommand.Settings.ChangeInterface(
                        stateStore.state.value.shell.general.interfaceSettings.copy(libraryAlbumSortOrder = order),
                    ),
                )
            }
            val playlistBrowse = NaviampCorePlaylistBrowseController(
                stateStore,
                providerSource,
                navigation,
                services.content.playlistSupplement,
                mediaRegistry = mediaRegistry,
                playbackProfiles = playbackProfiles,
                offlinePlaylists = NaviampCoreOfflinePlaylistSource(services.downloads.offlinePlaylists) { sourceId ->
                    services.downloads.storage.availableDownloads(sourceId)
                        ?.filter { it.sizeBytes > 0L }?.mapTo(mutableSetOf()) { it.track.id.value }
                },
            )
            val radio = NaviampCoreInternetRadioController(
                stateStore,
                providerSource,
                services.radio.playback,
                services.radio.recents,
                onPlaybackStarted = { station ->
                    selectionStarted()
                    samplePlayback.stop()
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
                routedPlaybackEffects,
                services.playback.sidecars,
                services.downloads.network,
                radio::stations,
            )
            val queueUndo = NaviampCoreQueueUndoController(
                scope = scope,
                stateStore = stateStore,
                playback = livePlayback,
                queue = queue,
                effects = routedPlaybackEffects,
                playbackOutput = { playbackOutputs.state.value },
                publishNowPlaying = nowPlayingPresenter::publish,
            )
            val queuePlayback = NaviampCoreQueuePlaybackController(
                playback = livePlayback,
                queue = queue,
                effects = routedPlaybackEffects,
                publishNowPlaying = nowPlayingPresenter::publish,
                openNowPlaying = navigation::openNowPlaying,
                activeSourceId = { stateStore.state.value.shell.connectionSettings.currentSourceId },
                profiles = services.playback.profiles,
                undo = queueUndo,
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
                services.downloads.offlinePlaylists,
            )
            val publishPlaylistQueueUpdate: () -> Unit = nowPlayingPresenter::publish
            val playlistTransactions = NaviampCorePlaylistTransactionController(
                stateStore,
                providerSource,
                playlistBrowse,
                playback = NaviampCorePlaylistPlaybackPort { playlist, tracks, shuffle ->
                    queuePlayback.play(
                        tracks = tracks,
                        shuffle = shuffle,
                        groupTarget = PlaybackProfileTarget(
                            PlaybackProfileTargetType.Playlist,
                            playlist.id,
                        ),
                        groupLabel = playlist.name,
                    )
                },
                queue = NaviampCorePlaylistQueuePort { playlist, tracks ->
                    val update = queuePlayback.addToQueue(
                        tracks = tracks,
                        groupTarget = PlaybackProfileTarget(
                            PlaybackProfileTargetType.Playlist,
                            playlist.id,
                        ),
                        groupLabel = playlist.name,
                    )
                    if (update.tracksChanged) {
                        routedPlaybackEffects.applyQueue(update.queue, clearPreparedNext = true)
                        publishPlaylistQueueUpdate()
                    }
                },
                NaviampCorePlaylistDownloadPort(downloads::downloadPlaylist),
                services.playlists.history,
                services.connection,
                services.playlists.preview,
                navigation::openNowPlaying,
                downloads::playlistTracksChanged,
                playbackProfiles,
                syncLibrary = catalog::syncSmartPlaylistPreview,
                messages = services.playlists.messages,
            )
            val playback = NaviampCorePlaybackController(
                scope,
                stateStore,
                providerSource,
                livePlayback,
                queue,
                routedPlaybackEffects,
                services.playback.settings,
                services.playback.sidecars,
                services.playback.sessions,
                nowPlayingPresenter,
                services.clockEpochMillis,
                onTrackPlayed = { sourceId, track, playedAt ->
                    scope.launch { home.recordPlayedTrack(sourceId, track, playedAt) }
                },
                queueUndo = queueUndo,
            )
            notifyPlaybackSettingsChanged = playback::playbackSettingsChanged
            val generatedRadioRecents = NaviampRecentRadioStreamController(
                load = services.radio.generatedRecents.load,
                save = services.radio.generatedRecents.save,
                onChanged = { notifyLocalSettingsChanged() },
                currentSourceId = { stateStore.state.value.shell.connectionSettings.currentSourceId },
                nowEpochMillis = services.clockEpochMillis,
            )
            val mediaTransactions = NaviampCoreMediaTransactions(
                stateStore,
                busyIndicator,
                providerSource,
                mediaRegistry,
                livePlayback,
                queue,
                routedPlaybackEffects,
                queuePlayback,
                downloads,
                mediaDetails,
                generatedRadioRecents,
                services.content.externalUri,
                services.favoritedAtIso8601,
                { nowPlayingPresenter.publish(playback.currentDisplay()) },
                navigation::openNowPlaying,
                favoriteArtistActivity = services.content.homeLibrary,
                onFavoriteArtistActivityChanged = { scope.launch { home.refreshAfterConnection() } },
                onAlbumUpdated = { provider, album -> services.content.albumIndex?.updateAlbum(provider, album) },
                queueUndo = queueUndo,
            )
            val playlistMembership = NaviampCorePlaylistMembershipCoordinator(
                providerSource = providerSource,
                currentEditor = { stateStore.state.value.shell.playlistMembership },
                publish = { editor -> stateStore.updateShell { it.copy(playlistMembership = editor) } },
                onPlaylistChanged = downloads::playlistTracksChanged,
                onContentsReconciled = playlistBrowse::reconcileContents,
                onPlaylistCreated = playlistBrowse::publishCreated,
            )
            val nowPlaying = NaviampCoreNowPlayingMediaController(
                stateStore,
                providerSource,
                livePlayback,
                queue,
                routedPlaybackEffects,
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
                onPlaylistContentsReconciled = playlistBrowse::reconcileContents,
                onPlaylistCreated = playlistBrowse::publishCreated,
                membershipCoordinator = playlistMembership,
                queueUndo = queueUndo,
            )
            val connectCatalog = NaviampCoreConnectCatalogController(
                providerSource = providerSource,
                media = mediaTransactions,
                radio = radio,
                registry = mediaRegistry,
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

                    override suspend fun playGenreSongs(tracks: List<app.naviamp.domain.Track>, startIndex: Int) {
                        mediaTransactions.play(tracks, startIndex)
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
            val trackActions = NaviampCoreTrackActionController(mediaRegistry, mediaTransactions,
                isUnavailableOfflinePlaylistTrack = playlistBrowse::isUnavailableOffline,
                notDownloadedMessage = services.playlists.messages.trackNotDownloaded,
                publishPlaylistNotice = playlistBrowse::publishDetailNotice,
                openPlaylistMembership = playlistMembership::open)
            // Resolved mix commands re-enter the Connect-aware handler created below.
            var resolvedCommandHandler: NaviampCoreCommandHandler? = null
            val collectionActions = NaviampCoreCollectionActionController(
                providerSource,
                mediaRegistry,
                mediaTransactions,
                mediaDetails,
                playbackProfiles,
                settings,
                issueResolvedCommand = { command -> checkNotNull(resolvedCommandHandler).execute(command) },
            )
            val somethingNew = NaviampCoreSomethingNewController(
                stateStore = stateStore,
                providerSource = providerSource,
                downloadedTracks = { sourceId ->
                    services.downloads.storage.availableDownloads(sourceId).orEmpty()
                        .filter { it.sizeBytes > 0 }.map { it.track }
                },
                pendingListens = services.providerActions::pendingListenTrackIds,
                currentTrack = { livePlayback.state.value.currentTrack ?: livePlayback.state.value.queue.current },
                radio = mediaTransactions,
                playTrack = { track -> mediaTransactions.play(listOf(track)) },
                issueResolvedCommand = { command -> checkNotNull(resolvedCommandHandler).execute(command) },
            )
            val noticeLifetimes = NaviampCoreNoticeLifetimeController(scope, stateStore).also { it.start() }
            val providerSessionLifecycle = NaviampCoreProviderSessionLifecycle(
                sessionPort = services.connection,
            )
            var connectionRestore: kotlinx.coroutines.Job? = null
            val restoreLocalSession: (String, Boolean) -> Unit = { sourceId, reconcileDownloads ->
                connectionRestore?.cancel()
                connectionRestore = scope.launch {
                    val reopenNowPlaying = services.playback.sessions.load(sourceId)?.nowPlayingOpen == true
                    if (playback.restoreSession(sourceId) && reopenNowPlaying) {
                        navigation.restoreNowPlayingOpen()
                    }
                }
                scope.launch { downloads.refresh(reconcile = reconcileDownloads) }
            }
            val refreshConnectedSource: (String, Boolean, Boolean) -> Unit = { sourceId, restorePlayback, restorePhoneUi ->
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
                                    similarityDiagnosticsAvailable = providerCapabilities.supportsSimilarityDiagnostics,
                                ),
                            )
                        }
                    }
                    if (restorePlayback && restorePhoneUi) restoreLocalSession(sourceId, true)
                    else {
                        connectionRestore?.cancel()
                        connectionRestore = if (restorePlayback) scope.launch { playback.restoreSession(sourceId, allowAutoPlay = false) } else null
                        scope.launch { downloads.refresh(reconcile = true) }
                    }
                    scope.launch {
                        home.refreshAfterConnection()
                        home.restoreRecentRadioStreams(generatedRadioRecents.current())
                    }
                    scope.launch { catalog.refreshAfterConnection() }
                    scope.launch { playlistBrowse.refreshAfterConnection() }
                    scope.launch { radio.refreshAfterConnection() }
                }
            val connection = NaviampCoreConnectionController(
                NaviampConnectionController(initialState.connection),
                stateStore,
                services.connection,
                initialState.connectionInventory,
                certificateFiles = services.certificateFiles,
                  onSourceChanging = { previousSourceId, newSourceId ->
                    connectionRestore?.cancel()
                    connectionRestore = null
                    stateStore.update { it.copy(sourceGeneration = it.sourceGeneration + 1L) }
                    similarity.resetForSourceChange()
                    stateStore.updateShell { shell -> shell.copy(playback = shell.playback.copy(
                        similarityTest = app.naviamp.ui.NaviampSimilarityTestUi(), radioDiagnostics = null,
                        similarityDiagnosticsAvailable = false,
                    )) }
                    stateStore.update { it.copy(overlays = it.overlays.copy(radioNotice = null, somethingNewNotice = null)) }
                    playback.resetForSourceChange(previousSourceId, newSourceId)
                    playlistMembership.reset()
                    downloads.resetForSourceChange()
                    radio.resetForSourceChange()
                    home.resetForSourceChange()
                    catalog.resetForSourceChange()
                    playlistBrowse.resetForSourceChange()
                    mediaDetails.resetForSourceChange()
                    standardMixes.resetForSourceChange()
                    sonicBuilders.resetForSourceChange()
                },
                onCompanionChanged = { sourceId ->
                    notifyLocalSettingsChanged()
                    if (sourceId == services.connection.currentSourceId()) {
                        samplePlayback.stop()
                        nowPlayingPresenter.publish()
                        stateStore.updateShell { shell -> shell.copy(artistDetail = shell.artistDetail.copy(
                            detail = shell.artistDetail.detail?.copy(aurralSourceId = sourceId.takeIf {
                                services.connection.currentProvider()?.discovery != null
                            }))) }
                        scope.launch { home.refreshAfterCompanionChange() }
                    }
                },
                onConnected = { sourceId -> refreshConnectedSource(sourceId, true, true) },
                onSavedSourceSelected = { sourceId, changed -> refreshConnectedSource(sourceId, changed, false) },
                onUserConnected = {
                    navigation.dispatch(NaviampCoreCommand.Navigation.SelectRoute(app.naviamp.ui.SharedRoute.Home))
                },
                onOfflineRestored = { sourceId ->
                    restoreLocalSession(sourceId, false)
                    scope.launch { playlistBrowse.refreshAfterConnection() }
                },
                networkMetering = services.playback.networkMetering,
                offlineModeEnabled = { stateStore.state.value.shell.cache.settings.offlineModeEnabled },
                // Refresh what offline hid (library, playlists, Home, Keep downloaded) and keep playing.
                onReconnected = { sourceId -> refreshConnectedSource(sourceId, false, false) },
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
                onDocumentApplied = { snapshot, imported ->
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
                    imported?.let { source ->
                        // A matched connection that already holds credentials reconnects as is;
                        // only a missing or rejected credential opens its password form.
                        val reconnected = source.hasStoredCredentials() &&
                            connection.reconnectImportedConnection(source.id)
                        if (!reconnected) {
                            connection.editImportedConnection(
                                id = source.id,
                                form = source.toSettingsSyncServerProfile().toConnectionFormState(),
                            )
                            navigation.dispatch(NaviampCoreCommand.Navigation.SelectRoute(SharedRoute.Settings))
                        }
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
                    queueUndo,
                    trackActions,
                    collectionActions,
                    somethingNew,
                    noticeLifetimes,
                ),
                onAsyncFailure = onAsyncFailure,
            )
            val connect = services.connect?.let { connectServices ->
                val supportsRemotePlayback = NaviampConnectDeviceCapability.PlaybackTarget in
                    connectServices.deviceCapabilities
                NaviampCoreConnectController(
                    scope = scope,
                    stateStore = stateStore,
                    services = connectServices,
                    targetPlayback = playback.takeIf { supportsRemotePlayback },
                    targetNowPlaying = nowPlaying.takeIf { supportsRemotePlayback },
                    targetCatalog = connectCatalog.takeIf { supportsRemotePlayback },
                    localPlayback = playback,
                    sourceIdentity = {
                        naviampCoreConnectSourceIdentity(stateStore, providerSource)
                    },
                    providerSessions = services.connection,
                    localConnection = connection,
                    targetConnection = connection.takeIf { supportsRemotePlayback },
                    targetSettings = settings.takeIf { supportsRemotePlayback },
                    revealTargetNowPlaying = navigation::openNowPlaying,
                    playbackOutputs = playbackOutputs,
                    trackSelectionContext = mediaRegistry::connectTrackSelectionContext,
                savedPlaybackProfile = playbackProfiles::profile,
                    localSettingsSnapshot = { runCatching { services.settings.sync.controller.localSnapshot() }.getOrNull() },
                    applyTransferredPreferences = settingsSync::applyTransferredPreferences,
                )
            }
            val castEffect = services.cast?.let { castServices ->
                castServices.session ?: checkNotNull(castServices.channel).let { native ->
                    app.naviamp.app.NaviampCastChannelSessionEffect(scope, native.transport,
                        app.naviamp.app.NaviampCastDeviceAuthentication(native.crypto, services.clockEpochMillis),
                        services.clockEpochMillis)
                }
            }
            val castSessions = castEffect?.let {
                NaviampCastSessionController(it, playbackOutputs).takeIf { sessions -> sessions.start() }
            }
            val availableCast = services.cast?.takeIf { castSessions != null }
            val castPicker = availableCast?.discovery?.let { effect ->
                NaviampCoreCastPickerController(scope,
                    app.naviamp.app.NaviampCastDiscoveryController(effect, services.clockEpochMillis),
                    checkNotNull(castSessions), playbackOutputs)
            }
            val cast = availableCast?.let { castServices ->
                NaviampCoreCastController(
                    scope = scope,
                    sessions = checkNotNull(castSessions),
                    outputs = playbackOutputs,
                    endpoint = NaviampCastMediaEndpointController(
                        server = castServices.server,
                        leases = NaviampCastMediaLeaseController(
                            tokens = castServices.tokens,
                            nowEpochMillis = services.clockEpochMillis,
                        ),
                        source = NaviampCoreCastMediaByteSource(providerSource, castServices.mediaStore),
                        localAddress = { castEffect.localAddress },
                    ),
                    providers = providerSource,
                    playback = livePlayback,
                    queue = queue,
                    local = services.playback.effects,
                    publishNowPlaying = { nowPlayingPresenter.publish(playback.currentDisplay()) },
                ).also { controller ->
                    routedPlaybackEffects.cast = controller
                    controller.start()
                }
            }
            val commandHandler = NaviampCoreConnectCommandHandler(router, connect, samplePlayback::stop,
                onPlaybackIntent = { stateStore.update { it.copy(playbackIntentGeneration = it.playbackIntentGeneration + 1L) } },
                closeDelegate = router::close)
            resolvedCommandHandler = commandHandler
            pauseMainForSample = { commandHandler.dispatch(NaviampCoreCommand.NowPlaying.Playback(
                NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.Pause))) }
            livePlayback.observe { live -> connect?.onLocalPlaybackChanged(live) }
            nowPlayingPresenter.publish()
            scope.launch { connection.restoreInitialConnection() }
            return NaviampCore(
                stateStore = stateStore,
                playbackProgress = livePlayback.progress,
                actions = createNaviampCoreActions(commandHandler,
                    actionAvailability.copy(chooseCertificate = services.certificateFiles != null), connect?.actions),
                commands = commandHandler,
                closeCommands = commandHandler::close,
                playbackController = playback,
                nowPlayingController = nowPlaying,
                nowPlayingPresenter = nowPlayingPresenter,
                providerSessionLifecycle = providerSessionLifecycle,
                startConnectionRecovery = connection::startRecovery,
                runtimeScope = scope,
                homeController = home,
                providerSource = providerSource,
                sidecars = services.playback.sidecars,
                samplePlayback = samplePlayback,
                diagnostics = services.diagnostics,
                connectController = connect,
                castController = cast,
                castPicker = castPicker,
                showCastRoutePicker = availableCast?.showRoutePicker,
                playbackOutputs = playbackOutputs,
                vehicleCollections = NaviampCoreVehicleCollectionAccess(stateStore.state, providerSource, queuePlayback),
                vehicleArtwork = services.content.artwork?.let { NaviampCoreVehicleArtworkSource(stateStore.state, providerSource, it) },
                vehicleLibrary = { labels -> NaviampCoreVehicleLibraryAccess(stateStore.state, providerSource,
                    { track -> queuePlayback.play(listOf(track)) }, labels) },
                vehicleDownloads = { labels, downloadsLabels -> NaviampVehicleDownloadsAccess(stateStore.state,
                    services.downloads.storage, { routedPlaybackEffects.capabilities.supportsDownloadedOnly },
                    { tracks, index, shuffle, reference -> queuePlayback.play(tracks, index, shuffle,
                        groupTarget = if (reference.kind == NaviampVehicleLibraryKind.DownloadedAlbumTracks)
                            app.naviamp.domain.playback.PlaybackProfileTarget(app.naviamp.domain.playback.PlaybackProfileTargetType.Album, reference.id) else null,
                        groupLabel = reference.title, downloadedOnly = true) },
                    labels, downloadsLabels, { routedPlaybackEffects.downloadedOnlyQueue }) },
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
