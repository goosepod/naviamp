package app.naviamp.app

import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncMirrorDocumentSource
import app.naviamp.domain.settings.SettingsSyncOperationKind

/**
 * Shared settings-sync workflow across a durable local mirror and an optional platform document
 * provider. Hosts retain document selection, URI/path permissions, and concrete store creation.
 */
class NaviampSettingsSyncHostController(
    private val controller: NaviampSettingsSyncController,
    private val mirrorStore: NaviampSettingsSyncDocumentStore,
    private val providerStore: () -> NaviampSettingsSyncDocumentStore?,
    private val autoExportEnabled: () -> Boolean,
    private val saveAutoExportEnabled: (Boolean) -> Unit,
    private val onMirrorWritten: (SettingsSyncDocument) -> Unit = {},
    private val onProviderPullSucceeded: () -> Unit = {},
    private val onProviderPushSucceeded: () -> Unit = {},
    private val onProviderSyncFailed: (String) -> Unit = {},
    private val setStatus: (String) -> Unit,
    private val publishStatusEffect: (String, NaviampApplicationStatusLevel) -> Unit,
) {
    fun markChangedAndAutoExport() {
        controller.markLocalChanged()
        controller.exportCurrent().documentToWrite?.let { document ->
            if (autoExportEnabled()) {
                writeMirrorAndTryProvider(document) { "Settings auto-synced to provider." }
            } else {
                writeMirror(document)
                    .onSuccess { publishStatus("Settings saved locally. Sync now when ready.") }
            }
        }
    }

    fun syncNow(statusPrefix: String = "Settings sync") {
        val localMirrorDocument = readMirror() ?: if (lastMirrorReadFailed) return else null
        val provider = providerStore()
        var providerDocument: SettingsSyncDocument? = null
        var providerFileMissing = false
        var providerReadError: String? = null
        if (provider != null) {
            runCatching { provider.read() }
                .onSuccess { document ->
                    providerDocument = document
                    providerFileMissing = document == null
                    if (document != null) onProviderPullSucceeded()
                }
                .onFailure { error ->
                    providerReadError = error.message ?: "Could not read settings sync provider."
                    onProviderSyncFailed(providerReadError.orEmpty())
                }
        }

        val reconciliation = controller.reconcileDocuments(
            localMirrorDocument = localMirrorDocument,
            providerDocument = providerDocument,
            syncLocationConfigured = true,
        )
        val selection = reconciliation.selection
        val result = reconciliation.result
        when (result.kind) {
            SettingsSyncOperationKind.Imported -> {
                selection.document?.let { document ->
                    if (writeMirror(document).isFailure) return
                    if (selection.source == SettingsSyncMirrorDocumentSource.Provider) {
                        onProviderPullSucceeded()
                    }
                }
                publishStatus(settingsSyncImportStatus(result.hasServerProfiles))
            }
            SettingsSyncOperationKind.Exported -> {
                result.documentToWrite?.let { document ->
                    writeMirrorAndTryProvider(document) { "$statusPrefix exported local settings." }
                }
            }
            SettingsSyncOperationKind.NoOp -> {
                if (providerFileMissing && localMirrorDocument != null && provider != null) {
                    writeProvider(provider, localMirrorDocument) { "$statusPrefix created provider file." }
                } else if (providerReadError != null && localMirrorDocument != null) {
                    publishStatus(
                        "Local settings mirror is ready. Provider sync pending: $providerReadError",
                        NaviampApplicationStatusLevel.Warning,
                    )
                } else {
                    publishStatus("$statusPrefix is up to date.")
                }
            }
            SettingsSyncOperationKind.UnsupportedSyncFile,
            SettingsSyncOperationKind.NeedsSetupChoice,
            SettingsSyncOperationKind.MissingSyncLocation,
            -> publishStatus(settingsSyncReconciliationStatus(result))
        }
        if (providerReadError != null && selection.document == null) {
            publishStatus(providerReadError.orEmpty(), NaviampApplicationStatusLevel.Warning)
        }
    }

    fun exportToProvider() {
        controller.markLocalChanged()
        controller.exportCurrent().documentToWrite?.let { document ->
            writeMirrorAndTryProvider(document) { "Settings exported to sync provider." }
        }
    }

    fun updateAutoExport(enabled: Boolean) {
        val normalized = enabled && providerStore() != null
        saveAutoExportEnabled(normalized)
        setStatus(settingsSyncAutoExportStatus(normalized))
        if (normalized) {
            controller.autoExport()?.documentToWrite?.let { document ->
                writeMirrorAndTryProvider(document) { "Settings auto-synced to provider." }
            }
        }
    }

    private var lastMirrorReadFailed: Boolean = false

    private fun readMirror(): SettingsSyncDocument? {
        lastMirrorReadFailed = false
        return runCatching { mirrorStore.read() }
            .onFailure { error ->
                lastMirrorReadFailed = true
                publishStatus(
                    error.message ?: "Could not read local settings mirror.",
                    NaviampApplicationStatusLevel.Error,
                )
            }
            .getOrNull()
    }

    private fun writeMirror(document: SettingsSyncDocument): Result<Unit> =
        runCatching {
            mirrorStore.write(document)
            controller.documentWritten(document)
            onMirrorWritten(document)
        }.onFailure { error ->
            publishStatus(
                error.message ?: "Could not save local settings mirror.",
                NaviampApplicationStatusLevel.Error,
            )
        }

    private fun writeMirrorAndTryProvider(
        document: SettingsSyncDocument,
        statusMessage: () -> String,
    ) {
        if (writeMirror(document).isFailure) return
        val provider = providerStore()
        if (provider == null) {
            setStatus("Settings saved locally. Choose a sync folder to sync provider.")
            return
        }
        writeProvider(provider, document, statusMessage)
    }

    private fun writeProvider(
        provider: NaviampSettingsSyncDocumentStore,
        document: SettingsSyncDocument,
        statusMessage: () -> String,
    ) {
        runCatching { provider.write(document) }
            .onSuccess {
                onProviderPushSucceeded()
                publishStatus(statusMessage())
            }
            .onFailure { error ->
                val message = error.message ?: "Could not sync settings with provider."
                onProviderSyncFailed(message)
                publishStatus(
                    "Settings saved locally. Provider sync pending: $message",
                    NaviampApplicationStatusLevel.Warning,
                )
            }
    }

    private fun publishStatus(
        message: String,
        level: NaviampApplicationStatusLevel = NaviampApplicationStatusLevel.Information,
    ) = publishStatusEffect.invoke(message, level)
}
