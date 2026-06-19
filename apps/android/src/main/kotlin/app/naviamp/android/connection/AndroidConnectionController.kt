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
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.connectionFormError
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.ProviderConnectionLifecycleRequest
import app.naviamp.domain.source.connectionFailureStatus
import app.naviamp.domain.source.openProviderConnectionSession
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeConnectionLoginRequest
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.provider.navidrome.navidromeTlsSettingsFromForm
import app.naviamp.provider.navidrome.prepareNavidromeConnection
import app.naviamp.provider.navidrome.resolvedDisplayName
import app.naviamp.provider.navidrome.toNavidromeConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AndroidConnectionSessionController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val storage: AndroidStorageDependencies,
    private val settingsStore: AndroidSettingsStore,
    private val savedConnection: ConnectionFormState,
    private val playbackEngine: AndroidPlaybackEngine,
    private val queueController: PlaybackQueueController,
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
            pendingProviderActionRepository = storage,
            homeLibraryRepository = storage.asHomeLibraryRepository(),
            playbackEngine = playbackEngine,
            preloadPlaylistTracks = preloadPlaylistTracks,
            restorePlaybackSession = ::restorePlaybackSession,
            startAndroidLibrarySync = startAndroidLibrarySync,
            checkAndroidLibraryFreshness = checkAndroidLibraryFreshness,
            recentRadioStreams = settingsStore.loadRecentRadioStreams(),
            recentInternetRadioStations = settingsStore.loadRecentInternetRadioStations().map { it.toStation() },
            refreshSavedMediaSources = { state.savedMediaSources = storage.mediaSources() },
        )
    }

    fun connectToNavidrome() {
        startNavidromeConnectionFromForm(
            scope = scope,
            state = state,
            settingsStore = settingsStore,
            connectWithNavidromeConnection = ::connectWithNavidromeConnection,
        )
    }

    fun autoConnect() {
        when {
            state.savedConnectionForLogin != null -> connectWithNavidromeConnection(state.savedConnectionForLogin!!)
            savedConnection.serverUrl.isNotBlank() &&
                savedConnection.username.isNotBlank() &&
                savedConnection.password.isNotBlank() -> {
                connectToNavidrome()
            }
        }
    }

    fun openNewConnectionForm() {
        state.savedConnectionForLogin = null
        state.applyConnectionForm(ConnectionFormState())
        state.editingConnection = true
        state.restoringConnection = false
        state.status = "Add a Navidrome connection."
    }

    fun openSavedConnectionForm(source: SavedMediaSource) {
        val connection = source.toNavidromeConnection()
        state.savedConnectionForLogin = connection
        state.applyConnectionForm(settingsStore.loadConnection(connection).copy(password = ""))
        state.editingConnection = true
        state.restoringConnection = false
        state.status = "Editing saved connection. Leave password blank to reuse it."
    }

    fun connectSavedConnection(source: SavedMediaSource) {
        val connection = source.toNavidromeConnection()
        state.savedConnectionForLogin = connection
        state.applyConnectionForm(settingsStore.loadConnection(connection).copy(password = ""))
        state.editingConnection = false
        connectWithNavidromeConnection(connection)
    }

    fun deleteConnection(source: SavedMediaSource) {
        storage.deleteMediaSource(source.id)
        state.savedMediaSources = storage.mediaSources()
        if (state.activeSourceId == source.id) {
            resetAndroidPlaybackState(state, playbackEngine, queueController)
            state.provider = null
            state.activeSourceId = null
            state.validation = null
            state.activeTlsSettings = NavidromeTlsSettings()
            state.savedConnectionForLogin = null
            state.editingConnection = true
            state.navigationState = state.navigationState.copy(route = NaviampRoute.Settings)
            clearAndroidDerivedMediaState(state)
        } else if (state.savedConnectionForLogin?.baseUrl == source.baseUrl &&
            state.savedConnectionForLogin?.username == source.username
        ) {
            state.savedConnectionForLogin = null
        }
        state.status = "Deleted ${source.displayName}."
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
    pendingProviderActionRepository: PendingProviderActionRepository,
    homeLibraryRepository: HomeLibraryRepository? = null,
    playbackEngine: AndroidPlaybackEngine,
    preloadPlaylistTracks: (NavidromeProvider, List<Playlist>) -> Unit,
    restorePlaybackSession: (String) -> Boolean,
    startAndroidLibrarySync: (Boolean) -> Unit,
    checkAndroidLibraryFreshness: () -> Unit,
    recentRadioStreams: List<RecentRadioStream> = emptyList(),
    recentInternetRadioStations: List<InternetRadioStation> = emptyList(),
    refreshSavedMediaSources: () -> Unit = {},
) {
    scope.launch {
        with(state) {
            status = "Connecting..."
            runCatching {
                val session = openProviderConnectionSession(
                    request = ProviderConnectionLifecycleRequest(
                        connection = connection,
                        prepareConnection = { requestedConnection -> requestedConnection },
                        preparedConnection = { preparedConnection -> preparedConnection },
                        provider = { preparedConnection -> NavidromeProvider(preparedConnection) },
                        mediaSourceConnection = { preparedConnection ->
                            preparedConnection.toProviderMediaSourceConnection()
                        },
                        applyTlsDefaults = { preparedConnection ->
                            playbackEngine.applyTlsSettings(preparedConnection.tlsSettings)
                            AndroidPlaybackTls.applyDefaults(preparedConnection.tlsSettings)
                        },
                    ),
                    providerMediaSourceRepository = providerMediaSourceRepository,
                )
                val nextProvider = session.provider
                validation = session.validation
                homeState = loadBrowseState(
                    provider = nextProvider,
                    providerResponseCacheRepository = providerResponseCacheRepository,
                    libraryRepository = homeLibraryRepository,
                    sourceId = session.sourceId,
                    recentRadioStreams = recentRadioStreams,
                    recentInternetRadioStations = recentInternetRadioStations,
                )
                preloadPlaylistTracks(nextProvider, homeState.playlists)
                provider = nextProvider
                activeSourceId = session.sourceId
                refreshSavedMediaSources()
                activeTlsSettings = session.connection.tlsSettings
                syncAndroidPendingProviderActions(
                    scope = scope,
                    sourceId = session.sourceId,
                    provider = nextProvider,
                    repository = pendingProviderActionRepository,
                    setStatus = { syncStatus ->
                        if (nowPlaying == null && nowPlayingStation == null) {
                            status = syncStatus
                        }
                    },
                )
                restorePlaybackSession(session.sourceId)
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
                status = connectionFailureStatus(error)
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
    connectWithNavidromeConnection: (NavidromeConnection) -> Unit,
) {
    val connectionForm = state.currentConnectionForm()
    val formError = connectionFormError(
        form = connectionForm,
        hasSavedConnectionForLogin = state.savedConnectionForLogin != null,
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
                    savedConnectionForLogin = state.savedConnectionForLogin,
                ),
            ).connection
        }.onSuccess { connection ->
            settingsStore.saveConnection(connectionForm)
            state.savedConnectionForLogin = connection
            connectWithNavidromeConnection(connection)
        }.onFailure { error ->
            state.status = connectionFailureStatus(error)
            state.restoringConnection = false
            state.provider = null
            state.validation = null
        }
    }
}
