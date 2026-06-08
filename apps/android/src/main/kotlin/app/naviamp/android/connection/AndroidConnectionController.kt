package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackTls
import app.naviamp.domain.Playlist
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.connectionFormError
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeConnectionLoginRequest
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.navidromeTlsSettingsFromForm
import app.naviamp.provider.navidrome.prepareNavidromeConnection
import app.naviamp.provider.navidrome.resolvedDisplayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AndroidConnectionSessionController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val storage: AndroidStorageDependencies,
    private val settingsStore: AndroidSettingsStore,
    private val savedProviderConnection: NavidromeConnection?,
    private val savedConnection: ConnectionFormState,
    private val playbackEngine: AndroidPlaybackEngine,
    private val preloadPlaylistTracks: (NavidromeProvider, List<Playlist>) -> Unit,
    private val loadRelatedTracks: (Track) -> Unit,
    private val startAndroidLibrarySync: (Boolean) -> Unit,
    private val checkAndroidLibraryFreshness: () -> Unit,
) {
    fun restorePlaybackSession(sourceId: String): Boolean =
        restoreAndroidPlaybackSession(state, storage, sourceId, loadRelatedTracks)

    fun connectWithNavidromeConnection(connection: NavidromeConnection) {
        startNavidromeConnection(
            scope = scope,
            state = state,
            connection = connection,
            providerMediaSourceRepository = storage,
            providerResponseCacheRepository = storage,
            playbackEngine = playbackEngine,
            preloadPlaylistTracks = preloadPlaylistTracks,
            restorePlaybackSession = ::restorePlaybackSession,
            startAndroidLibrarySync = startAndroidLibrarySync,
            checkAndroidLibraryFreshness = checkAndroidLibraryFreshness,
            recentRadioStreams = settingsStore.loadRecentRadioStreams(),
            recentInternetRadioStations = settingsStore.loadRecentInternetRadioStations().map { it.toStation() },
        )
    }

    fun connectToNavidrome() {
        startNavidromeConnectionFromForm(
            scope = scope,
            state = state,
            settingsStore = settingsStore,
            savedProviderConnection = savedProviderConnection,
            connectWithNavidromeConnection = ::connectWithNavidromeConnection,
        )
    }

    fun autoConnect() {
        when {
            savedProviderConnection != null -> connectWithNavidromeConnection(savedProviderConnection)
            savedConnection.serverUrl.isNotBlank() &&
                savedConnection.username.isNotBlank() &&
                savedConnection.password.isNotBlank() -> {
                connectToNavidrome()
            }
        }
    }
}

fun AndroidAppState.applyConnectionForm(form: ConnectionFormState) {
    connectionName = form.displayName
    serverUrl = form.serverUrl
    username = form.username
    password = form.password
    skipTlsVerification = form.skipTlsVerification
    customCertificatePath = form.customCertificatePath
    clientCertificatePath = form.clientCertificatePath
    clientCertificatePassword = form.clientCertificatePassword
}

fun AndroidAppState.currentConnectionForm(): ConnectionFormState =
    ConnectionFormState(
        displayName = connectionName,
        serverUrl = serverUrl,
        username = username,
        password = password,
        skipTlsVerification = skipTlsVerification,
        customCertificatePath = customCertificatePath,
        clientCertificatePath = clientCertificatePath,
        clientCertificatePassword = clientCertificatePassword,
    )

fun startNavidromeConnection(
    scope: CoroutineScope,
    state: AndroidAppState,
    connection: NavidromeConnection,
    providerMediaSourceRepository: ProviderMediaSourceRepository,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    playbackEngine: AndroidPlaybackEngine,
    preloadPlaylistTracks: (NavidromeProvider, List<Playlist>) -> Unit,
    restorePlaybackSession: (String) -> Boolean,
    startAndroidLibrarySync: (Boolean) -> Unit,
    checkAndroidLibraryFreshness: () -> Unit,
    recentRadioStreams: List<RecentRadioStream> = emptyList(),
    recentInternetRadioStations: List<InternetRadioStation> = emptyList(),
) {
    scope.launch {
        with(state) {
            status = "Connecting..."
            runCatching {
                val tlsSettings = connection.tlsSettings
                val nextProvider = NavidromeProvider(connection)
                playbackEngine.applyTlsSettings(tlsSettings)
                AndroidPlaybackTls.applyDefaults(tlsSettings)
                validation = nextProvider.validateConnection()
                val mediaSource = providerMediaSourceRepository.upsertProviderMediaSource(
                    connection = connection.toProviderMediaSourceConnection(),
                    cacheNamespace = nextProvider.cacheNamespace,
                    providerId = nextProvider.id.value,
                )
                homeState = loadBrowseState(
                    provider = nextProvider,
                    providerResponseCacheRepository = providerResponseCacheRepository,
                    recentRadioStreams = recentRadioStreams,
                    recentInternetRadioStations = recentInternetRadioStations,
                )
                preloadPlaylistTracks(nextProvider, homeState.playlists)
                provider = nextProvider
                activeSourceId = mediaSource.id
                activeTlsSettings = tlsSettings
                restorePlaybackSession(mediaSource.id)
            }.onSuccess {
                if (nowPlaying == null && nowPlayingStation == null) {
                    status = "Connected."
                }
                restoringConnection = false
                editingConnection = false
                navigationState = navigationState.copy(route = NaviampRoute.Home)
                startAndroidLibrarySync(false)
                checkAndroidLibraryFreshness()
            }.onFailure { error ->
                status = error.message ?: "Connection failed."
                restoringConnection = false
                provider = null
                validation = null
            }
        }
    }
}

private fun NavidromeConnection.toProviderMediaSourceConnection(): ProviderMediaSourceConnection =
    ProviderMediaSourceConnection(
        displayName = resolvedDisplayName(),
        baseUrl = baseUrl,
        username = username,
        token = token,
        salt = salt,
        nativeToken = nativeToken,
        tlsSettings = tlsSettings,
    )

fun startNavidromeConnectionFromForm(
    scope: CoroutineScope,
    state: AndroidAppState,
    settingsStore: AndroidSettingsStore,
    savedProviderConnection: NavidromeConnection?,
    connectWithNavidromeConnection: (NavidromeConnection) -> Unit,
) {
    val connectionForm = state.currentConnectionForm()
    val formError = connectionFormError(
        form = connectionForm,
        hasSavedConnectionForLogin = savedProviderConnection != null,
    )
    if (formError != null) {
        state.status = formError
        state.restoringConnection = false
        return
    }
    val tlsSettings = navidromeTlsSettingsFromForm(
        insecureSkipTlsVerification = connectionForm.skipTlsVerification,
        customCertificatePath = connectionForm.customCertificatePath,
        clientCertificateKeyStorePath = connectionForm.clientCertificatePath,
        clientCertificateKeyStorePassword = connectionForm.clientCertificatePassword,
    )
    scope.launch {
        runCatching {
            prepareNavidromeConnection(
                NavidromeConnectionLoginRequest(
                    baseUrl = connectionForm.serverUrl,
                    username = connectionForm.username,
                    password = connectionForm.password,
                    displayName = connectionForm.displayName.trim().takeIf { it.isNotEmpty() },
                    tlsSettings = tlsSettings,
                    savedConnectionForLogin = savedProviderConnection,
                ),
            ).connection
        }.onSuccess { connection ->
            settingsStore.saveConnection(connectionForm)
            connectWithNavidromeConnection(connection)
        }.onFailure { error ->
            state.status = error.message ?: "Connection failed."
            state.restoringConnection = false
            state.provider = null
            state.validation = null
        }
    }
}
