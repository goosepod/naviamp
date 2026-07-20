package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.connectionFormMusicFolders
import app.naviamp.domain.settings.defaultSelectedMusicFolderIds
import app.naviamp.domain.settings.toConnectionHeaderDefinitions
import app.naviamp.domain.settings.toConnectionSecondaryUrls
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the remaining host-lifetime effects that coordinate Desktop controllers and adapters.
 * Product rendering and controller construction stay outside this boundary.
 */
@Composable
internal fun DesktopHostEffects(
    applicationStatusSequence: Long?,
    applicationStatusMessage: String?,
    setConnectionStatus: (String?) -> Unit,
    settingsSyncHost: DesktopSettingsSyncHost,
    sonicHomeDiscoveryController: DesktopSonicHomeDiscoveryController,
    sonicDiscoveryProvider: NavidromeProvider?,
    sonicDiscoveryEnabled: Boolean,
    sonicDiscoverySourceId: String?,
    sonicDiscoveryTrackId: String?,
    sonicDiscoveryQueueSize: Int,
    connectionForm: DesktopConnectionFormStateHolder,
    connectedProvider: NavidromeProvider?,
    connectedSourceId: String?,
    setAvailableMusicFolders: (List<ConnectionFormMusicFolder>) -> Unit,
    setMusicFoldersStatus: (String?) -> Unit,
    downloadsController: DesktopDownloadsController,
    playlistSignatures: List<String>,
    nowPlayingFavoriteTimestamp: String?,
) {
    LaunchedEffect(applicationStatusSequence) {
        applicationStatusMessage?.let(setConnectionStatus)
    }

    LaunchedEffect(settingsSyncHost) {
        settingsSyncHost.reconcileAtStartup()
    }

    LaunchedEffect(
        sonicDiscoveryEnabled,
        sonicDiscoveryProvider,
        sonicDiscoverySourceId,
        sonicDiscoveryTrackId,
        sonicDiscoveryQueueSize,
    ) {
        sonicHomeDiscoveryController.loadIfNeeded(sonicDiscoveryEnabled)
    }

    LaunchedEffect(
        connectionForm.isOpen,
        connectionForm.serverUrl,
        connectionForm.username,
        connectionForm.password,
        connectionForm.insecureSkipTlsVerification,
        connectionForm.customCertificatePath,
        connectionForm.clientCertificateKeyStorePath,
        connectionForm.clientCertificateKeyStorePassword,
        connectionForm.secondaryUrls,
        connectionForm.customHeaders,
        connectionForm.savedConnectionForLogin,
    ) {
        if (!connectionForm.isOpen) {
            setMusicFoldersStatus(null)
            return@LaunchedEffect
        }
        val baseUrl = connectionForm.serverUrl.trim()
        val username = connectionForm.username.trim()
        val savedLogin = connectionForm.savedConnectionForLogin
        val password = connectionForm.password
        if (baseUrl.isEmpty() || username.isEmpty() || (savedLogin == null && password.isBlank())) {
            setAvailableMusicFolders(emptyList())
            setMusicFoldersStatus("Enter connection details to load libraries.")
            return@LaunchedEffect
        }

        setMusicFoldersStatus("Loading libraries...")
        val tlsSettings = ConnectionTlsSettings(
            insecureSkipTlsVerification = connectionForm.insecureSkipTlsVerification,
            customCertificatePath = connectionForm.customCertificatePath.ifBlank { null },
            clientCertificateKeyStorePath = connectionForm.clientCertificateKeyStorePath.ifBlank { null },
            clientCertificateKeyStorePassword = connectionForm.clientCertificateKeyStorePassword.ifBlank { null },
        )
        val lookupConnection = if (savedLogin != null && password.isBlank()) {
            savedLogin.copy(
                baseUrl = baseUrl,
                username = username,
                tlsSettings = tlsSettings,
                secondaryUrls = connectionForm.secondaryUrls.toConnectionSecondaryUrls(),
                customHeaders = connectionForm.customHeaders.toConnectionHeaderDefinitions(),
            )
        } else {
            NavidromeConnection.fromPassword(
                baseUrl = baseUrl,
                username = username,
                password = password,
                displayName = connectionForm.connectionName.ifBlank { null },
                tlsSettings = tlsSettings,
                secondaryUrls = connectionForm.secondaryUrls.toConnectionSecondaryUrls(),
                customHeaders = connectionForm.customHeaders.toConnectionHeaderDefinitions(),
            )
        }
        runCatching {
            withContext(Dispatchers.IO) {
                NavidromeProvider(lookupConnection).musicFolders()
            }
        }.fold(
            onSuccess = { folders ->
                val choices = connectionFormMusicFolders(folders.map { folder -> folder.id to folder.name })
                setAvailableMusicFolders(choices)
                setMusicFoldersStatus(if (choices.isEmpty()) "No libraries returned by the server." else null)
                connectionForm.selectedMusicFolderIds = defaultSelectedMusicFolderIds(
                    selectedIds = connectionForm.selectedMusicFolderIds,
                    availableFolders = choices,
                )
            },
            onFailure = { error ->
                setAvailableMusicFolders(emptyList())
                setMusicFoldersStatus("Could not load libraries: ${error.message ?: error::class.simpleName}")
            },
        )
    }

    LaunchedEffect(connectedProvider, connectedSourceId, connectionForm.isOpen) {
        val provider = connectedProvider
        if (connectionForm.isOpen || provider == null) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) { provider.musicFolders() }
        }.onSuccess { folders ->
            setAvailableMusicFolders(connectionFormMusicFolders(folders.map { folder -> folder.id to folder.name }))
        }
    }

    LaunchedEffect(connectedSourceId) {
        downloadsController.reloadKeepDownloadedPolicies()
    }

    LaunchedEffect(playlistSignatures, nowPlayingFavoriteTimestamp) {
        if (downloadsController.keepDownloadedPolicies.isNotEmpty()) {
            downloadsController.reconcileKeepDownloadedCollections()
        }
    }
}
