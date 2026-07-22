package app.naviamp.app

import app.naviamp.domain.settings.SettingsSyncCoordinator
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncMirrorDocumentSelection
import app.naviamp.domain.settings.SettingsSyncOperationResult
import app.naviamp.domain.settings.SettingsSyncOperationKind
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.buildSettingsSyncDocument
import app.naviamp.domain.settings.selectSettingsSyncMirrorDocument

data class NaviampSettingsSyncReconciliation(
    val selection: SettingsSyncMirrorDocumentSelection,
    val result: SettingsSyncOperationResult,
)

/** Shared application owner for settings snapshots, timestamps, import, export, and reconciliation. */
class NaviampSettingsSyncController(
    deviceId: String,
    state: () -> SettingsSyncRuntimeState,
    saveState: (SettingsSyncRuntimeState) -> Unit,
    nowEpochMillis: () -> Long,
    snapshot: () -> SettingsSyncLocalSnapshot,
    applyDocument: (SettingsSyncDocument) -> Unit,
) {
    private val coordinator = SettingsSyncCoordinator(
        deviceId = deviceId,
        state = state,
        saveState = saveState,
        nowEpochMillis = nowEpochMillis,
        buildLocalDocument = { updatedAtEpochMillis ->
            buildSettingsSyncDocument(
                snapshot = snapshot(),
                nowEpochMillis = updatedAtEpochMillis,
                deviceId = deviceId,
            )
        },
        applyDocument = applyDocument,
    )

    fun markLocalChanged() = coordinator.markLocalChanged()

    fun applySyncedDocument(document: SettingsSyncDocument): SettingsSyncOperationResult =
        coordinator.applySyncedDocument(document)

    fun exportCurrent(markChanged: Boolean = false): SettingsSyncOperationResult =
        coordinator.exportCurrent(markChanged)

    fun documentWritten(document: SettingsSyncDocument) = coordinator.documentWritten(document)

    fun autoExport(): SettingsSyncOperationResult? = coordinator.autoExport()

    fun reconcileStartup(
        syncedDocument: SettingsSyncDocument?,
        syncLocationConfigured: Boolean,
    ): SettingsSyncOperationResult = coordinator.reconcileStartup(syncedDocument, syncLocationConfigured)

    fun reconcileDocuments(
        localMirrorDocument: SettingsSyncDocument?,
        providerDocument: SettingsSyncDocument?,
        syncLocationConfigured: Boolean,
    ): NaviampSettingsSyncReconciliation {
        val selection = selectSettingsSyncMirrorDocument(localMirrorDocument, providerDocument)
        return NaviampSettingsSyncReconciliation(
            selection = selection,
            result = reconcileStartup(selection.document, syncLocationConfigured),
        )
    }
}

fun settingsSyncImportStatus(hasServerProfiles: Boolean): String =
    if (hasServerProfiles) {
        "Settings imported. Enter the Navidrome password to finish connecting."
    } else {
        "Settings imported."
    }

fun settingsSyncReconciliationStatus(result: SettingsSyncOperationResult): String =
    when (result.kind) {
        SettingsSyncOperationKind.Imported -> settingsSyncImportStatus(result.hasServerProfiles)
        SettingsSyncOperationKind.Exported -> "Settings sync exported local settings."
        SettingsSyncOperationKind.NoOp -> "Settings sync is up to date."
        SettingsSyncOperationKind.UnsupportedSyncFile ->
            "Settings sync file was created by a newer Naviamp version."
        SettingsSyncOperationKind.NeedsSetupChoice -> "Choose how to set up Naviamp."
        SettingsSyncOperationKind.MissingSyncLocation -> "Choose a settings sync folder first."
    }

fun settingsSyncAutoExportStatus(enabled: Boolean): String =
    if (enabled) "Auto-sync enabled." else "Auto-sync disabled."

fun settingsSyncLocationStatus(configured: Boolean): String =
    if (configured) "Settings sync folder selected." else "Settings sync disabled."

fun settingsSyncMissingLocationStatus(): String = "Choose a settings sync folder first."

fun settingsSyncExportStatus(fileName: String, automatic: Boolean): String =
    if (automatic) "Settings auto-exported to $fileName." else "Settings exported to $fileName."

fun settingsSyncAutoExportEnabled(requested: Boolean, locationConfigured: Boolean): Boolean =
    requested && locationConfigured
