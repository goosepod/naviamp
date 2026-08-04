package app.naviamp.app

import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampSettingsSyncHostControllerTest {
    @Test
    fun localChangeWritesMirrorWithoutProviderWhenAutoExportIsDisabled() {
        val fixture = Fixture(autoExport = false, withProvider = false)

        fixture.host.markChangedAndAutoExport()

        assertNotNull(fixture.mirror.document)
        assertNull(fixture.provider?.document)
        assertEquals("Settings saved locally. Sync now when ready.", fixture.statuses.last().first)
    }

    @Test
    fun localChangeWritesMirrorThenProviderWhenAutoExportIsEnabled() {
        val fixture = Fixture(autoExport = true, withProvider = true)

        fixture.host.markChangedAndAutoExport()

        assertEquals(fixture.mirror.document, fixture.provider?.document)
        assertEquals(1, fixture.providerPushes)
        assertEquals("Settings auto-synced to provider.", fixture.statuses.last().first)
    }

    @Test
    fun newerProviderDocumentIsAppliedAndMirrored() {
        val fixture = Fixture(autoExport = false, withProvider = true)
        fixture.mirror.document = SettingsSyncDocument(updatedAtEpochMillis = 10L)
        fixture.provider?.document = SettingsSyncDocument(updatedAtEpochMillis = 20L)

        fixture.host.syncNow()

        assertEquals(20L, fixture.applied?.updatedAtEpochMillis)
        assertEquals(20L, fixture.mirror.document?.updatedAtEpochMillis)
        assertTrue(fixture.providerPulls >= 1)
        assertEquals("Settings imported.", fixture.statuses.last().first)
    }

    @Test
    fun providerFailureKeepsMirrorAndPublishesWarning() {
        val fixture = Fixture(autoExport = true, withProvider = true)
        fixture.provider?.writeFailure = IllegalStateException("provider unavailable")

        fixture.host.markChangedAndAutoExport()

        assertNotNull(fixture.mirror.document)
        assertEquals("provider unavailable", fixture.providerFailure)
        assertEquals(NaviampApplicationStatusLevel.Warning, fixture.statuses.last().second)
    }

    @Test
    fun enablingAutoExportRequiresProviderAndPersistsNormalizedValue() {
        val fixture = Fixture(autoExport = false, withProvider = false)

        fixture.host.updateAutoExport(true)

        assertEquals(false, fixture.autoExport)
        assertEquals("Auto-sync disabled.", fixture.directStatus)
    }

    private class Fixture(
        var autoExport: Boolean,
        withProvider: Boolean,
    ) {
        var runtime = SettingsSyncRuntimeState(autoExportEnabled = autoExport)
        var applied: SettingsSyncDocument? = null
        val mirror = MemoryDocumentStore()
        val provider = if (withProvider) MemoryDocumentStore() else null
        var providerPulls = 0
        var providerPushes = 0
        var providerFailure: String? = null
        var directStatus: String? = null
        val statuses = mutableListOf<Pair<String, NaviampApplicationStatusLevel>>()
        private val controller = NaviampSettingsSyncController(
            deviceId = "test",
            state = { runtime },
            saveState = { runtime = it },
            nowEpochMillis = { 100L },
            snapshot = { SettingsSyncLocalSnapshot() },
            applyDocument = { applied = it },
        )
        val host = NaviampSettingsSyncHostController(
            controller = controller,
            mirrorStore = mirror,
            providerStore = { provider },
            autoExportEnabled = { autoExport },
            saveAutoExportEnabled = { enabled ->
                autoExport = enabled
                runtime = runtime.copy(autoExportEnabled = enabled)
            },
            onProviderPullSucceeded = { providerPulls += 1 },
            onProviderPushSucceeded = { providerPushes += 1 },
            onProviderSyncFailed = { providerFailure = it },
            setStatus = { directStatus = it },
            publishStatusEffect = { message, level -> statuses += message to level },
        )
    }
}

private class MemoryDocumentStore : NaviampSettingsSyncDocumentStore {
    override val displayName: String = "memory"
    var document: SettingsSyncDocument? = null
    var writeFailure: Throwable? = null

    override fun read(): SettingsSyncDocument? = document

    override fun write(document: SettingsSyncDocument) {
        writeFailure?.let { throw it }
        this.document = document
    }
}
