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
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.toConnectionHeaderDefinitions
import app.naviamp.domain.settings.toConnectionSecondaryUrls
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.navidromeTlsSettingsFromForm
import app.naviamp.ui.NaviampSleepTimerExpiryEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

        LaunchedEffect(
            editingConnection,
            serverUrl,
            username,
            password,
            connectionName,
            skipTlsVerification,
            customCertificatePath,
            clientCertificatePath,
            clientCertificatePassword,
            secondaryUrls,
            customHeaders,
            savedConnectionForLogin,
        ) {
            if (!editingConnection) {
                availableMusicFolders = emptyList()
                musicFoldersStatus = null
                return@LaunchedEffect
            }

            val baseUrl = serverUrl.trim()
            val loginUsername = username.trim()
            val savedLogin = savedConnectionForLogin
            if (baseUrl.isEmpty() || loginUsername.isEmpty() || (savedLogin == null && password.isBlank())) {
                availableMusicFolders = emptyList()
                musicFoldersStatus = "Enter connection details to load libraries."
                return@LaunchedEffect
            }

            musicFoldersStatus = "Loading libraries..."
            val tlsSettings = navidromeTlsSettingsFromForm(
                insecureSkipTlsVerification = skipTlsVerification,
                customCertificatePath = customCertificatePath,
                clientCertificateKeyStorePath = clientCertificatePath,
                clientCertificateKeyStorePassword = clientCertificatePassword,
            )
            val lookupSecondaryUrls = secondaryUrls.toConnectionSecondaryUrls()
            val lookupCustomHeaders = customHeaders.toConnectionHeaderDefinitions()
            val lookupConnection = if (savedLogin != null && password.isBlank()) {
                savedLogin.copy(
                    baseUrl = baseUrl,
                    username = loginUsername,
                    tlsSettings = tlsSettings,
                    secondaryUrls = lookupSecondaryUrls,
                    customHeaders = lookupCustomHeaders,
                )
            } else {
                NavidromeConnection.fromPassword(
                    baseUrl = baseUrl,
                    username = loginUsername,
                    password = password,
                    displayName = connectionName.ifBlank { null },
                    tlsSettings = tlsSettings,
                    secondaryUrls = lookupSecondaryUrls,
                    customHeaders = lookupCustomHeaders,
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    NavidromeProvider(lookupConnection).musicFolders()
                }
            }.fold(
                onSuccess = { folders ->
                    val choices = folders.mapIndexed { index, folder ->
                        ConnectionFormMusicFolder(
                            id = folder.id,
                            name = folder.name,
                            defaultSelected = index == 0,
                        )
                    }
                    availableMusicFolders = choices
                    musicFoldersStatus = when {
                        choices.isEmpty() -> "No libraries returned by the server."
                        else -> null
                    }
                    if (selectedMusicFolderIds.isEmpty() && choices.isNotEmpty()) {
                        selectedMusicFolderIds = listOf(choices.first().id)
                    }
                },
                onFailure = { error ->
                    availableMusicFolders = emptyList()
                    musicFoldersStatus = "Could not load libraries: ${error.message ?: error::class.simpleName}"
                },
            )
        }

        NaviampSleepTimerExpiryEffect(
            sleepTimer = sleepTimer,
            snapshot = sleepTimerController.snapshot(),
            onTick = sleepTimerController::tick,
            onExpired = sleepTimerController::expire,
        )
    }
}
