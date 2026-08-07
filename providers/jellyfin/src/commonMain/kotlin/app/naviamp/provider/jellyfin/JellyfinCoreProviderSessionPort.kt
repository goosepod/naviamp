package app.naviamp.provider.jellyfin

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.network.NaviampAppVersion
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.ProviderIdJellyfin
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.domain.settings.ConnectionFormSecondaryUrl
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.toConnectionHeaderDefinitions
import app.naviamp.domain.settings.toConnectionSecondaryUrls
import app.naviamp.domain.settings.toSelectedMusicFolderIds
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.ProviderConnectionLifecycleRequest
import app.naviamp.domain.source.ProviderConnectionSession
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.openProviderConnectionSession
import app.naviamp.domain.source.resolvedConnectionDisplayName
import app.naviamp.domain.source.unusedSourceScopeCleanupCutoff
import app.naviamp.domain.source.visibleServerConnections
import app.naviamp.presentation.NaviampCoreConnectedSession
import app.naviamp.presentation.NaviampCoreConnectionInventory
import app.naviamp.presentation.NaviampCoreConnectionRequest
import app.naviamp.presentation.NaviampCoreEditableConnection
import app.naviamp.presentation.NaviampCoreMediaProviderSource
import app.naviamp.presentation.NaviampCoreProviderSessionPort
import app.naviamp.presentation.NaviampCoreSavedConnectionRecord

data class JellyfinConnectionLoginRequest(
    val baseUrl: String,
    val username: String,
    val password: String,
    val displayName: String?,
    val tlsSettings: ConnectionTlsSettings,
    val secondaryUrls: List<app.naviamp.domain.source.ConnectionSecondaryUrl>,
    val customHeaders: List<app.naviamp.domain.source.ConnectionHeaderDefinition>,
    val selectedMusicFolderIds: List<String>,
    val savedSource: SavedMediaSource?,
    val savedSourceId: String?,
)

fun interface JellyfinSessionServiceFactory {
    fun create(tlsSettings: ConnectionTlsSettings): JellyfinSessionService
}

typealias JellyfinProviderConnectionSession = ProviderConnectionSession<JellyfinConnection, JellyfinProvider>

fun interface JellyfinProviderSessionOpener {
    suspend fun open(
        request: JellyfinConnectionLoginRequest,
        clearProviderData: Boolean,
    ): JellyfinProviderConnectionSession
}

