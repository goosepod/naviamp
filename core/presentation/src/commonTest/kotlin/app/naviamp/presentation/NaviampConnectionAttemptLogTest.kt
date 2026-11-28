package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionController
import app.naviamp.ui.NaviampSavedConnectionUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampConnectionAttemptLogTest {
    @Test fun recordsAreBoundedNewestFirstAndCarryTheirOutcome() {
        var now = 1_000L
        val log = NaviampConnectionAttemptLog(capacity = 2, nowEpochMillis = { now })
        val first = log.started(NaviampConnectionAttemptTrigger.StartupRestore, NaviampCoreConnectionRequest.Saved("a"), "Home", editorOpen = false)
        log.finished(first, NaviampConnectionAttemptOutcome.Connected)
        val second = log.started(NaviampConnectionAttemptTrigger.SettingsImport, NaviampCoreConnectionRequest.Saved("a"), "Settings", editorOpen = true)
        log.planned(second, restoring = true, clearsPlayback = false)
        now = 1_250L
        log.finished(second, NaviampConnectionAttemptOutcome.Failed, "The server did not respond in time.")
        log.started(NaviampConnectionAttemptTrigger.EditorSave,
            NaviampCoreConnectionRequest.Form(app.naviamp.domain.settings.ConnectionFormState(password = "secret"), "a"), "Settings", editorOpen = true)

        val records = log.records()
        assertEquals(listOf(3L, 2L), records.map { it.sequence })
        assertEquals("form/edit:a", records[0].request)
        assertEquals(NaviampConnectionAttemptOutcome.InProgress, records[0].outcome)
        assertEquals(NaviampConnectionAttemptOutcome.Failed, records[1].outcome)
        assertEquals(250L, records[1].durationMillis)
        val (label, value) = log.diagnosticRows()[1]
        assertTrue(label.startsWith("#2 ") && label.endsWith(" SettingsImport"), label)
        assertEquals("saved:a · route=Settings · editor open · restoring=true · clears playback=false · Failed · " +
            "The server did not respond in time. · 250 ms", value)
        assertFalse(log.diagnosticRows().any { "secret" in it.first || "secret" in it.second }, "Form contents never reach the log")
    }

    @Test fun eachConnectPathRecordsItsOwnTriggerAndOutcome() = kotlinx.coroutines.test.runTest {
        val log = NaviampConnectionAttemptLog()
        val inventory = NaviampCoreConnectionInventory(
            connections = listOf(NaviampCoreSavedConnectionRecord(id = "source-1", displayName = "Home Music",
                serverUrl = "https://music.example", username = "demo")),
            currentSourceId = "source-1",
        )
        val store = NaviampCoreStateStore()
        var failNext: Throwable? = null
        val port = object : NaviampCoreProviderSessionPort {
            override fun initialInventory() = inventory
            override suspend fun connect(request: NaviampCoreConnectionRequest, plan: app.naviamp.app.NaviampConnectionAttemptPlan): NaviampCoreConnectedSession {
                failNext?.let { failNext = null; throw it }
                return NaviampCoreConnectedSession("source-1", "Home Music", "1.0", inventory)
            }
            override suspend fun editableConnection(id: String) = NaviampCoreEditableConnection(
                form = app.naviamp.domain.settings.ConnectionFormState(serverUrl = "https://music.example", username = "demo"))
            override suspend fun deleteConnection(id: String) = NaviampCoreConnectionInventory()
            override suspend fun smartPlaylistProvider(password: String?) = null
            override suspend fun refreshActiveSession() = true
            override suspend fun persistActiveSession() = Unit
            override suspend fun clearActiveSession() = Unit
        }
        val controller = NaviampCoreConnectionController(NaviampConnectionController(), store, port,
            initialInventory = inventory, attemptLog = log)

        controller.restoreInitialConnection()
        controller.execute(NaviampCoreCommand.Connection.ConnectSaved(NaviampSavedConnectionUi(
            id = "source-1", displayName = "Home Music", serverUrl = "https://music.example", username = "demo")))
        failNext = IllegalStateException("Wrong username or password.")
        controller.reconnectImportedConnection("source-1")

        val records = log.records().reversed()
        assertEquals(listOf(NaviampConnectionAttemptTrigger.StartupRestore, NaviampConnectionAttemptTrigger.SavedConnectionButton,
            NaviampConnectionAttemptTrigger.SettingsImport), records.map { it.trigger })
        assertEquals(listOf(NaviampConnectionAttemptOutcome.Connected, NaviampConnectionAttemptOutcome.Connected,
            NaviampConnectionAttemptOutcome.Failed), records.map { it.outcome })
        assertEquals("Wrong username or password.", records[2].detail)
        assertTrue(records.all { it.restoring != null && it.durationMillis != null })
    }
}
