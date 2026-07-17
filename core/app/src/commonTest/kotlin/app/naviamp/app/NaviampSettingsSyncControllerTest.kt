package app.naviamp.app

import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncOperationKind
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.SettingsSyncMirrorDocumentSource
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampSettingsSyncControllerTest {
    @Test
    fun exportBuildsTheDocumentFromTheCurrentSharedSnapshot() {
        var runtime = SettingsSyncRuntimeState()
        val expectedInterface = InterfaceSettings(showDesktopTooltips = false)
        val controller = controller(
            state = { runtime },
            saveState = { runtime = it },
            snapshot = { SettingsSyncLocalSnapshot(interfaceSettings = expectedInterface) },
        )

        controller.markLocalChanged()
        val result = controller.exportCurrent()

        assertEquals(SettingsSyncOperationKind.Exported, result.kind)
        assertEquals(100L, result.documentToWrite?.updatedAtEpochMillis)
        assertEquals("test-device", result.documentToWrite?.lastWriterDeviceId)
        assertEquals(expectedInterface, result.documentToWrite?.preferences?.interfaceSettings)
    }

    @Test
    fun importAppliesTheDocumentAndAdvancesSharedRuntimeState() {
        var runtime = SettingsSyncRuntimeState(lastLocalUpdateEpochMillis = 10L)
        var applied: SettingsSyncDocument? = null
        val controller = controller(
            state = { runtime },
            saveState = { runtime = it },
            applyDocument = { applied = it },
        )
        val document = SettingsSyncDocument(updatedAtEpochMillis = 20L)

        val result = controller.applySyncedDocument(document)

        assertEquals(SettingsSyncOperationKind.Imported, result.kind)
        assertEquals(document, applied)
        assertEquals(20L, runtime.lastLocalUpdateEpochMillis)
        assertEquals(20L, runtime.lastAppliedSyncUpdateEpochMillis)
    }

    @Test
    fun reconciliationSelectsTheNewestAvailableDocumentBeforeApplyingIt() {
        var runtime = SettingsSyncRuntimeState(lastLocalUpdateEpochMillis = 10L)
        var applied: SettingsSyncDocument? = null
        val controller = controller(
            state = { runtime },
            saveState = { runtime = it },
            applyDocument = { applied = it },
        )
        val localMirror = SettingsSyncDocument(updatedAtEpochMillis = 15L)
        val provider = SettingsSyncDocument(updatedAtEpochMillis = 20L)

        val reconciliation = controller.reconcileDocuments(
            localMirrorDocument = localMirror,
            providerDocument = provider,
            syncLocationConfigured = true,
        )

        assertEquals(SettingsSyncMirrorDocumentSource.Provider, reconciliation.selection.source)
        assertEquals(SettingsSyncOperationKind.Imported, reconciliation.result.kind)
        assertEquals(provider, applied)
    }

    @Test
    fun sharedStatusesCoverImportSetupAndAutomaticSync() {
        assertEquals("Settings imported.", settingsSyncImportStatus(hasServerProfiles = false))
        assertEquals(
            "Settings imported. Enter the Navidrome password to finish connecting.",
            settingsSyncImportStatus(hasServerProfiles = true),
        )
        assertEquals("Auto-sync enabled.", settingsSyncAutoExportStatus(enabled = true))
        assertEquals("Settings sync disabled.", settingsSyncLocationStatus(configured = false))
    }

    private fun controller(
        state: () -> SettingsSyncRuntimeState,
        saveState: (SettingsSyncRuntimeState) -> Unit,
        snapshot: () -> SettingsSyncLocalSnapshot = { SettingsSyncLocalSnapshot() },
        applyDocument: (SettingsSyncDocument) -> Unit = {},
    ) = NaviampSettingsSyncController(
        deviceId = "test-device",
        state = state,
        saveState = saveState,
        nowEpochMillis = { 100L },
        snapshot = snapshot,
        applyDocument = applyDocument,
    )
}