class JellyfinCoreProviderSessionPort(
    private val mediaSources: MediaSourceRepository,
    private val sessionOpener: JellyfinProviderSessionOpener,
    private val sessionServices: JellyfinSessionServiceFactory,
    private val deviceId: String,
    initialSource: SavedMediaSource? = null,
) : NaviampCoreProviderSessionPort {
    private val initialSessionSource = initialSource?.takeIf {
        it.providerId == ProviderIdJellyfin && !it.nativeToken.isNullOrBlank()
    }
    private var provider: JellyfinProvider? = initialSessionSource?.toUnvalidatedJellyfinConnection(deviceId)
        ?.let { connection -> JellyfinProvider(connection, sessionServices) }
    private var currentSourceId: String? = initialSessionSource?.id

    override val providerSource = NaviampCoreMediaProviderSource { provider }
    override fun currentProvider(): MediaProvider? = provider
    override fun currentSourceId(): String? = currentSourceId
    override fun initialInventory(): NaviampCoreConnectionInventory = inventory()

    override suspend fun connect(
        request: NaviampCoreConnectionRequest,
        plan: NaviampConnectionAttemptPlan,
    ): NaviampCoreConnectedSession {
        val session = sessionOpener.open(request.toLoginRequest(), plan.clearProviderData)
        provider = session.provider
        currentSourceId = session.sourceId
        return NaviampCoreConnectedSession(
            sourceId = session.sourceId,
            displayName = session.connection.resolvedDisplayName(),
            serverVersion = session.validation.serverVersion,
            inventory = inventory(),
        )
    }

    override suspend fun editableConnection(id: String): NaviampCoreEditableConnection {
        val saved = requireJellyfinSaved(id)
        val libraries = runCatching {
            val connection = sessionServices.create(saved.tlsSettings).restore(saved)
            JellyfinProvider(connection, sessionServices).musicLibraries()
        }
        return NaviampCoreEditableConnection(
            form = saved.toConnectionForm(),
            availableMusicFolders = libraries.getOrDefault(emptyList()).map { library ->
                app.naviamp.domain.settings.ConnectionFormMusicFolder(library.id, library.name)
            },
            musicFoldersLoadFailed = libraries.isFailure,
        )
    }

    override suspend fun deleteConnection(id: String): NaviampCoreConnectionInventory {
        requireSaved(id)
        mediaSources.deleteMediaSource(id)
        if (currentSourceId == id) {
            currentSourceId = null
            provider = null
        }
        return inventory()
    }

    override suspend fun smartPlaylistProvider(password: String?): MediaProvider? = provider

    override suspend fun refreshActiveSession(): Boolean {
        val saved = currentSourceId?.let(mediaSources::mediaSource) ?: return false
        if (saved.providerId != ProviderIdJellyfin) return false
        val restored = sessionServices.create(saved.tlsSettings).restore(saved)
        provider = JellyfinProvider(restored, sessionServices)
        return true
    }

    override suspend fun persistActiveSession() = Unit

    override suspend fun clearActiveSession() {
        currentSourceId = null
        provider = null
    }

    private fun NaviampCoreConnectionRequest.toLoginRequest(): JellyfinConnectionLoginRequest {
        val form = when (this) {
            is NaviampCoreConnectionRequest.Form -> form
            is NaviampCoreConnectionRequest.Saved -> requireJellyfinSaved(id).toConnectionForm()
        }
        require(form.providerId == ProviderIdJellyfin) { "Jellyfin support is not available for this connection." }
        val savedSource = when (this) {
            is NaviampCoreConnectionRequest.Form -> savedConnectionId?.let(::requireSaved)
            is NaviampCoreConnectionRequest.Saved -> requireJellyfinSaved(id)
        }
        return JellyfinConnectionLoginRequest(
            baseUrl = form.serverUrl,
            username = form.username,
            password = form.password,
            displayName = resolvedConnectionDisplayName(form.displayName, form.serverUrl),
            tlsSettings = ConnectionTlsSettings(
                insecureSkipTlsVerification = form.skipTlsVerification,
                customCertificatePath = form.customCertificatePath.trim().ifEmpty { null },
                clientCertificateKeyStorePath = form.clientCertificatePath.trim().ifEmpty { null },
                clientCertificateKeyStorePassword = form.clientCertificatePassword.ifEmpty { null },
            ),
            secondaryUrls = form.secondaryUrls.toConnectionSecondaryUrls(),
            customHeaders = form.customHeaders.toConnectionHeaderDefinitions(),
            selectedMusicFolderIds = form.selectedMusicFolderIds.toSelectedMusicFolderIds(),
            savedSource = savedSource,
            savedSourceId = when (this) {
                is NaviampCoreConnectionRequest.Form -> savedConnectionId
                is NaviampCoreConnectionRequest.Saved -> id
            },
        )
    }

    private fun requireSaved(id: String): SavedMediaSource =
        requireNotNull(mediaSources.mediaSource(id)) { "Saved connection is no longer available." }

    private fun requireJellyfinSaved(id: String): SavedMediaSource = requireSaved(id).also {
        require(it.providerId == ProviderIdJellyfin) { "Saved connection is not a Jellyfin connection." }
    }

    private fun inventory(): NaviampCoreConnectionInventory = NaviampCoreConnectionInventory(
        connections = mediaSources.mediaSources().visibleServerConnections(currentSourceId).map { saved ->
            NaviampCoreSavedConnectionRecord(
                id = saved.id,
                providerId = saved.providerId,
                displayName = saved.displayName,
                serverUrl = saved.baseUrl,
                username = saved.username,
                selectedMusicFolderIds = saved.selectedMusicFolderIds,
            )
        },
        currentSourceId = currentSourceId,
    )
}

