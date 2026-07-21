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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

data class NaviampCoreInitialState(
    val product: NaviampCoreState = NaviampCoreState(),
    val navigation: NaviampNavigationState = NaviampNavigationState(),
    val playback: NaviampLivePlaybackState = NaviampLivePlaybackState(),
    val connection: NaviampConnectionRuntimeState = NaviampConnectionRuntimeState(),
    val connectionInventory: NaviampCoreConnectionInventory = NaviampCoreConnectionInventory(),
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
            )
            deferredArtistNavigator.target = mediaDetails

            val connection = NaviampCoreConnectionController(
                NaviampConnectionController(initialState.connection),
                stateStore,
                services.connection,
                initialState.connectionInventory,
            )
            val settings = NaviampCoreSettingsController(
                stateStore,
                services.settings.interfaceSettings,
                services.playback.settings,
                services.settings.cacheSettings,
                services.settings.maintenance,
            )
            val settingsSync = NaviampCoreSettingsSyncController(stateStore, services.settings.sync)
            val catalog = NaviampCoreCatalogController(stateStore, services.content.providerSource)
            val home = NaviampCoreHomeController(
                stateStore,
                services.content.providerSource,
                navigation,
                services.content.homeDate,
                services.content.homeSupplement,
                services.content.providerResponses,
                services.content.homeLibrary,
            )
            val playlistBrowse = NaviampCorePlaylistBrowseController(
                stateStore,
                services.content.providerSource,
                navigation,
                services.content.playlistSupplement,
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
                services.playlists.playback,
                services.playlists.queue,
                services.playlists.downloads,
                services.playlists.history,
                services.playlists.smartProviderSource,
            )
            val radio = NaviampCoreInternetRadioController(
                stateStore,
                services.content.providerSource,
                services.radio.playback,
                services.radio.recents,
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
                stateStore,
                services.content.providerSource,
                livePlayback,
                queue,
                services.playback.effects,
                services.playback.settings,
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
                ),
                onAsyncFailure = onAsyncFailure,
            )
            nowPlayingPresenter.publish()
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
