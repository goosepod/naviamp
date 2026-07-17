package app.naviamp.app

import app.naviamp.domain.settings.SettingsSyncCoordinator
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncOperationResult
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.buildSettingsSyncDocument

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
}