fun jellyfinProviderSessionOpener(
    sessionServices: JellyfinSessionServiceFactory,
    cacheMaintenanceRepository: CacheMaintenanceRepository<*>,
    providerMediaSourceRepository: ProviderMediaSourceRepository,
    nowEpochMillis: () -> Long,
): JellyfinProviderSessionOpener = JellyfinProviderSessionOpener { login, clearProviderData ->
    openProviderConnectionSession(
        request = ProviderConnectionLifecycleRequest(
            connection = login,
            prepareConnection = { request ->
                val service = sessionServices.create(request.tlsSettings)
                if (request.password.isBlank() && request.savedSource != null) {
                    service.restore(request.savedSource)
                } else {
                    service.authenticate(
                        JellyfinAuthenticationRequest(
                            baseUrl = request.baseUrl,
                            username = request.username,
                            password = request.password,
                            displayName = request.displayName,
                            tlsSettings = request.tlsSettings,
                            secondaryUrls = request.secondaryUrls,
                            customHeaders = request.customHeaders,
                            selectedMusicFolderIds = request.selectedMusicFolderIds,
                        ),
                    )
                }
            },
            preparedConnection = { it },
            provider = { connection -> JellyfinProvider(connection, sessionServices) },
            mediaSourceConnection = JellyfinConnection::toProviderMediaSourceConnection,
            preferredSourceId = login.savedSourceId,
            clearProviderData = clearProviderData,
            pruneUnusedSourceScopesBeforeEpochMillis = unusedSourceScopeCleanupCutoff(nowEpochMillis()),
        ),
        cacheMaintenanceRepository = cacheMaintenanceRepository,
        providerMediaSourceRepository = providerMediaSourceRepository,
    )
}

fun jellyfinClientIdentity(deviceId: String, deviceName: String): JellyfinClientIdentity =
    JellyfinClientIdentity(
        deviceId = deviceId,
        deviceName = deviceName,
        clientVersion = NaviampAppVersion,
    )

private fun SavedMediaSource.toUnvalidatedJellyfinConnection(deviceId: String): JellyfinConnection =
    JellyfinConnection(
        baseUrl = baseUrl,
        username = username,
        accessToken = nativeToken.orEmpty(),
        userId = PendingJellyfinUserId,
        deviceId = deviceId,
        displayName = displayName,
        tlsSettings = tlsSettings,
        secondaryUrls = secondaryUrls,
        customHeaders = customHeaders,
        selectedMusicFolderIds = selectedMusicFolderIds,
    )

private fun SavedMediaSource.toConnectionForm(): ConnectionFormState = ConnectionFormState(
    providerId = providerId,
    displayName = displayName.takeUnless { it == baseUrl }.orEmpty(),
    serverUrl = baseUrl,
    username = username,
    password = "",
    skipTlsVerification = tlsSettings.insecureSkipTlsVerification,
    customCertificatePath = tlsSettings.customCertificatePath.orEmpty(),
    clientCertificatePath = tlsSettings.clientCertificateKeyStorePath.orEmpty(),
    clientCertificatePassword = tlsSettings.clientCertificateKeyStorePassword.orEmpty(),
    secondaryUrls = secondaryUrls.map { ConnectionFormSecondaryUrl(it.url, it.label.orEmpty()) },
    customHeaders = customHeaders.map { ConnectionFormHeader(it.name, it.value.orEmpty(), it.valueIsSecret) },
    selectedMusicFolderIds = selectedMusicFolderIds,
)
