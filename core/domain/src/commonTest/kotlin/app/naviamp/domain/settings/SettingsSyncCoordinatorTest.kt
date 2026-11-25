package app.naviamp.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsSyncCoordinatorTest {
    @Test fun documentAndSyncStampCommitBeforeLiveEffectsAndFailedBatchDoesNotRunThem() {
        var runtime = SettingsSyncRuntimeState()
        val events = mutableListOf<String>()
        var fail = false
        val coordinator = SettingsSyncCoordinator(
            deviceId = "test", state = { runtime }, saveState = { events += "stamp"; runtime = it }, nowEpochMillis = { 1L },
            buildLocalDocument = { document(it) }, applyDocument = { events += "document" },
            persistenceTransaction = { body ->
                events += "begin"
                if (fail) error("storage rejected")
                body(); events += "commit"
            }, afterDocumentApplied = { events += "effects" },
        )
        coordinator.applySyncedDocument(document(20))
        assertEquals(listOf("begin", "document", "stamp", "commit", "effects"), events)
        events.clear(); fail = true
        kotlin.test.assertFailsWith<IllegalStateException> { coordinator.applySyncedDocument(document(30)) }
        assertEquals(listOf("begin"), events)
        assertEquals(20L, runtime.lastAppliedSyncUpdateEpochMillis)
    }
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
        assertTrue(result.needsCredentials)
    }

    @Test
    fun importOfAProfileThisDeviceAlreadyHoldsCredentialsForNeedsNoPassword() {
        var runtime = SettingsSyncRuntimeState()
        val coordinator = SettingsSyncCoordinator(
            deviceId = "test",
            state = { runtime },
            saveState = { runtime = it },
            nowEpochMillis = { 100L },
            buildLocalDocument = { updatedAt -> document(updatedAt) },
            applyDocument = {},
            importNeedsCredentials = { false },
        )

        val result = coordinator.applySyncedDocument(document(updatedAt = 20L, serverProfiles = listOf(profile("source"))))

        assertTrue(result.hasServerProfiles)
        assertFalse(result.needsCredentials)
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
