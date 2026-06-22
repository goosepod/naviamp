package app.naviamp.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsSyncCoordinatorTest {
    @Test
    fun marksLocalChangesAndBuildsExportDocument() {
        var runtime = SettingsSyncRuntimeState()
        val coordinator = coordinator(
            state = { runtime },
            saveState = { runtime = it },
            now = { 100L },
        )

        coordinator.markLocalChanged()
        val result = coordinator.exportCurrent()

        assertEquals(100L, runtime.lastLocalUpdateEpochMillis)
        assertEquals(SettingsSyncOperationKind.Exported, result.kind)
        assertEquals(100L, result.documentToWrite?.updatedAtEpochMillis)
    }

    @Test
    fun appliesImportedDocumentAndMarksItApplied() {
        var runtime = SettingsSyncRuntimeState(lastLocalUpdateEpochMillis = 10L)
        var applied: SettingsSyncDocument? = null
        val coordinator = coordinator(
            state = { runtime },
            saveState = { runtime = it },
            apply = { applied = it },
        )
        val document = document(
            updatedAt = 20L,
            serverProfiles = listOf(profile("source")),
        )

        val result = coordinator.applySyncedDocument(document)

        assertEquals(document, applied)
        assertEquals(20L, runtime.lastLocalUpdateEpochMillis)
        assertEquals(20L, runtime.lastAppliedSyncUpdateEpochMillis)
        assertTrue(result.hasServerProfiles)
    }

    @Test
    fun startupReconcileExportsNewerLocalDocument() {
        var runtime = SettingsSyncRuntimeState(lastLocalUpdateEpochMillis = 30L)
        val coordinator = coordinator(
            state = { runtime },
            saveState = { runtime = it },
            now = { 40L },
        )

        val result = coordinator.reconcileStartup(
            syncedDocument = document(updatedAt = 20L),
            syncLocationConfigured = true,
        )

        assertEquals(SettingsSyncOperationKind.Exported, result.kind)
        assertEquals(40L, result.documentToWrite?.updatedAtEpochMillis)
    }

    @Test
    fun startupReconcileImportsNewerSyncedDocument() {
        var runtime = SettingsSyncRuntimeState(lastLocalUpdateEpochMillis = 10L)
        var applied: SettingsSyncDocument? = null
        val coordinator = coordinator(
            state = { runtime },
            saveState = { runtime = it },
            apply = { applied = it },
        )
        val synced = document(updatedAt = 20L)

        val result = coordinator.reconcileStartup(
            syncedDocument = synced,
            syncLocationConfigured = true,
        )

        assertEquals(SettingsSyncOperationKind.Imported, result.kind)
        assertEquals(synced, applied)
        assertEquals(20L, runtime.lastAppliedSyncUpdateEpochMillis)
    }

    @Test
    fun autoExportOnlyBuildsDocumentWhenEnabled() {
        var runtime = SettingsSyncRuntimeState(autoExportEnabled = false, lastLocalUpdateEpochMillis = 10L)
        val coordinator = coordinator(
            state = { runtime },
            saveState = { runtime = it },
        )

        assertNull(coordinator.autoExport())

        runtime = runtime.copy(autoExportEnabled = true)
        assertNotNull(coordinator.autoExport()?.documentToWrite)
    }

    private fun coordinator(
        state: () -> SettingsSyncRuntimeState,
        saveState: (SettingsSyncRuntimeState) -> Unit,
        now: () -> Long = { 100L },
        apply: (SettingsSyncDocument) -> Unit = {},
    ): SettingsSyncCoordinator =
        SettingsSyncCoordinator(
            deviceId = "test",
            state = state,
            saveState = saveState,
            nowEpochMillis = now,
            buildLocalDocument = { updatedAt -> document(updatedAt) },
            applyDocument = apply,
        )

    private fun document(
        updatedAt: Long,
        serverProfiles: List<SettingsSyncServerProfile> = emptyList(),
    ): SettingsSyncDocument =
        SettingsSyncDocument(
            updatedAtEpochMillis = updatedAt,
            serverProfiles = serverProfiles,
        )

    private fun profile(id: String): SettingsSyncServerProfile =
        SettingsSyncServerProfile(
            id = id,
            displayName = "Goosepod",
            username = "ursasmar",
            primaryUrl = "https://navidrome.example",
        )
}
