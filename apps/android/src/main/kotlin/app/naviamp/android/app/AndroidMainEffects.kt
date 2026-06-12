package app.naviamp.android

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.SleepTimerController
import app.naviamp.domain.provider.PlaylistDetailRefreshIntervalMillis
import app.naviamp.domain.provider.playlistDetailAutoRefreshTarget
import app.naviamp.domain.provider.runPlaylistDetailAutoRefresh
import app.naviamp.ui.NaviampSleepTimerExpiryEffect
import kotlinx.coroutines.delay

@Composable
internal fun AndroidMainEffects(
    state: AndroidAppState,
    searchController: AndroidSearchController,
    nowPlayingSidecarController: AndroidNowPlayingSidecarController,
    androidAutoController: AndroidAutoAppController,
    navigationController: AndroidNavigationController,
    mediaAppController: AndroidMediaAppController,
    mixBuilderController: AndroidMixBuilderController,
    sonicHomeDiscoveryController: AndroidSonicHomeDiscoveryController,
    connectionSessionController: AndroidConnectionSessionController,
    sleepTimerController: SleepTimerController,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    onAutoPlayMediaIdConsumed: () -> Unit,
    onAutoCommandConsumed: () -> Unit,
) {
    with(state) {
        LaunchedEffect(query, provider) {
            searchController.load(query, debounce = true)
        }

        LaunchedEffect(nowPlaying?.id, activeSourceId, provider) {
            nowPlaying
                ?.takeUnless { it.isInternetRadioTrack() }
                ?.let(nowPlayingSidecarController::loadAudioTags)
        }

        LaunchedEffect(pendingAutoPlayMediaId, provider, activeSourceId) {
            androidAutoController.consumePendingMediaId(onAutoPlayMediaIdConsumed)
        }

        LaunchedEffect(pendingAutoCommand, provider, activeSourceId, nowPlaying?.id, playbackQueue) {
            androidAutoController.consumePendingCommand(onAutoCommandConsumed)
        }

        LaunchedEffect(nowPlaying?.id, nowPlaying?.favoritedAtIso8601, provider?.capabilities?.supportsTrackFavorites) {
            mediaAppController.updateNotificationFavoriteState()
        }

        BackHandler(enabled = navigationController.handlesAndroidBack()) {
            navigationController.handleAndroidBack()
        }

        LaunchedEffect(provider, homeState.artists) {
            if (provider != null && artistMixSuggestions.isEmpty()) {
                mixBuilderController.refreshArtistInitialSuggestions()
            }
        }

        LaunchedEffect(provider, homeState.randomAlbums, homeState.mixAlbums) {
            if (provider != null && albumMixSuggestions.isEmpty()) {
                mixBuilderController.refreshAlbumInitialSuggestions()
            }
        }

        LaunchedEffect(provider, homeState.genres) {
            if (provider != null && genreMixSuggestions.isEmpty()) {
                mixBuilderController.refreshGenreSuggestions()
            }
        }

        LaunchedEffect(
            provider,
            activeSourceId,
            playbackSettings.sonicSimilarityEnabled,
            provider?.capabilities?.supportsSonicSimilarity,
            isLibrarySyncing,
            nowPlaying?.id,
            playbackQueue.tracks.size,
        ) {
            val enabled = playbackSettings.sonicSimilarityEnabled &&
                provider?.capabilities?.supportsSonicSimilarity == true &&
                !isLibrarySyncing
            sonicHomeDiscoveryController.loadIfNeeded(enabled)
        }

        LaunchedEffect(provider, selectedPlaylist?.id) {
            val target = playlistDetailAutoRefreshTarget(
                provider = provider,
                playlist = selectedPlaylist,
            ) ?: return@LaunchedEffect
            runPlaylistDetailAutoRefresh(
                target = target,
                waitForNextRefresh = {
                    delay(PlaylistDetailRefreshIntervalMillis)
                },
            ) { activeProvider, playlist ->
                refreshAndroidPlaylistDetailsFromServer(
                    state = state,
                    activeProvider = activeProvider,
                    playlist = playlist,
                    showLoadingStatus = false,
                    providerResponseCacheRepository = providerResponseCacheRepository,
                )
            }
        }

        LaunchedEffect(Unit) {
            connectionSessionController.autoConnect()
        }

        NaviampSleepTimerExpiryEffect(
            sleepTimer = sleepTimer,
            snapshot = sleepTimerController.snapshot(),
            onTick = sleepTimerController::tick,
            onExpired = sleepTimerController::expire,
        )
    }
}
