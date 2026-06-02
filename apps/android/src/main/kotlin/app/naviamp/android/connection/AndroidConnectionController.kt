package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackTls
import app.naviamp.domain.Playlist
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.connectionFormError
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeConnectionLoginRequest
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.navidromeTlsSettingsFromForm
import app.naviamp.provider.navidrome.prepareNavidromeConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    storage: AndroidStorage,
    playbackEngine: AndroidPlaybackEngine,
    preloadPlaylistTracks: (NavidromeProvider, List<Playlist>) -> Unit,
    restorePlaybackSession: (String) -> Boolean,
    startAndroidLibrarySync: (Boolean) -> Unit,
    checkAndroidLibraryFreshness: () -> Unit,
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
                val mediaSource = storage.upsertNavidromeSource(
                    connection = connection,
                    cacheNamespace = nextProvider.cacheNamespace,
                    providerId = nextProvider.id.value,
                )
                homeState = loadBrowseState(nextProvider, storage)
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
