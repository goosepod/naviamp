package app.naviamp.provider.navidrome

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderIdentityMigrationRepository
import app.naviamp.domain.cache.ProviderIdentityProbeState
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.ProviderIdNavidrome
import app.naviamp.domain.provider.ProviderIdSubsonic
import app.naviamp.domain.provider.ProviderIdBandcamp
import app.naviamp.domain.provider.ProviderProtocolFamily
import app.naviamp.domain.provider.providerDescriptor
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.domain.settings.ConnectionFormSecondaryUrl
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.connectionFormMusicFolders
import app.naviamp.domain.settings.toConnectionHeaderDefinitions
import app.naviamp.domain.settings.toConnectionSecondaryUrls
import app.naviamp.domain.settings.toSelectedMusicFolderIds
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
import app.naviamp.presentation.NaviampCoreProviderSessionRoute
import app.naviamp.presentation.NaviampCoreProviderSessionRouter
import app.naviamp.presentation.NaviampCoreSavedConnectionRecord

typealias NavidromeProviderConnectionSession = ProviderConnectionSession<NavidromeConnection, NavidromeProvider>

fun interface NavidromeProviderSessionOpener {
    suspend fun open(
        request: NavidromeConnectionLoginRequest,
        clearProviderData: Boolean,
    ): NavidromeProviderConnectionSession
}

