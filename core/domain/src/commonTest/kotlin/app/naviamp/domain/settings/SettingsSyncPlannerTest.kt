package app.naviamp.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsSyncPlannerTest {
    @Test
    fun asksForInitialChoiceWhenNothingExistsYet() {
        val plan = planSettingsSync(
            localDocument = null,
            syncedDocument = null,
            syncLocationConfigured = false,
            nowEpochMillis = 100L,
            deviceId = "desktop",
        )

        assertEquals(SettingsSyncAction.AskForInitialSetupChoice, plan.action)
        assertEquals(SettingsSyncPlanReason.NoLocalOrSyncedSettings, plan.reason)
        assertNull(plan.documentToImport)
        assertNull(plan.documentToWrite)
    }

    @Test
    fun firstRunWithSyncedSettingsImportsAndRequiresSecretsForServerProfiles() {
        val synced = document(
            updatedAt = 50L,
            serverProfiles = listOf(profile("goosepod")),
        )

        val plan = planSettingsSync(
            localDocument = null,
            syncedDocument = synced,
            syncLocationConfigured = true,
            nowEpochMillis = 100L,
            deviceId = "android",
        )

        assertEquals(SettingsSyncAction.ImportFromSyncFile, plan.action)
        assertEquals(SettingsSyncPlanReason.FirstRunWithSyncedSettings, plan.reason)
        assertEquals(synced.normalized(), plan.documentToImport)
        assertTrue(plan.requiresServerSecrets)
    }

    @Test
    fun firstRunWithSyncedSettingsDoesNotRequireSecretsWhenNoServerProfilesExist() {
        val plan = planSettingsSync(
            localDocument = null,
            syncedDocument = document(updatedAt = 50L),
            syncLocationConfigured = true,
            nowEpochMillis = 100L,
            deviceId = "android",
        )

        assertEquals(SettingsSyncAction.ImportFromSyncFile, plan.action)
        assertFalse(plan.requiresServerSecrets)
    }

    @Test
    fun exportsLocalSettingsWhenSyncFileDoesNotExist() {
        val local = document(updatedAt = 10L)

        val plan = planSettingsSync(
            localDocument = local,
            syncedDocument = null,
            syncLocationConfigured = true,
            nowEpochMillis = 100L,
            deviceId = "desktop",
        )

        val write = assertNotNull(plan.documentToWrite)
        assertEquals(SettingsSyncAction.ExportToSyncFile, plan.action)
        assertEquals(SettingsSyncPlanReason.LocalSettingsNeedInitialExport, plan.reason)
        assertEquals(100L, write.updatedAtEpochMillis)
        assertEquals("desktop", write.lastWriterDeviceId)
    }

    @Test
    fun importsWhenSyncedDocumentIsNewer() {
        val local = document(updatedAt = 10L)
        val synced = document(updatedAt = 20L, serverProfiles = listOf(profile("goosepod")))

        val plan = planSettingsSync(
            localDocument = local,
            syncedDocument = synced,
            syncLocationConfigured = true,
            nowEpochMillis = 100L,
            deviceId = "desktop",
        )

        assertEquals(SettingsSyncAction.ImportFromSyncFile, plan.action)
        assertEquals(SettingsSyncPlanReason.SyncedSettingsAreNewer, plan.reason)
        assertEquals(synced.normalized(), plan.documentToImport)
        assertTrue(plan.requiresServerSecrets)
    }

    @Test
    fun exportsWhenLocalDocumentIsNewer() {
        val local = document(updatedAt = 30L)
        val synced = document(updatedAt = 20L)

        val plan = planSettingsSync(
            localDocument = local,
            syncedDocument = synced,
            syncLocationConfigured = true,
            nowEpochMillis = 40L,
            deviceId = "mac",
        )

        val write = assertNotNull(plan.documentToWrite)
        assertEquals(SettingsSyncAction.ExportToSyncFile, plan.action)
        assertEquals(SettingsSyncPlanReason.LocalSettingsAreNewer, plan.reason)
        assertEquals(40L, write.updatedAtEpochMillis)
        assertEquals("mac", write.lastWriterDeviceId)
    }

    @Test
    fun doesNothingWhenTimestampsMatch() {
        val plan = planSettingsSync(
            localDocument = document(updatedAt = 20L),
            syncedDocument = document(updatedAt = 20L),
            syncLocationConfigured = true,
            nowEpochMillis = 40L,
            deviceId = "mac",
        )

        assertEquals(SettingsSyncAction.NoOp, plan.action)
        assertEquals(SettingsSyncPlanReason.DocumentsMatch, plan.reason)
        assertNull(plan.documentToImport)
        assertNull(plan.documentToWrite)
    }

    @Test
    fun rejectsNewerSchemaSyncFiles() {
        val plan = planSettingsSync(
            localDocument = document(updatedAt = 20L),
            syncedDocument = document(updatedAt = 30L).copy(schemaVersion = CurrentSettingsSyncSchemaVersion + 1),
            syncLocationConfigured = true,
            nowEpochMillis = 40L,
            deviceId = "mac",
        )

        assertEquals(SettingsSyncAction.UnsupportedSyncFile, plan.action)
        assertEquals(SettingsSyncPlanReason.SyncedSchemaIsNewer, plan.reason)
        assertNotNull(plan.documentToImport)
        assertNull(plan.documentToWrite)
    }

    @Test
    fun writeMetadataNeverMovesTimestampBackward() {
        val updated = document(updatedAt = 100L)
            .withSyncWriteMetadata(nowEpochMillis = 50L, deviceId = " desktop ")

        assertEquals(100L, updated.updatedAtEpochMillis)
        assertEquals("desktop", updated.lastWriterDeviceId)
    }

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