/** Provider-common implementation of Core's complete provider-session boundary. */
class NavidromeCoreProviderSessionPort(
    private val mediaSources: MediaSourceRepository,
    private val sessionOpener: NavidromeProviderSessionOpener,
    initialSource: SavedMediaSource? = null,
    private val applyTlsDefaults: (NavidromeConnection) -> Unit = {},
    private val musicFolders: suspend (NavidromeConnection) -> List<NavidromeMusicFolder> = { connection ->
        applyTlsDefaults(connection)
        NavidromeProvider(connection).musicFolders()
    },
    private val validateProvider: suspend (NavidromeProvider) -> ConnectionValidation = { it.validateConnection() },
    private val canonicalIdMigrationSupport: (NavidromeProvider) -> NavidromeCanonicalIdMigrationSupport =
        { provider -> provider.canonicalIdMigrationSupport() },
) : NaviampCoreProviderSessionPort {
    private val initialSessionSource = initialSource?.takeIf(SavedMediaSource::supportsSubsonicSession)
    private var provider: NavidromeProvider? = initialSessionSource?.toNavidromeConnection()?.let { connection ->
        applyTlsDefaults(connection)
        NavidromeProvider(connection)
    }
    private var currentSourceId: String? = initialSessionSource?.id
    private val nativeSession = NavidromeNativeSessionController(
        currentProvider = { provider },
        savedConnection = { currentSourceId?.let(mediaSources::mediaSource)?.toNavidromeConnection() },
        replaceProvider = { provider = it },
        repository = mediaSources as? ProviderMediaSourceRepository,
        prepareConnection = applyTlsDefaults,
    )

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
        migrateProviderIdentities(session.provider, session.sourceId, session.validation.serverVersion)
        return NaviampCoreConnectedSession(
            sourceId = session.sourceId,
            displayName = session.connection.resolvedDisplayName(),
            serverVersion = session.validation.serverVersion,
            inventory = inventory(),
        )
    }

    override suspend fun editableConnection(id: String): NaviampCoreEditableConnection {
        val saved = requireSubsonicSaved(id)
        val connection = saved.toNavidromeConnection()
        val folders = runCatching { musicFolders(connection) }
        return NaviampCoreEditableConnection(
            form = saved.toConnectionForm(),
            availableMusicFolders = connectionFormMusicFolders(
                folders.getOrDefault(emptyList()).map { it.id to it.name },
            ),
            musicFoldersLoadFailed = folders.isFailure,
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

    override suspend fun smartPlaylistProvider(password: String?): MediaProvider? =
        if (provider?.id?.value?.let(::subsonicProviderProfile)?.nativeAuthentication == true) {
            nativeSession.provider(password)
        } else {
            provider
        }

    override suspend fun refreshActiveSession(): Boolean {
        val activeProvider = provider
        val sourceId = currentSourceId
        if (activeProvider != null && sourceId != null) {
            runCatching { migrateProviderIdentities(activeProvider, sourceId) }
        }
        return if (activeProvider?.id?.value?.let(::subsonicProviderProfile)?.nativeAuthentication == true) {
            nativeSession.refresh()
        } else {
            false
        }
    }

    override suspend fun persistActiveSession() = nativeSession.persist()

    override suspend fun clearActiveSession() {
        currentSourceId = null
        provider = null
    }

    private fun NaviampCoreConnectionRequest.toLoginRequest(): NavidromeConnectionLoginRequest {
        val form = when (this) {
            is NaviampCoreConnectionRequest.Form -> form
            is NaviampCoreConnectionRequest.Saved -> requireSubsonicSaved(id).toConnectionForm()
        }
        val provider = providerDescriptor(form.providerId)
        require(provider.selectable && provider.protocolFamily == ProviderProtocolFamily.Subsonic) {
            "${provider.displayName} support is not available yet."
        }
        val saved = when (this) {
            is NaviampCoreConnectionRequest.Form -> savedConnectionId?.let(::requireSaved)
            is NaviampCoreConnectionRequest.Saved -> requireSubsonicSaved(id)
        }?.toNavidromeConnection()
        val savedSourceId = when (this) {
            is NaviampCoreConnectionRequest.Form -> savedConnectionId
            is NaviampCoreConnectionRequest.Saved -> id
        }
        val profile = subsonicProviderProfile(form.providerId)
        return NavidromeConnectionLoginRequest(
            providerId = form.providerId,
            baseUrl = form.serverUrl,
            secondaryUrls = form.secondaryUrls.toConnectionSecondaryUrls(),
            username = form.username,
            password = form.password,
            displayName = resolvedConnectionDisplayName(form.displayName, form.serverUrl),
            tlsSettings = navidromeTlsSettingsFromForm(
                insecureSkipTlsVerification = form.skipTlsVerification,
                customCertificatePath = form.customCertificatePath,
                clientCertificateKeyStorePath = form.clientCertificatePath,
                clientCertificateKeyStorePassword = form.clientCertificatePassword,
            ),
            customHeaders = form.customHeaders.toConnectionHeaderDefinitions(),
            selectedMusicFolderIds = form.selectedMusicFolderIds.toSelectedMusicFolderIds(),
            savedSourceId = savedSourceId,
            savedConnectionForLogin = saved,
            nativeAuthEnabled = profile.nativeAuthentication,
            nativeAuthRequired = profile.nativeAuthentication,
        )
    }

    private fun requireSaved(id: String): SavedMediaSource =
        requireNotNull(mediaSources.mediaSource(id)) { "Saved connection is no longer available." }

    private fun requireSubsonicSaved(id: String): SavedMediaSource = requireSaved(id).also { saved ->
        val provider = providerDescriptor(saved.providerId)
        require(provider.selectable && provider.protocolFamily == ProviderProtocolFamily.Subsonic) {
            "${provider.displayName} support is not available yet."
        }
    }

    private suspend fun migrateProviderIdentities(
        activeProvider: NavidromeProvider,
        sourceId: String,
        knownServerVersion: String? = null,
    ) {
        if (!subsonicProviderProfile(activeProvider.id.value).canonicalIdMigration) return
        val migrations = mediaSources as? ProviderIdentityMigrationRepository ?: return
        if ((migrations.providerIdentityVersion(sourceId) ?: 0L) >= NavidromeCanonicalIdentityVersion) return
        val serverVersion = knownServerVersion ?: validateProvider(activeProvider).serverVersion
        val normalizedServerVersion = serverVersion?.trim().orEmpty()
        val previousProbe = migrations.providerIdentityProbeState(sourceId)
        if (
            normalizedServerVersion.isNotEmpty() &&
            previousProbe?.targetIdentityVersion == NavidromeCanonicalIdentityVersion &&
            previousProbe.serverVersion == normalizedServerVersion
        ) return
        when (canonicalIdMigrationSupport(activeProvider)) {
            NavidromeCanonicalIdMigrationSupport.Confirmed -> migrations.migrateProviderIdentities(
                sourceId = sourceId,
                providerId = activeProvider.id.value,
                targetVersion = NavidromeCanonicalIdentityVersion,
                transform = NavidromeCanonicalId::migrate,
            )
            NavidromeCanonicalIdMigrationSupport.Unsupported,
            -> if (normalizedServerVersion.isNotEmpty()) {
                migrations.recordProviderIdentityProbeState(
                    sourceId,
                    ProviderIdentityProbeState(
                        targetIdentityVersion = NavidromeCanonicalIdentityVersion,
                        serverVersion = normalizedServerVersion,
                    ),
                )
            }
            NavidromeCanonicalIdMigrationSupport.Inconclusive -> Unit
        }
    }

    private fun inventory(): NaviampCoreConnectionInventory {
        val connections = mediaSources.mediaSources().visibleServerConnections(currentSourceId).map { saved ->
            NaviampCoreSavedConnectionRecord(
                id = saved.id,
                providerId = saved.providerId,
                displayName = saved.displayName,
                serverUrl = saved.baseUrl,
                username = saved.username,
                selectedMusicFolderIds = saved.selectedMusicFolderIds,
            )
        }
        return NaviampCoreConnectionInventory(connections, currentSourceId)
    }
}

fun subsonicFamilyProviderSessionRouter(
    sessionPort: NavidromeCoreProviderSessionPort,
    additionalRoutes: List<NaviampCoreProviderSessionRoute> = emptyList(),
): NaviampCoreProviderSessionPort = NaviampCoreProviderSessionRouter(
    routes = listOf(
        NaviampCoreProviderSessionRoute(
            providerIds = setOf(ProviderIdNavidrome, ProviderIdSubsonic, ProviderIdBandcamp),
            sessionPort = sessionPort,
        ),
    ) + additionalRoutes,
)

private fun SavedMediaSource.supportsSubsonicSession(): Boolean = providerDescriptor(providerId).let { provider ->
    provider.selectable && provider.protocolFamily == ProviderProtocolFamily.Subsonic
}

fun navidromeProviderSessionOpener(
    cacheMaintenanceRepository: CacheMaintenanceRepository<*>,
    providerMediaSourceRepository: ProviderMediaSourceRepository,
    applyTlsDefaults: (NavidromeConnection) -> Unit = {},
    nowEpochMillis: () -> Long,
): NavidromeProviderSessionOpener = NavidromeProviderSessionOpener { login, clearProviderData ->
    openProviderConnectionSession(
        request = ProviderConnectionLifecycleRequest(
            connection = login,
            prepareConnection = { prepareNavidromeConnection(it) },
            preparedConnection = { it.connection },
            provider = ::NavidromeProvider,
            mediaSourceConnection = NavidromeConnection::toProviderMediaSourceConnection,
            applyTlsDefaults = { applyTlsDefaults(it) },
            smartPlaylistAuthWarning = { it.nativeAuthErrorMessage },
            preferredSourceId = login.savedSourceId,
            clearProviderData = clearProviderData,
            pruneUnusedSourceScopesBeforeEpochMillis = unusedSourceScopeCleanupCutoff(nowEpochMillis()),
        ),
        cacheMaintenanceRepository = cacheMaintenanceRepository,
        providerMediaSourceRepository = providerMediaSourceRepository,
    )
}

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
