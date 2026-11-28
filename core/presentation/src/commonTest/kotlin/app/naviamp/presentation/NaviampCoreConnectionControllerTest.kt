package app.naviamp.presentation

import kotlinx.coroutines.test.runCurrent

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.app.NaviampConnectionController
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.ui.NaviampSavedConnectionUi
import app.naviamp.ui.NaviampLibrarySourcePickerError
import app.naviamp.ui.NaviampAccountSwitcherError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

class NaviampCoreConnectionControllerTest {
    @Test
    fun runtimeRenewalIsSharedAndSurvivesSceneWaiterCancellation() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        val lifecycle = NaviampCoreProviderSessionLifecycle(fixture.port, refreshIntervalMillis = 100L)
        val runtime = lifecycle.start(backgroundScope)
        kotlin.test.assertSame(runtime, lifecycle.start(backgroundScope))
        val scene = launch { runtime.join() }
        runCurrent()
        scene.cancelAndJoin()
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(1, fixture.port.refreshCalls)
        assertTrue(runtime.isActive)
        lifecycle.close()
        runtime.join()
        advanceTimeBy(200L)
        runCurrent()
        assertEquals(1, fixture.port.refreshCalls)
        assertFalse(runtime.isActive)
    }

    @Test fun provisioningAnAlreadyConnectedPrimaryStillSavesItsCompanionWithoutReconnecting() = kotlinx.coroutines.test.runTest {
        var changes = 0
        val f = fixture(onCompanionChanged = { changes++ })
        f.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        f.port.connectRequests.clear()
        val companion = app.naviamp.domain.source.AurralCompanion(true, "https://aurral.example", "secondary", "secret")
        val form = ConnectionFormState(serverUrl = "https://music.example", username = "demo", password = "primary-secret",
            aurralCompanion = companion)
        assertTrue(f.controller.provisionConnect(form))
        assertEquals("source-1" to companion, f.port.savedCompanions.single())
        assertEquals(1, changes)
        assertTrue(f.port.connectRequests.isEmpty())
        assertTrue(f.controller.provisionConnect(form.copy(aurralCompanion = null)))
        assertEquals(1, f.port.savedCompanions.size, "Older offers must retain the target's attachment")
        f.port.companionSaveFailure = IllegalStateException("Storage unavailable")
        assertFalse(f.controller.provisionConnect(form))
        assertEquals(1, changes)
        assertFalse(f.controller.provisionConnect(form.copy(aurralCompanion = companion.copy(baseUrl = "invalid"))))
        assertTrue(f.port.connectRequests.isEmpty())
    }

    @Test fun savingAnUnchangedPrimaryWithAnExistingCompanionCanStillReconnectThePrimary() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        fixture.port.editableCompanion = app.naviamp.domain.source.AurralCompanion(true, "https://aurral.example", "secondary", "secret")
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.port.connectRequests.clear()
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        assertFalse(fixture.store.state.value.shell.connectionSettings.connection.saveWithoutReconnect)
        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
        assertEquals(1, fixture.port.connectRequests.size)
        assertTrue(fixture.port.savedCompanions.isEmpty())
    }

    @Test fun failedCompanionSaveKeepsThePrimaryConnectedAndTheEditorOpen() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        val form = fixture.store.state.value.shell.connectionSettings.connection.form
        fixture.port.companionSaveFailure = IllegalStateException("credential protector unavailable")
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form.copy(
            aurralCompanion = app.naviamp.domain.source.AurralCompanion(true, "https://aurral.example", "secondary", "secret"))))
        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(connection.connected)
        assertTrue(connection.editingConnection)
        assertTrue(connection.statusIsError)
        assertEquals("connection_aurral_companion_save_failed", connection.status)
    }

    @Test fun companionOnlyEditPreservesTheConnectedPrimaryAndDoesNotRestoreItsQueue() = kotlinx.coroutines.test.runTest {
        var connectedCallbacks = 0
        var sourceChanges = 0
        var companionChanges = 0
        val fixture = fixture(onConnected = { connectedCallbacks++ }, onSourceChanging = { _, _ -> sourceChanges++ },
            onCompanionChanged = { companionChanges++ })
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        val before = fixture.store.state.value.shell.connectionSettings.currentSourceId
        fixture.port.connectRequests.clear()
        connectedCallbacks = 0; sourceChanges = 0
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        val form = fixture.store.state.value.shell.connectionSettings.connection.form
        val companion = app.naviamp.domain.source.AurralCompanion(true, "https://aurral.example", "secondary", "secret")
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form.copy(aurralCompanion = companion)))
        assertTrue(fixture.store.state.value.shell.connectionSettings.connection.saveWithoutReconnect)
        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
        assertEquals("source-1" to companion, fixture.port.savedCompanions.single())
        assertEquals(before, fixture.store.state.value.shell.connectionSettings.currentSourceId)
        assertTrue(fixture.store.state.value.shell.connectionSettings.connection.connected)
        assertTrue(fixture.port.connectRequests.isEmpty())
        assertEquals(0, connectedCallbacks)
        assertEquals(0, sourceChanges)
        assertEquals(1, companionChanges)
    }

    @Test fun companionOnlyEditWithRejectedCredentialsIsReportedAndNotSaved() = kotlinx.coroutines.test.runTest {
        var companionChanges = 0
        val fixture = fixture(onCompanionChanged = { companionChanges++ })
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        val form = fixture.store.state.value.shell.connectionSettings.connection.form
        val companion = app.naviamp.domain.source.AurralCompanion(true, "https://aurral.example", "secondary", "wrong")
        fixture.port.companionVerification = app.naviamp.domain.provider.ProviderDiscoveryStatus.SignInRequired
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form.copy(aurralCompanion = companion)))
        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertEquals(listOf(companion), fixture.port.verifiedCompanions)
        assertTrue(fixture.port.savedCompanions.isEmpty())
        assertEquals(0, companionChanges)
        assertTrue(connection.connected)
        assertTrue(connection.editingConnection)
        assertTrue(connection.statusIsError)
        assertEquals("connection_aurral_companion_sign_in_failed", connection.status)
    }

    @Test fun companionVerificationFailuresMapToSpecificFormErrors() = kotlinx.coroutines.test.runTest {
        val expected = mapOf(
            app.naviamp.domain.provider.ProviderDiscoveryStatus.PasswordRequired to "connection_aurral_companion_sign_in_failed",
            app.naviamp.domain.provider.ProviderDiscoveryStatus.Forbidden to "connection_aurral_companion_sign_in_failed",
            app.naviamp.domain.provider.ProviderDiscoveryStatus.Unsupported to "connection_aurral_companion_unsupported",
            app.naviamp.domain.provider.ProviderDiscoveryStatus.Unavailable to "connection_aurral_companion_unreachable",
        )
        expected.forEach { (status, key) ->
            val fixture = fixture()
            fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
            fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
            val form = fixture.store.state.value.shell.connectionSettings.connection.form
            fixture.port.companionVerification = status
            fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form.copy(
                aurralCompanion = app.naviamp.domain.source.AurralCompanion(true, "https://aurral.example", "secondary", "secret"))))
            fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
            assertEquals(key, fixture.store.state.value.shell.connectionSettings.connection.status, "$status")
            assertTrue(fixture.port.savedCompanions.isEmpty())
        }
    }

    @Test fun connectingWithAChangedCompanionVerifiesItBeforeTouchingThePrimary() = kotlinx.coroutines.test.runTest {
        var sourceChanges = 0
        val fixture = fixture(onSourceChanging = { _, _ -> sourceChanges++ })
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.port.connectRequests.clear()
        sourceChanges = 0
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        val form = fixture.store.state.value.shell.connectionSettings.connection.form
        fixture.port.companionVerification = app.naviamp.domain.provider.ProviderDiscoveryStatus.SignInRequired
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form.copy(displayName = "Renamed",
            aurralCompanion = app.naviamp.domain.source.AurralCompanion(true, "https://aurral.example", "secondary", "wrong"))))
        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
        assertTrue(fixture.port.connectRequests.isEmpty())
        assertEquals(0, sourceChanges)
        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(connection.connected)
        assertTrue(connection.editingConnection)
        assertEquals("connection_aurral_companion_sign_in_failed", connection.status)
    }

    @Test fun disabledOrUnchangedCompanionIsNotReverified() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        fixture.port.editableCompanion = app.naviamp.domain.source.AurralCompanion(true, "https://aurral.example", "secondary", "secret")
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        val form = fixture.store.state.value.shell.connectionSettings.connection.form
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form.copy(displayName = "Renamed")))
        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
        assertTrue(fixture.port.verifiedCompanions.isEmpty())
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        val edited = fixture.store.state.value.shell.connectionSettings.connection.form
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(edited.copy(
            aurralCompanion = edited.aurralCompanion!!.copy(enabled = false, password = "other"))))
        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
        assertTrue(fixture.port.verifiedCompanions.isEmpty())
    }

    @Test fun savingAnEditOfTheCurrentConnectionKeepsItsPlaybackAndLibrary() = kotlinx.coroutines.test.runTest {
        var connectedCallbacks = 0
        var sourceChanges = 0
        val selections = mutableListOf<Pair<String, Boolean>>()
        val fixture = fixture(onConnected = { connectedCallbacks++ }, onSourceChanging = { _, _ -> sourceChanges++ },
            onSavedSourceSelected = { id, changed -> selections += id to changed })
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.port.connectRequests.clear()
        connectedCallbacks = 0; sourceChanges = 0
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        val form = fixture.store.state.value.shell.connectionSettings.connection.form
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form.copy(displayName = "Renamed", password = "new-secret")))
        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
        val (request, plan) = fixture.port.connectRequests.single()
        assertEquals("source-1", (request as NaviampCoreConnectionRequest.Form).savedConnectionId)
        assertFalse(plan.clearExistingPlayback, "Editing the same account must not reset the queue")
        assertFalse(plan.clearProviderData, "Editing the same account must not discard its Library cache")
        assertFalse(plan.runFullLibraryRefresh)
        assertEquals(0, sourceChanges)
        assertEquals(0, connectedCallbacks, "The current queue is kept, not restored from storage")
        assertEquals(listOf("source-1" to false), selections)
        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(connection.connected)
        assertFalse(connection.editingConnection)
    }

    @Test fun failedEditOfTheCurrentConnectionKeepsTheSessionAndReportsTheError() = kotlinx.coroutines.test.runTest {
        var sourceChanges = 0
        val fixture = fixture(onSourceChanging = { _, _ -> sourceChanges++ })
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        sourceChanges = 0
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        val form = fixture.store.state.value.shell.connectionSettings.connection.form
        fixture.port.connectFailure = IllegalStateException("Wrong username or password.")
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form.copy(password = "wrong")))
        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
        val settings = fixture.store.state.value.shell.connectionSettings
        assertEquals(0, sourceChanges)
        assertEquals("source-1", settings.currentSourceId)
        assertTrue(settings.connection.connected)
        assertTrue(settings.connection.editingConnection)
        assertTrue(settings.connection.statusIsError)
        assertEquals("Wrong username or password.", settings.connection.status)
    }

    @Test fun editingTheCurrentConnectionToAnotherServerStillStartsFresh() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.port.connectRequests.clear()
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        val form = fixture.store.state.value.shell.connectionSettings.connection.form
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form.copy(serverUrl = "https://other.example")))
        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
        assertTrue(fixture.port.connectRequests.single().second.clearExistingPlayback)
    }

    @Test fun companionCertificateSelectionUpdatesOnlyItsOwnTlsIdentity() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(certificateFiles = NaviampCoreCertificateFilePort { "/companion.p12" })
        val original = ConnectionFormState(serverUrl = "https://primary.example", clientCertificatePath = "/primary.p12",
            aurralCompanion = app.naviamp.domain.source.AurralCompanion(enabled = true))
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(original))
        fixture.controller.execute(NaviampCoreCommand.Connection.ChooseCertificate(NaviampConnectionCertificateKind.ClientIdentity, companion = true))
        val updated = fixture.store.state.value.shell.connectionSettings.connection.form
        assertEquals(original.clientCertificatePath, updated.clientCertificatePath)
        assertEquals("/companion.p12", updated.aurralCompanion!!.tlsSettings.clientCertificateKeyStorePath)
    }

    @Test fun importedConnectionsCanBeEditedAndConnectedThroughNestedRoutersWithoutRestart() = kotlinx.coroutines.test.runTest {
        val empty = NaviampCoreConnectionInventory()
        val leaf = FakeProviderSessionPort(empty, null, false, emptyList(), emptyList())
        val inner = NaviampCoreProviderSessionRouter(listOf(NaviampCoreProviderSessionRoute(setOf("subsonic"), leaf)))
        val outer = NaviampCoreProviderSessionRouter(listOf(NaviampCoreProviderSessionRoute(setOf("subsonic"), inner)))
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectionController(NaviampConnectionController(), store, outer)
        val imported = savedRecord(id = "imported").copy(providerId = "subsonic")
        // Settings import has already committed to the repository behind the leaf effect.
        leaf.inventory = NaviampCoreConnectionInventory(connections = listOf(imported))
        controller.replaceSavedConnections(listOf(imported))
        val displayed = store.state.value.shell.connectionSettings.connection.savedConnections.single()
        controller.execute(NaviampCoreCommand.Connection.Edit(displayed))
        assertEquals("https://edited.example", store.state.value.shell.connectionSettings.connection.form.serverUrl)
        controller.execute(NaviampCoreCommand.Connection.ConnectSaved(displayed))
        assertEquals(NaviampCoreConnectionRequest.Saved("imported"), leaf.connectRequests.single().first)
        assertEquals("imported", store.state.value.shell.connectionSettings.currentSourceId)
        assertTrue(store.state.value.shell.connectionSettings.connection.connected)
    }

    @Test fun openingAnotherIdenticalFormRevokesAPendingCertificateSelection() = kotlinx.coroutines.test.runTest {
        val selected = CompletableDeferred<String?>()
        val fixture = fixture(certificateFiles = NaviampCoreCertificateFilePort { selected.await() })
        fixture.controller.dispatch(NaviampCoreCommand.Connection.New)
        val choosing = launch { fixture.controller.execute(NaviampCoreCommand.Connection.ChooseCertificate(NaviampConnectionCertificateKind.ClientIdentity)) }
        runCurrent()
        fixture.controller.dispatch(NaviampCoreCommand.Connection.New)
        val before = fixture.store.state.value.shell.connectionSettings.connection.form
        selected.complete("/private/old-form.p12")
        choosing.join()
        assertEquals(before, fixture.store.state.value.shell.connectionSettings.connection.form)
    }

    @Test fun latestCertificateSelectionWinsEvenWhenTheOlderReadFinishesFirst() = kotlinx.coroutines.test.runTest {
        val first = CompletableDeferred<String?>()
        val latest = CompletableDeferred<String?>()
        var calls = 0
        val fixture = fixture(certificateFiles = NaviampCoreCertificateFilePort { if (calls++ == 0) first.await() else latest.await() })
        val old = launch { fixture.controller.execute(NaviampCoreCommand.Connection.ChooseCertificate(NaviampConnectionCertificateKind.ClientIdentity)) }
        runCurrent()
        val next = launch { fixture.controller.execute(NaviampCoreCommand.Connection.ChooseCertificate(NaviampConnectionCertificateKind.ClientIdentity)) }
        runCurrent()
        val before = fixture.store.state.value.shell.connectionSettings.connection.form
        first.complete("/old.p12")
        old.join()
        assertEquals(before, fixture.store.state.value.shell.connectionSettings.connection.form)
        latest.complete("/latest.p12")
        next.join()
        assertEquals("/latest.p12", fixture.store.state.value.shell.connectionSettings.connection.form.clientCertificatePath)
    }
    @Test fun certificateImportUpdatesOnlyItsSelectedFieldAndPreservesOtherCredentials() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(certificateFiles = NaviampCoreCertificateFilePort { kind -> "/private/$kind" })
        val original = ConnectionFormState(serverUrl = "https://music.example", password = "unchanged", clientCertificatePassword = "certificate-password")
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(original))
        fixture.controller.execute(NaviampCoreCommand.Connection.ChooseCertificate(NaviampConnectionCertificateKind.ServerAuthority))
        val server = fixture.store.state.value.shell.connectionSettings.connection.form
        assertEquals(original.copy(customCertificatePath = "/private/ServerAuthority"), server)
        fixture.controller.execute(NaviampCoreCommand.Connection.ChooseCertificate(NaviampConnectionCertificateKind.ClientIdentity))
        assertEquals(server.copy(clientCertificatePath = "/private/ClientIdentity"), fixture.store.state.value.shell.connectionSettings.connection.form)
    }

    @Test fun cancelledCertificateSelectionLeavesTheFormUnchanged() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(certificateFiles = NaviampCoreCertificateFilePort { null })
        val before = fixture.store.state.value.shell.connectionSettings.connection.form
        fixture.controller.execute(NaviampCoreCommand.Connection.ChooseCertificate(NaviampConnectionCertificateKind.ClientIdentity))
        assertEquals(before, fixture.store.state.value.shell.connectionSettings.connection.form)
    }

    @Test fun lateCertificateSelectionDoesNotOverwriteAnotherForm() = kotlinx.coroutines.test.runTest {
        val selected = CompletableDeferred<String?>()
        val fixture = fixture(certificateFiles = NaviampCoreCertificateFilePort { selected.await() })
        val choosing = launch { fixture.controller.execute(NaviampCoreCommand.Connection.ChooseCertificate(NaviampConnectionCertificateKind.ClientIdentity)) }
        runCurrent()
        val next = ConnectionFormState(serverUrl = "https://another.example", password = "other")
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(next))
        selected.complete("/private/late.p12")
        choosing.join()
        assertEquals(next, fixture.store.state.value.shell.connectionSettings.connection.form)
    }

    @Test fun certificateImportFailureIsRecoverableAndKeepsTheOldCertificate() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(certificateFiles = NaviampCoreCertificateFilePort { error("Native picker read failed") })
        val before = ConnectionFormState(clientCertificatePath = "/old.p12")
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(before))
        fixture.controller.execute(NaviampCoreCommand.Connection.ChooseCertificate(NaviampConnectionCertificateKind.ClientIdentity))
        val after = fixture.store.state.value.shell.connectionSettings.connection
        assertEquals(before, after.form)
        assertEquals("connection_certificate_import_failed", after.status)
        assertTrue(after.statusIsError)
    }
    @Test fun accountHandoffKeepsBothSavedAccountsAndUsesStoredCredentials() = kotlinx.coroutines.test.runTest {
        val changes = mutableListOf<Pair<String?, String>>()
        val homeRequests = mutableListOf<String>()
        val fixture = fixture(savedRecords = listOf(savedRecord(), savedRecord(id = "source-2")),
            onUserConnected = { homeRequests += it },
            onSourceChanging = { previous, next -> changes += previous to next })
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.port.connectRequests.clear()
        changes.clear()
        homeRequests.clear()
        fixture.controller.dispatch(NaviampCoreCommand.Connection.OpenAccounts)

        fixture.controller.execute(NaviampCoreCommand.Connection.SwitchAccount(savedConnectionUi().copy(id = "source-2")))

        val settings = fixture.store.state.value.shell.connectionSettings
        assertEquals("source-2", settings.currentSourceId)
        assertEquals(listOf(false, true), settings.connection.savedConnections.map { it.current })
        assertEquals(listOf<Pair<String?, String>>("source-1" to "source-2"), changes)
        assertEquals(NaviampCoreConnectionRequest.Saved("source-2"), fixture.port.connectRequests.single().first)
        assertFalse(fixture.port.connectRequests.single().second.clearProviderData)
        assertFalse(settings.accountSwitcher.visible)
        assertFalse(settings.accountSwitcher.connecting)
        assertEquals(listOf("source-2"), homeRequests)
        assertEquals(1L, settings.accountSelectionGeneration)
    }

    @Test fun rejectedOrUnavailableAccountsLeaveTheCurrentSessionUsable() = kotlinx.coroutines.test.runTest {
        for (failure in listOf(IllegalStateException("expired credential"), IllegalStateException("connection refused"))) {
            var resets = 0
            var offlineRestores = 0
            var homeRequests = 0
            val fixture = fixture(savedRecords = listOf(savedRecord(), savedRecord(id = "source-2")),
                onUserConnected = { homeRequests++ },
                onSourceChanging = { _, _ -> resets++ }, onOfflineRestored = { offlineRestores++ })
            fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
            homeRequests = 0
            val before = fixture.store.state.value.shell.connectionSettings
            fixture.port.connectFailure = failure
            fixture.controller.dispatch(NaviampCoreCommand.Connection.OpenAccounts)

            fixture.controller.execute(NaviampCoreCommand.Connection.SwitchAccount(savedConnectionUi().copy(id = "source-2")))

            val after = fixture.store.state.value.shell.connectionSettings
            assertTrue(after.connection.connected)
            assertFalse(after.connection.isConnecting)
            assertEquals(before.connection, after.connection)
            assertEquals("source-1", after.currentSourceId)
            assertEquals("source-1", fixture.port.inventory.currentSourceId)
            assertEquals(NaviampAccountSwitcherError.ConnectionFailed, after.accountSwitcher.error)
            assertTrue(after.accountSwitcher.visible)
            assertEquals(0, resets)
            assertEquals(0, offlineRestores)
            assertEquals(0, homeRequests)
            assertEquals(0L, after.accountSelectionGeneration)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun backCancelsAnUnfinishedSwitchAndRetainsThePreviousAccount() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(savedRecords = listOf(savedRecord(), savedRecord(id = "source-2")))
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.port.connectGate = CompletableDeferred()
        fixture.controller.dispatch(NaviampCoreCommand.Connection.OpenAccounts)
        val job = launch {
            fixture.controller.execute(NaviampCoreCommand.Connection.SwitchAccount(savedConnectionUi().copy(id = "source-2")))
        }
        runCurrent()
        val pending = fixture.store.state.value.shell.connectionSettings
        assertTrue(pending.connection.connected)
        assertTrue(pending.connection.isConnecting)
        assertFalse(pending.connection.restoringConnection)
        assertTrue(pending.accountSwitcher.connecting)
        assertEquals("source-1", pending.currentSourceId)

        fixture.controller.dispatch(NaviampCoreCommand.Connection.CloseAccounts)
        job.join()

        val after = fixture.store.state.value.shell.connectionSettings
        assertTrue(job.isCancelled)
        assertTrue(after.connection.connected)
        assertFalse(after.connection.isConnecting)
        assertFalse(after.accountSwitcher.visible)
        assertFalse(after.accountSwitcher.connecting)
        assertEquals("source-1", fixture.port.inventory.currentSourceId)
        assertEquals(0L, after.accountSelectionGeneration)
    }

    @Test fun choosingTheCurrentAccountIsIdempotentAndMissingAccountsAreRejected() = kotlinx.coroutines.test.runTest {
        val homeRequests = mutableListOf<String>()
        val fixture = fixture(onUserConnected = { homeRequests += it })
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.port.connectRequests.clear()
        homeRequests.clear()
        fixture.controller.dispatch(NaviampCoreCommand.Connection.OpenAccounts)
        fixture.controller.execute(NaviampCoreCommand.Connection.SwitchAccount(savedConnectionUi()))
        assertTrue(fixture.port.connectRequests.isEmpty())
        assertFalse(fixture.store.state.value.shell.connectionSettings.accountSwitcher.visible)
        assertEquals(listOf("source-1"), homeRequests)
        assertEquals(1L, fixture.store.state.value.shell.connectionSettings.accountSelectionGeneration)
        fixture.controller.execute(NaviampCoreCommand.Connection.SwitchAccount(savedConnectionUi().copy(id = "missing")))
        assertTrue(fixture.port.connectRequests.isEmpty())
        assertEquals("source-1", fixture.store.state.value.shell.connectionSettings.currentSourceId)
        assertEquals(NaviampAccountSwitcherError.ConnectionFailed,
            fixture.store.state.value.shell.connectionSettings.accountSwitcher.error)
        assertEquals(listOf("source-1"), homeRequests)
    }

    @Test fun failedAccountAdditionKeepsTheExistingSessionAndEditableForm() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.controller.dispatch(NaviampCoreCommand.Connection.AddAccount)
        val form = ConnectionFormState(serverUrl = "https://another.example", username = "other", password = "invalid")
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form))
        fixture.port.connectFailure = IllegalStateException("expired credential")

        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)

        val after = fixture.store.state.value.shell.connectionSettings
        assertTrue(after.connection.connected)
        assertTrue(after.connection.editingConnection)
        assertEquals("source-1", after.currentSourceId)
        assertEquals(form, after.connection.form)
        assertEquals(NaviampAccountSwitcherError.ConnectionFailed, after.accountSwitcher.error)
        assertTrue(after.accountSwitcher.addingAccount)
        assertFalse(after.accountSwitcher.visible)
    }

    @Test fun addingAndCancellingAnAccountReusesTheFormWithoutSigningOut() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.controller.dispatch(NaviampCoreCommand.Connection.OpenAccounts)
        fixture.controller.dispatch(NaviampCoreCommand.Connection.AddAccount)
        val adding = fixture.store.state.value.shell.connectionSettings
        assertTrue(adding.accountSwitcher.addingAccount)
        assertTrue(adding.connection.editingConnection)
        assertTrue(adding.connection.connected)
        assertEquals(ConnectionFormState(), adding.connection.form)

        fixture.controller.dispatch(NaviampCoreCommand.Connection.CancelForm)

        val after = fixture.store.state.value.shell.connectionSettings
        assertTrue(after.accountSwitcher.visible)
        assertFalse(after.accountSwitcher.addingAccount)
        assertFalse(after.connection.editingConnection)
        assertTrue(after.connection.connected)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun providerSessionLifecycleRefreshesNowAndOnTheSharedSchedule() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        val lifecycle = NaviampCoreProviderSessionLifecycle(
            sessionPort = fixture.port,
            refreshIntervalMillis = 100L,
        )

        lifecycle.refreshNow()
        val scheduled = launch { lifecycle.maintainWhileMounted() }
        runCurrent()
        assertEquals(1, fixture.port.refreshCalls)

        advanceTimeBy(100L)
        runCurrent()
        assertEquals(2, fixture.port.refreshCalls)
        scheduled.cancelAndJoin()
    }

    @Test
    fun formAndEditingStateAreOwnedImmediatelyByCore() {
        val fixture = fixture()
        val form = ConnectionFormState(serverUrl = "https://music.example", username = "demo", password = "secret")

        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form))
        fixture.controller.dispatch(NaviampCoreCommand.Connection.New)

        val state = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(state.editingConnection)
        assertFalse(state.editingSavedConnection)
        assertEquals(ConnectionFormState(), state.form)
    }

    @Test
    fun cancellingSavedConnectionEditsPreservesSessionAndReloadsSavedValues() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.port.connectRequests.clear()
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        val original = fixture.store.state.value.shell.connectionSettings.connection.form
        val inventory = fixture.port.inventory
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(original.copy(username = "unsaved")))

        fixture.controller.dispatch(NaviampCoreCommand.Connection.CancelForm)

        val state = fixture.store.state.value.shell.connectionSettings.connection
        assertFalse(state.editingConnection)
        assertFalse(state.editingSavedConnection)
        assertTrue(state.connected)
        assertTrue(fixture.port.connectRequests.isEmpty())
        assertEquals(inventory, fixture.port.inventory)
        fixture.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        assertEquals(original, fixture.store.state.value.shell.connectionSettings.connection.form)
    }

    @Test
    fun successfulConnectionUsesSharedAttemptPolicyAndPublishesOneSnapshot() = kotlinx.coroutines.test.runTest {
        var connectedNotifications = 0
        var userConnectedNotifications = 0
        val fixture = fixture(
            onConnected = { connectedNotifications += 1 },
            onUserConnected = { userConnectedNotifications += 1 },
        )
        val form = ConnectionFormState(serverUrl = "https://music.example", username = "demo", password = "secret")
        fixture.controller.dispatch(NaviampCoreCommand.Connection.ChangeForm(form))

        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)

        val (request, plan) = fixture.port.connectRequests.single()
        assertEquals(NaviampCoreConnectionRequest.Form(form), request)
        assertFalse(plan.restoreSavedSession)
        assertTrue(plan.clearExistingPlayback)
        val state = fixture.store.state.value.shell.connectionSettings
        assertTrue(state.connection.connected)
        assertFalse(state.connection.isConnecting)
        assertEquals("Connected to Home Music.", state.connection.status)
        assertEquals("source-1", state.currentSourceId)
        assertTrue(state.connection.savedConnections.single().current)
        assertEquals(1, connectedNotifications)
        assertEquals(1, userConnectedNotifications)
    }

    @Test
    fun restoringInitialConnectionDoesNotReplaceTheRestoredRoute() = kotlinx.coroutines.test.runTest {
        var userConnectedNotifications = 0
        val fixture = fixture(onUserConnected = { userConnectedNotifications += 1 })

        fixture.controller.restoreInitialConnection()

        assertTrue(fixture.store.state.value.shell.connectionSettings.connection.connected)
        assertEquals(0, userConnectedNotifications)
    }

    @Test
    fun failedConnectProvisioningLeavesTheExistingSourceUntouched() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(connectFailure = IllegalStateException("invalid credential"))

        val connected = fixture.controller.provisionConnect(
            ConnectionFormState(
                serverUrl = "https://other.example",
                username = "listener",
                password = "wrong",
            ),
        )

        assertFalse(connected)
        assertEquals("source-1", fixture.store.state.value.shell.connectionSettings.currentSourceId)
        assertEquals(listOf("source-1"), fixture.port.inventory.connections.map { it.id })
    }

    @Test
    fun provisioningTheAlreadyConnectedSourceIsIdempotent() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        fixture.controller.execute(
            NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()),
        )
        fixture.port.connectRequests.clear()

        val connected = fixture.controller.provisionConnect(
            ConnectionFormState(
                serverUrl = "https://MUSIC.example/",
                username = "demo",
                password = "newly-transferred-secret",
            ),
        )

        assertTrue(connected)
        assertTrue(fixture.port.connectRequests.isEmpty())
        assertEquals("source-1", fixture.store.state.value.shell.connectionSettings.currentSourceId)
    }

    @Test
    fun savedConnectionUsesRestorationPolicy() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()

        fixture.controller.execute(
            NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()),
        )

        val (request, plan) = fixture.port.connectRequests.single()
        assertEquals(NaviampCoreConnectionRequest.Saved("source-1"), request)
        assertTrue(plan.restoreSavedSession)
        assertFalse(plan.clearExistingPlayback)
    }

    @Test
    fun successfulServerSwitchPublishesSourceTransitionBeforeTheNewSource() = kotlinx.coroutines.test.runTest {
        val transitions = mutableListOf<String>()
        lateinit var fixture: ConnectionFixture
        fixture = fixture(
            currentSourceId = "source-old",
            onSourceChanging = { previous, next ->
                val published = fixture.store.state.value.shell.connectionSettings.currentSourceId
                transitions += "$previous->$next:$published"
            },
        )

        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))

        assertEquals(listOf("source-old->source-1:source-old"), transitions)
        assertEquals("source-1", fixture.store.state.value.shell.connectionSettings.currentSourceId)
    }

    @Test
    fun switchingAmongMultipleSavedSourcesPublishesExactlyOneCurrentSource() = kotlinx.coroutines.test.runTest {
        val records = listOf(
            savedRecord(),
            savedRecord(
                id = "source-2",
                displayName = "Studio Music",
                serverUrl = "https://studio.example",
            ),
        )
        val transitions = mutableListOf<Pair<String?, String>>()
        val fixture = fixture(
            savedRecords = records,
            onSourceChanging = { previous, next -> transitions += previous to next },
        )

        fixture.controller.execute(
            NaviampCoreCommand.Connection.ConnectSaved(
                NaviampSavedConnectionUi(
                    id = "source-2",
                    displayName = "Studio Music",
                    serverUrl = "https://studio.example",
                    username = "demo",
                ),
            ),
        )

        assertEquals(NaviampCoreConnectionRequest.Saved("source-2"), fixture.port.connectRequests.single().first)
        assertEquals(listOf<Pair<String?, String>>("source-1" to "source-2"), transitions)
        val settings = fixture.store.state.value.shell.connectionSettings
        assertEquals("source-2", settings.currentSourceId)
        assertEquals(listOf(false, true), settings.connection.savedConnections.map { it.current })
        assertEquals("Connected to Studio Music.", settings.connection.status)
    }

    @Test
    fun startupRestoresThePreferredSavedConnectionInCore() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(currentSourceId = null)

        fixture.controller.restoreInitialConnection()

        val (request, plan) = fixture.port.connectRequests.single()
        assertEquals(NaviampCoreConnectionRequest.Saved("source-1"), request)
        assertTrue(plan.restoreSavedSession)
        assertTrue(fixture.store.state.value.shell.connectionSettings.connection.connected)
    }

    @Test
    fun startupDoesNothingWithoutASavedConnection() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(hasSavedConnection = false)

        fixture.controller.restoreInitialConnection()

        assertTrue(fixture.port.connectRequests.isEmpty())
        assertFalse(fixture.store.state.value.shell.connectionSettings.connection.connected)
    }

    @Test
    fun unreachableSavedConnectionRestoresTheLocalShellAndOfflineContent() = kotlinx.coroutines.test.runTest {
        var offlineSourceId: String? = null
        val fixture = fixture(
            connectFailure = IllegalStateException("Failed to connect to server"),
            onOfflineRestored = { offlineSourceId = it },
        )

        fixture.controller.restoreInitialConnection()

        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(connection.connected)
        assertFalse(connection.statusIsError)
        assertEquals("Offline. Downloaded music remains available.", connection.status)
        assertEquals("source-1", fixture.store.state.value.shell.connectionSettings.currentSourceId)
        assertEquals("source-1", offlineSourceId)
    }

    @Test
    fun anAirplaneModeColdStartShowsOfflineContentWithoutWaitingForATimeout() = kotlinx.coroutines.test.runTest {
        var offlineSourceId: String? = null
        val fixture = fixture(
            network = { app.naviamp.domain.network.NetworkMetering.Disconnected },
            onOfflineRestored = { offlineSourceId = it },
            recoveryScope = backgroundScope,
        )

        fixture.controller.restoreInitialConnection()

        assertTrue(fixture.port.connectRequests.isEmpty(), "No attempt is made while the OS reports no network")
        assertEquals(0L, testScheduler.currentTime)
        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(connection.offline)
        assertEquals("source-1", offlineSourceId)
    }

    @Test
    fun whenTheNetworkReturnsTheOfflineSessionReconnectsWithinThePollBoundAndKeepsPlaying() =
        kotlinx.coroutines.test.runTest {
            var network = app.naviamp.domain.network.NetworkMetering.Disconnected
            val reconnected = mutableListOf<String>()
            var playbackRestores = 0
            var sourceResets = 0
            val fixture = fixture(
                network = { network },
                onConnected = { playbackRestores++ },
                onSourceChanging = { _, _ -> sourceResets++ },
                onReconnected = { reconnected += it },
                recoveryScope = backgroundScope,
            )
            fixture.controller.restoreInitialConnection()
            advanceTimeBy(60_000); runCurrent()
            assertTrue(fixture.port.connectRequests.isEmpty(), "Still no attempt while the network is missing")

            network = app.naviamp.domain.network.NetworkMetering.Unmetered
            advanceTimeBy(NaviampCoreReconnectSchedule().networkPollMillis + 1); runCurrent()

            val (request, plan) = fixture.port.connectRequests.single()
            assertEquals(NaviampCoreConnectionRequest.Saved("source-1"), request)
            assertTrue(plan.restoreSavedSession)
            assertFalse(plan.clearExistingPlayback, "Reconnecting never clears what is playing")
            val connection = fixture.store.state.value.shell.connectionSettings.connection
            assertTrue(connection.connected)
            assertFalse(connection.offline)
            assertEquals(listOf("source-1"), reconnected)
            assertEquals(0, playbackRestores, "A background reconnect does not restore the saved session over playback")
            assertEquals(0, sourceResets)
        }

    @Test
    fun withTheNetworkUpButTheServerDownItRetriesOnBackoffAndStaysOfflineMeanwhile() =
        kotlinx.coroutines.test.runTest {
            val fixture = fixture(
                network = { app.naviamp.domain.network.NetworkMetering.Unmetered },
                connectFailure = IllegalStateException("Failed to connect to server"),
                recoveryScope = backgroundScope,
            )
            fixture.controller.restoreInitialConnection()
            assertEquals(1, fixture.port.connectRequests.size)
            assertTrue(fixture.store.state.value.shell.connectionSettings.connection.offline)

            val schedule = NaviampCoreReconnectSchedule()
            advanceTimeBy(schedule.backoffMillis[0] - 1); runCurrent()
            assertEquals(1, fixture.port.connectRequests.size)
            advanceTimeBy(2); runCurrent()
            assertEquals(2, fixture.port.connectRequests.size, "First retry after the first backoff step")
            assertTrue(fixture.store.state.value.shell.connectionSettings.connection.offline)

            // The server comes back; the next retry, after the second step, connects.
            fixture.port.connectFailure = null
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            fixture.port.connectGate = gate
            advanceTimeBy(schedule.backoffMillis[1] + 1); runCurrent()
            assertEquals(3, fixture.port.connectRequests.size)
            assertTrue(fixture.store.state.value.shell.connectionSettings.connection.offline,
                "Offline browsing continues while the retry is in flight")
            gate.complete(Unit); runCurrent()
            assertFalse(fixture.store.state.value.shell.connectionSettings.connection.offline)
            assertTrue(fixture.store.state.value.shell.connectionSettings.connection.connected)

            advanceTimeBy(30 * 60_000L); runCurrent()
            assertEquals(3, fixture.port.connectRequests.size, "The loop ends once connected")
        }

    @Test
    fun recoveryWaitsForRuntimeServicesAndStartsAtOnceIfAlreadyOffline() = kotlinx.coroutines.test.runTest {
        var network = app.naviamp.domain.network.NetworkMetering.Disconnected
        val fixture = fixture(network = { network })
        fixture.controller.restoreInitialConnection()
        network = app.naviamp.domain.network.NetworkMetering.Unmetered
        advanceTimeBy(30 * 60_000L); runCurrent()
        assertTrue(fixture.port.connectRequests.isEmpty(), "No runtime loop before the host starts runtime services")

        fixture.controller.startRecovery(backgroundScope)
        advanceTimeBy(NaviampCoreReconnectSchedule().backoffMillis.first() + 1); runCurrent()
        assertEquals(1, fixture.port.connectRequests.size)
        assertFalse(fixture.store.state.value.shell.connectionSettings.connection.offline)
    }

    @Test
    fun aDatabaseResetDuringARetryCannotReviveTheDeletedSource() = kotlinx.coroutines.test.runTest {
        val fixture = offlineWithRetryInFlight()

        fixture.controller.resetAfterDatabaseClear()
        runCurrent()

        val settings = fixture.store.state.value.shell.connectionSettings
        assertTrue(settings.connection.savedConnections.isEmpty())
        assertFalse(settings.connection.offline, "The cancelled retry must not restore its pre-attempt offline state")
        assertEquals(null, settings.currentSourceId)
    }

    @Test
    fun turningOfflineModeOnDuringARetryCancelsItAndALateSuccessNeverCommits() = kotlinx.coroutines.test.runTest {
        val reconnected = mutableListOf<String>()
        // Read from the store like Core does; the loop reads it before the helper returns.
        var holder: ConnectionFixture? = null
        val fixture = offlineWithRetryInFlight(
            offlineModeEnabled = { holder?.store?.state?.value?.shell?.cache?.settings?.offlineModeEnabled == true },
            onReconnected = { reconnected += it },
        ).also { holder = it }

        fixture.store.updateShell { shell -> shell.copy(cache = shell.cache.copy(
            settings = shell.cache.settings.copy(offlineModeEnabled = true))) }
        runCurrent()
        fixture.port.connectGate?.complete(Unit)
        runCurrent()

        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(connection.offline, "Offline Mode keeps the session offline")
        assertTrue(reconnected.isEmpty(), "No library or download refresh against the user's choice")
        advanceTimeBy(30 * 60_000L); runCurrent()
        assertEquals(2, fixture.port.connectRequests.size, "No further attempts while Offline Mode is on")
    }

    @Test
    fun aRejectedAccountIsNotRetriedAndItsErrorIsShownWhileDownloadsStayAvailable() =
        kotlinx.coroutines.test.runTest {
            val fixture = fixture(
                network = { app.naviamp.domain.network.NetworkMetering.Unmetered },
                connectFailure = IllegalStateException("Failed to connect to server"),
                recoveryScope = backgroundScope,
            )
            fixture.controller.restoreInitialConnection()
            fixture.port.connectFailure = IllegalStateException("HTTP 401")

            advanceTimeBy(NaviampCoreReconnectSchedule().backoffMillis.first() + 1); runCurrent()
            assertEquals(2, fixture.port.connectRequests.size)
            advanceTimeBy(30 * 60_000L); runCurrent()
            assertEquals(2, fixture.port.connectRequests.size, "Rejected credentials are not retried")

            val connection = fixture.store.state.value.shell.connectionSettings.connection
            assertTrue(connection.offline, "Downloads stay available")
            assertTrue(connection.statusIsError, "The user is told why, so they can fix the account")
            assertFalse(connection.status.orEmpty().startsWith("Offline."))
        }

    @Test
    fun aBackgroundRetryLeavesVehicleSurfacesUsable() = kotlinx.coroutines.test.runTest {
        val fixture = offlineWithRetryInFlight()
        val connection = fixture.store.state.value.shell.connectionSettings.connection
        // NaviampVehicleDownloadsAccess and NaviampVehicleBrowseController treat these as "busy".
        assertTrue(connection.offline)
        assertFalse(connection.isConnecting)
        assertFalse(connection.restoringConnection)
    }

    @Test
    fun deletingTheSourceDuringARetryCannotReviveIt() = kotlinx.coroutines.test.runTest {
        val fixture = offlineWithRetryInFlight()

        fixture.controller.execute(NaviampCoreCommand.Connection.Delete(savedConnectionUi()))
        runCurrent()
        fixture.port.connectGate?.complete(Unit)
        advanceTimeBy(30 * 60_000L); runCurrent()

        val settings = fixture.store.state.value.shell.connectionSettings
        assertTrue(settings.connection.savedConnections.isEmpty())
        assertEquals(null, settings.currentSourceId, "The cancelled retry must not restore the deleted source")
        assertFalse(settings.connection.offline)
        assertEquals(2, fixture.port.connectRequests.size, "The deleted source is not retried")
    }

    @Test
    fun aTemporaryServerOutageKeepsRecoveringEvenWhenTheProviderCallsItAnAuthenticationFailure() =
        kotlinx.coroutines.test.runTest {
            val fixture = fixture(
                network = { app.naviamp.domain.network.NetworkMetering.Unmetered },
                connectFailure = IllegalStateException("Failed to connect to server"),
                recoveryScope = backgroundScope,
            )
            fixture.controller.restoreInitialConnection()
            fixture.port.connectFailure = IllegalStateException("Jellyfin authentication failed (HTTP 503)")
            val schedule = NaviampCoreReconnectSchedule()

            advanceTimeBy(schedule.backoffMillis[0] + 1); runCurrent()
            advanceTimeBy(schedule.backoffMillis[1] + 1); runCurrent()
            assertEquals(3, fixture.port.connectRequests.size, "A 503 is retried on the backoff, not treated as a rejection")
            val connection = fixture.store.state.value.shell.connectionSettings.connection
            assertTrue(connection.offline)
            assertFalse(connection.statusIsError)

            fixture.port.connectFailure = null
            advanceTimeBy(schedule.backoffMillis[2] + 1); runCurrent()
            assertEquals(4, fixture.port.connectRequests.size)
            assertFalse(fixture.store.state.value.shell.connectionSettings.connection.offline, "The server came back")
        }

    @Test
    fun aUserRequestedSwitchDuringARetryReplacesItAndIsShownAsConnecting() = kotlinx.coroutines.test.runTest {
        for (command in listOf(
            NaviampCoreCommand.Connection.SelectSavedSource("source-2"),
            NaviampCoreCommand.Connection.SwitchAccount(savedConnectionUi().copy(id = "source-2")),
        )) switchDuringRetry(command)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.switchDuringRetry(command: NaviampCoreCommand) {
        val fixture = offlineWithRetryInFlight(savedRecords = listOf(
            savedRecord(), savedRecord("source-2", "Studio Music", "https://studio.example")))

        val switch = async { fixture.controller.execute(command) }
        runCurrent()

        assertEquals(NaviampCoreConnectionRequest.Saved("source-2"), fixture.port.connectRequests.last().first,
            "The switch is not dropped behind the background retry")
        val during = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(during.isConnecting, "A switch the user asked for is a foreground attempt")
        assertFalse(during.offline)

        fixture.port.connectGate?.complete(Unit)
        runCurrent()
        switch.await()
        val settings = fixture.store.state.value.shell.connectionSettings
        assertEquals("source-2", settings.currentSourceId)
        assertFalse(settings.connection.offline)
        advanceTimeBy(30 * 60_000L); runCurrent()
        assertEquals(3, fixture.port.connectRequests.size, "The superseded retry does not run again")
    }

    @Test
    fun cancellingAnAccountSwitchFromOfflineResumesRecoveryOfTheOriginalSource() = kotlinx.coroutines.test.runTest {
        val fixture = offlineWithRetryInFlight(savedRecords = listOf(
            savedRecord(), savedRecord("source-2", "Studio Music", "https://studio.example")))
        fixture.controller.dispatch(NaviampCoreCommand.Connection.OpenAccounts)
        val switch = launch {
            fixture.controller.execute(NaviampCoreCommand.Connection.SwitchAccount(savedConnectionUi().copy(id = "source-2")))
        }
        runCurrent()
        assertEquals(NaviampCoreConnectionRequest.Saved("source-2"), fixture.port.connectRequests.last().first)

        // Back cancels the switch; the original source is restored to offline.
        fixture.controller.dispatch(NaviampCoreCommand.Connection.CloseAccounts)
        switch.join()
        assertTrue(switch.isCancelled)
        val restored = fixture.store.state.value.shell.connectionSettings
        assertTrue(restored.connection.offline)
        assertEquals("source-1", restored.currentSourceId)

        // The original server comes back; recovery must still be running to notice.
        fixture.port.connectGate = null
        val requestsBefore = fixture.port.connectRequests.size
        advanceTimeBy(NaviampCoreReconnectSchedule().backoffMillis.first() + 1); runCurrent()
        assertEquals(NaviampCoreConnectionRequest.Saved("source-1"), fixture.port.connectRequests.drop(requestsBefore).single().first)
        assertFalse(fixture.store.state.value.shell.connectionSettings.connection.offline)
    }

    /** An offline-restored session whose first background retry is suspended inside the provider. */
    private suspend fun kotlinx.coroutines.test.TestScope.offlineWithRetryInFlight(
        offlineModeEnabled: () -> Boolean = { false },
        onReconnected: (String) -> Unit = {},
        savedRecords: List<NaviampCoreSavedConnectionRecord> = listOf(savedRecord()),
    ): ConnectionFixture {
        val fixture = fixture(
            network = { app.naviamp.domain.network.NetworkMetering.Unmetered },
            connectFailure = IllegalStateException("Failed to connect to server"),
            offlineModeEnabled = offlineModeEnabled,
            onReconnected = onReconnected,
            recoveryScope = backgroundScope,
            savedRecords = savedRecords,
        )
        fixture.controller.restoreInitialConnection()
        fixture.port.connectFailure = null
        fixture.port.connectGate = CompletableDeferred()
        advanceTimeBy(NaviampCoreReconnectSchedule().backoffMillis.first() + 1); runCurrent()
        assertEquals(2, fixture.port.connectRequests.size, "The retry is in flight")
        return fixture
    }

    @Test
    fun theBackoffIsCappedSoAnUnreachableServerIsStillRetried() {
        val schedule = NaviampCoreReconnectSchedule()
        assertEquals(schedule.backoffMillis.last(), schedule.delayAfter(50))
        assertTrue(schedule.backoffMillis.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test
    fun offlineModeStartsOfflineAndNeverReconnectsUntilTurnedOff() = kotlinx.coroutines.test.runTest {
        var offlineMode = true
        val fixture = fixture(
            network = { app.naviamp.domain.network.NetworkMetering.Unmetered },
            offlineModeEnabled = { offlineMode },
            recoveryScope = backgroundScope,
        )
        fixture.controller.restoreInitialConnection()
        advanceTimeBy(30 * 60_000L); runCurrent()
        assertTrue(fixture.port.connectRequests.isEmpty())
        assertTrue(fixture.store.state.value.shell.connectionSettings.connection.offline)

        offlineMode = false
        // Noticed within one poll, then the first backoff step.
        val schedule = NaviampCoreReconnectSchedule()
        advanceTimeBy(schedule.networkPollMillis + schedule.backoffMillis.first() + 1); runCurrent()
        assertEquals(1, fixture.port.connectRequests.size, "Turning Offline Mode off resumes reconnecting")
    }

    @Test
    fun aReachableServerStillStartsStraightOnlineWithNoBackgroundRetries() = kotlinx.coroutines.test.runTest {
        var connected = 0
        val fixture = fixture(
            network = { app.naviamp.domain.network.NetworkMetering.Unmetered },
            onConnected = { connected++ },
            recoveryScope = backgroundScope,
        )
        fixture.controller.restoreInitialConnection()
        advanceTimeBy(30 * 60_000L); runCurrent()
        assertEquals(1, fixture.port.connectRequests.size)
        assertEquals(1, connected, "A normal start still restores the saved session")
        assertFalse(fixture.store.state.value.shell.connectionSettings.connection.offline)
    }

    @Test
    fun unreachableSavedSourceSwitchMakesTheSelectedOfflineSourceAuthoritative() =
        kotlinx.coroutines.test.runTest {
            val records = listOf(
                savedRecord(),
                savedRecord("source-2", "Studio Music", "https://studio.example"),
            )
            val transitions = mutableListOf<Pair<String?, String>>()
            var offlineSourceId: String? = null
            val fixture = fixture(
                connectFailure = IllegalStateException("Failed to connect to server"),
                savedRecords = records,
                onSourceChanging = { previous, next -> transitions += previous to next },
                onOfflineRestored = { offlineSourceId = it },
            )

            fixture.controller.execute(
                NaviampCoreCommand.Connection.ConnectSaved(
                    NaviampSavedConnectionUi(
                        id = "source-2",
                        displayName = "Studio Music",
                        serverUrl = "https://studio.example",
                        username = "demo",
                    ),
                ),
            )

            val settings = fixture.store.state.value.shell.connectionSettings
            assertEquals("source-2", settings.currentSourceId)
            assertEquals(listOf(false, true), settings.connection.savedConnections.map { it.current })
            assertEquals(listOf<Pair<String?, String>>("source-1" to "source-2"), transitions)
            assertEquals("source-2", offlineSourceId)
            assertEquals("Offline. Downloaded music remains available.", settings.connection.status)
        }

    @Test
    fun authenticationFailureDoesNotEnterOfflineMode() = kotlinx.coroutines.test.runTest {
        var offlineRestorations = 0
        val fixture = fixture(
            connectFailure = IllegalStateException("HTTP 401"),
            onOfflineRestored = { offlineRestorations += 1 },
        )

        fixture.controller.restoreInitialConnection()

        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertFalse(connection.connected)
        assertTrue(connection.statusIsError)
        assertEquals(0, offlineRestorations)
    }

    @Test
    fun failuresBecomeCommonConnectionStateInsteadOfHostMessages() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(connectFailure = IllegalStateException("Server unavailable"))
        fixture.controller.dispatch(
            NaviampCoreCommand.Connection.ChangeForm(
                ConnectionFormState(serverUrl = "https://music.example", username = "demo", password = "secret"),
            ),
        )

        fixture.controller.execute(NaviampCoreCommand.Connection.Connect)

        val state = fixture.store.state.value.shell.connectionSettings.connection
        assertFalse(state.connected)
        assertFalse(state.isConnecting)
        assertTrue(state.statusIsError)
        assertEquals("Server unavailable", state.status)
    }

    @Test
    fun validatesNewConnectionsBeforeInvokingAHostAndRetainsEditIdentityForCredentialReuse() =
        kotlinx.coroutines.test.runTest {
            val fixture = fixture()

            fixture.controller.execute(NaviampCoreCommand.Connection.Connect)

            assertTrue(fixture.port.connectRequests.isEmpty())
            assertEquals(
                "Enter a server URL and username.",
                fixture.store.state.value.shell.connectionSettings.connection.status,
            )
            assertTrue(fixture.store.state.value.shell.connectionSettings.connection.statusIsError)

            val saved = savedConnectionUi()
            fixture.controller.execute(NaviampCoreCommand.Connection.Edit(saved))
            fixture.controller.execute(NaviampCoreCommand.Connection.Connect)

            assertEquals(
                NaviampCoreConnectionRequest.Form(
                    ConnectionFormState(serverUrl = "https://edited.example", username = "demo"),
                    savedConnectionId = "source-1",
                ),
                fixture.port.connectRequests.single().first,
            )
        }

    @Test
    fun importedConnectionsReconnectTheImportedProfileInsteadOfAddingADuplicate() =
        kotlinx.coroutines.test.runTest {
            val fixture = fixture()
            val imported = ConnectionFormState(serverUrl = "https://imported.example", username = "demo")

            fixture.controller.editImportedConnection("source-1", imported)
            val editing = fixture.store.state.value.shell.connectionSettings.connection
            assertTrue(editing.editingConnection)
            assertTrue(editing.editingSavedConnection, "Import must offer Reconnect for the imported profile")
            assertFalse(editing.saveWithoutReconnect)
            assertEquals(imported, editing.form)

            fixture.controller.execute(NaviampCoreCommand.Connection.Connect)
            assertEquals(
                NaviampCoreConnectionRequest.Form(imported, savedConnectionId = "source-1"),
                fixture.port.connectRequests.single().first,
            )
        }

    @Test
    fun editAndDeleteFlowsAreCoreTransactions() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        val saved = savedConnectionUi()

        fixture.controller.execute(NaviampCoreCommand.Connection.Edit(saved))
        val editing = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(editing.editingConnection)
        assertTrue(editing.editingSavedConnection)
        assertEquals("https://edited.example", editing.form.serverUrl)

        fixture.controller.execute(NaviampCoreCommand.Connection.Delete(saved))
        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertEquals(emptyList<NaviampSavedConnectionUi>(), connection.savedConnections)
        assertEquals("Deleted Home Music.", connection.status)
    }

    @Test
    fun databaseResetDropsTheLiveSessionAndPublishesAnEmptyDisconnectedInventory() =
        kotlinx.coroutines.test.runTest {
            val fixture = fixture()

            fixture.controller.resetAfterDatabaseClear()

            val settings = fixture.store.state.value.shell.connectionSettings
            assertTrue(fixture.port.activeSessionCleared)
            assertEquals(null, settings.currentSourceId)
            assertTrue(settings.connection.savedConnections.isEmpty())
            assertFalse(settings.connection.connected)
            assertEquals("Database reset.", settings.connection.status)
        }

    @Test
    fun musicFolderFailuresRemainVisibleWhileCoreKeepsTheConnectionEditable() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(musicFoldersLoadFailed = true)

        fixture.controller.execute(NaviampCoreCommand.Connection.Edit(savedConnectionUi()))

        val connection = fixture.store.state.value.shell.connectionSettings.connection
        assertTrue(connection.editingConnection)
        assertEquals(emptyList(), connection.availableMusicFolders)
        assertEquals(
            "Could not load music folders. You can still edit the connection.",
            connection.musicFoldersStatus,
        )
    }

    @Test
    fun provisioningPasswordValidationKeepsPlaybackAndUsesTheExistingSource() = kotlinx.coroutines.test.runTest {
        val changed = mutableListOf<String>()
        val fixture = fixture(onSourceChanging = { _, id -> changed += id })
        assertTrue(fixture.controller.updateProvisioningCredential("source-1", "replacement"))
        val (request, plan) = fixture.port.connectRequests.single()
        val form = request as NaviampCoreConnectionRequest.Form
        assertEquals("source-1", form.savedConnectionId)
        assertEquals("replacement", form.form.password)
        assertFalse(plan.clearExistingPlayback)
        assertFalse(plan.clearProviderData)
        assertTrue(changed.isEmpty())
        assertTrue(fixture.store.state.value.shell.connectionSettings.connection.connected)
        assertEquals("", fixture.store.state.value.shell.connectionSettings.connection.form.password)
    }

    @Test
    fun invalidOrCancelledProvisioningPasswordRestoresConnectionState() = kotlinx.coroutines.test.runTest {
        for (failure in listOf(IllegalArgumentException("invalid password"), kotlinx.coroutines.CancellationException())) {
            val fixture = fixture(connectFailure = failure)
            val before = fixture.store.state.value.shell.connectionSettings
            try {
                assertFalse(fixture.controller.updateProvisioningCredential("source-1", "replacement"))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                assertTrue(failure is kotlinx.coroutines.CancellationException)
            }
            assertEquals(before, fixture.store.state.value.shell.connectionSettings)
        }
    }

    @Test
    fun provisioningPasswordCannotChangeAnotherSourceOrSubmitBlankCredentials() = kotlinx.coroutines.test.runTest {
        val fixture = fixture()
        assertFalse(fixture.controller.updateProvisioningCredential("other-source", "replacement"))
        assertFalse(fixture.controller.updateProvisioningCredential("source-1", "  "))
        assertTrue(fixture.port.connectRequests.isEmpty())
    }

    @Test
    fun librarySourcePickerPublishesNamedChoicesAndDropsStaleIds() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(
            selectedMusicFolderIds = listOf("2", "stale-id"),
            availableMusicFolders = listOf(
                ConnectionFormMusicFolder("1", "Music", defaultSelected = true),
                ConnectionFormMusicFolder("2", "Classical"),
            ),
        )

        fixture.controller.execute(NaviampCoreCommand.Library.OpenSources)

        val picker = fixture.store.state.value.shell.library.sourcePicker
        assertTrue(picker.visible)
        assertEquals(listOf("Music", "Classical"), picker.libraries.map { it.name })
        assertEquals(listOf("2"), picker.selectedIds)
        assertFalse(picker.loading)
    }

    @Test
    fun savingLibrarySourcesReconnectsSilentlyAndRefreshesTheCurrentSource() = kotlinx.coroutines.test.runTest {
        val transitions = mutableListOf<Pair<String?, String>>()
        var connectedNotifications = 0
        val fixture = fixture(
            selectedMusicFolderIds = listOf("1"),
            availableMusicFolders = listOf(
                ConnectionFormMusicFolder("1", "Music", defaultSelected = true),
                ConnectionFormMusicFolder("2", "Classical"),
            ),
            onSourceChanging = { previous, next -> transitions += previous to next },
            onConnected = { connectedNotifications += 1 },
        )
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.port.connectRequests.clear()
        transitions.clear()
        connectedNotifications = 0
        fixture.controller.execute(NaviampCoreCommand.Library.OpenSources)
        fixture.controller.dispatch(NaviampCoreCommand.Library.ToggleSource("2"))

        fixture.controller.execute(NaviampCoreCommand.Library.SaveSources)

        val (request, plan) = fixture.port.connectRequests.single()
        assertEquals(listOf("1", "2"), (request as NaviampCoreConnectionRequest.Form).form.selectedMusicFolderIds)
        assertEquals("source-1", request.savedConnectionId)
        assertTrue(plan.restoreSavedSession)
        assertEquals(listOf<Pair<String?, String>>("source-1" to "source-1"), transitions)
        assertEquals(1, connectedNotifications)
        assertFalse(fixture.store.state.value.shell.library.sourcePicker.visible)
    }

    @Test
    fun cancellingLibrarySourcesDoesNotReconnect() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(availableMusicFolders = listOf(ConnectionFormMusicFolder("1", "Music")))
        fixture.controller.execute(NaviampCoreCommand.Library.OpenSources)

        fixture.controller.dispatch(NaviampCoreCommand.Library.CancelSources)

        assertFalse(fixture.store.state.value.shell.library.sourcePicker.visible)
        assertTrue(fixture.port.connectRequests.isEmpty())
    }

    @Test
    fun librarySourcePickerExplainsUnavailableAndEmptyStates() = kotlinx.coroutines.test.runTest {
        val disconnected = fixture(hasSavedConnection = false, currentSourceId = null)
        disconnected.controller.execute(NaviampCoreCommand.Library.OpenSources)
        assertEquals(
            NaviampLibrarySourcePickerError.ConnectionRequired,
            disconnected.store.state.value.shell.library.sourcePicker.errorKind,
        )

        val unsupported = fixture(availableMusicFolders = emptyList())
        unsupported.controller.execute(NaviampCoreCommand.Library.OpenSources)
        val picker = unsupported.store.state.value.shell.library.sourcePicker
        assertTrue(picker.visible)
        assertTrue(picker.libraries.isEmpty())
        assertEquals(null, picker.errorKind)
    }

    @Test
    fun librarySourcePickerKeepsTheOnlyAvailableLibrarySelected() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(availableMusicFolders = listOf(ConnectionFormMusicFolder("1", "Music")))
        fixture.controller.execute(NaviampCoreCommand.Library.OpenSources)

        fixture.controller.dispatch(NaviampCoreCommand.Library.ToggleSource("1"))
        fixture.controller.dispatch(NaviampCoreCommand.Library.ToggleSource("unknown"))

        assertEquals(listOf("1"), fixture.store.state.value.shell.library.sourcePicker.selectedIds)
    }

    @Test
    fun failedLibrarySourceSaveKeepsTheConnectedSessionAndPickerOpen() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(availableMusicFolders = listOf(ConnectionFormMusicFolder("1", "Music")))
        fixture.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        fixture.port.connectRequests.clear()
        fixture.controller.execute(NaviampCoreCommand.Library.OpenSources)
        fixture.port.connectFailure = IllegalStateException("provider rejected selection")

        fixture.controller.execute(NaviampCoreCommand.Library.SaveSources)

        val picker = fixture.store.state.value.shell.library.sourcePicker
        assertTrue(picker.visible)
        assertFalse(picker.saving)
        assertEquals(NaviampLibrarySourcePickerError.SaveFailed, picker.errorKind)
        assertTrue(fixture.store.state.value.shell.connectionSettings.connection.connected)
        assertEquals("source-1", fixture.store.state.value.shell.connectionSettings.currentSourceId)
    }

    @Test fun inPlaceSelectionUsesStoredCredentialsAndPreservesPhoneEditorAndAccountState() = kotlinx.coroutines.test.runTest {
        val callbacks = mutableListOf<Pair<String, Boolean>>()
        var home = 0
        val f = fixture(savedRecords = listOf(savedRecord(), savedRecord(id = "source-2")),
            onUserConnected = { home++ }, onSavedSourceSelected = { id, changed -> callbacks += id to changed })
        f.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        f.controller.execute(NaviampCoreCommand.Connection.EditCurrent)
        f.controller.dispatch(NaviampCoreCommand.Connection.OpenAccounts)
        val before = f.store.state.value.shell.connectionSettings
        home = 0; f.port.connectRequests.clear()
        assertEquals(NaviampCoreCommandResult.SavedSourceSelected(true), f.controller.execute(NaviampCoreCommand.Connection.SelectSavedSource("source-2")))
        val after = f.store.state.value.shell.connectionSettings
        assertEquals("source-2", after.currentSourceId)
        assertEquals(before.connection.form, after.connection.form)
        assertEquals(before.connection.editingConnection, after.connection.editingConnection)
        assertEquals(before.accountSwitcher, after.accountSwitcher)
        assertEquals(0, home); assertEquals(listOf("source-2" to true), callbacks)
        assertEquals(NaviampCoreConnectionRequest.Saved("source-2"), f.port.connectRequests.single().first)
        assertFalse(f.port.connectRequests.single().second.clearProviderData)
    }
    @Test fun failedInPlaceSelectionKeepsCurrentSessionAndDoesNotOpenPhoneFailureUi() = kotlinx.coroutines.test.runTest {
        var changes = 0
        val f = fixture(savedRecords = listOf(savedRecord(), savedRecord(id = "source-2")), onSourceChanging = { _, _ -> changes++ })
        f.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        val before = f.store.state.value.shell.connectionSettings
        changes = 0; f.port.connectFailure = IllegalStateException("expired credential")
        assertEquals(NaviampCoreCommandResult.SavedSourceSelected(false), f.controller.execute(NaviampCoreCommand.Connection.SelectSavedSource("source-2")))
        assertEquals(before, f.store.state.value.shell.connectionSettings)
        assertEquals("source-1", f.port.inventory.currentSourceId); assertEquals(0, changes)
    }
    @Test fun inPlaceSelectionRejectsMissingBusyAndStaleRecoveryAndKeepsCurrentSelectionIdempotent() = kotlinx.coroutines.test.runTest {
        val f = fixture(savedRecords = listOf(savedRecord(), savedRecord(id = "source-2")))
        f.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi())); f.port.connectRequests.clear()
        assertEquals(NaviampCoreCommandResult.SavedSourceSelected(true), f.controller.execute(NaviampCoreCommand.Connection.SelectSavedSource("source-1")))
        for (request in listOf(NaviampCoreCommand.Connection.SelectSavedSource("missing"), NaviampCoreCommand.Connection.SelectSavedSource("source-2", reconnect = true)))
            assertEquals(NaviampCoreCommandResult.SavedSourceSelected(false), f.controller.execute(request))
        assertTrue(f.port.connectRequests.isEmpty())
        f.store.updateShell { it.copy(connect = it.connect.copy(remotePlaybackAuthorityActive = true)) }
        assertEquals(NaviampCoreCommandResult.SavedSourceSelected(false), f.controller.execute(NaviampCoreCommand.Connection.SelectSavedSource("source-2")))
        assertTrue(f.port.connectRequests.isEmpty())
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun cancelledInPlaceSelectionRestoresConnectionAndDoesNotSelectTarget() = kotlinx.coroutines.test.runTest {
        val f = fixture(savedRecords = listOf(savedRecord(), savedRecord(id = "source-2")))
        f.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        val before = f.store.state.value.shell.connectionSettings
        f.port.connectGate = CompletableDeferred()
        val job = launch { f.controller.execute(NaviampCoreCommand.Connection.SelectSavedSource("source-2")) }
        runCurrent()
        assertEquals(NaviampCoreCommandResult.SavedSourceSelected(false), f.controller.execute(NaviampCoreCommand.Connection.SelectSavedSource("source-1", reconnect = true)))
        job.cancel(); job.join()
        assertEquals(before, f.store.state.value.shell.connectionSettings)
        assertEquals("source-1", f.port.inventory.currentSourceId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun inPlaceSelectionTimeoutRestoresThePreviousSession() = kotlinx.coroutines.test.runTest {
        val f = fixture(savedRecords = listOf(savedRecord(), savedRecord(id = "source-2")))
        f.controller.execute(NaviampCoreCommand.Connection.ConnectSaved(savedConnectionUi()))
        val before = f.store.state.value.shell.connectionSettings
        f.port.connectGate = CompletableDeferred()
        var result: NaviampCoreCommandResult? = null
        val job = launch { result = f.controller.execute(NaviampCoreCommand.Connection.SelectSavedSource("source-2")) }
        runCurrent(); testScheduler.advanceTimeBy(30_001); runCurrent(); job.join()
        assertEquals(NaviampCoreCommandResult.SavedSourceSelected(false), result)
        assertEquals(before, f.store.state.value.shell.connectionSettings)
        assertEquals("source-1", f.port.inventory.currentSourceId)
    }

    private fun fixture(
        connectFailure: Throwable? = null,
        musicFoldersLoadFailed: Boolean = false,
        onConnected: (String) -> Unit = {},
        onSavedSourceSelected: (String, Boolean) -> Unit = { id, _ -> onConnected(id) },
        onCompanionChanged: (String) -> Unit = {},
        onUserConnected: (String) -> Unit = {},
        onOfflineRestored: (String) -> Unit = {},
        onSourceChanging: (String?, String) -> Unit = { _, _ -> },
        currentSourceId: String? = "source-1",
        hasSavedConnection: Boolean = true,
        savedRecords: List<NaviampCoreSavedConnectionRecord> = listOf(savedRecord()),
        selectedMusicFolderIds: List<String> = emptyList(),
        availableMusicFolders: List<ConnectionFormMusicFolder> = emptyList(),
        certificateFiles: NaviampCoreCertificateFilePort? = null,
        network: () -> app.naviamp.domain.network.NetworkMetering? = { null },
        offlineModeEnabled: () -> Boolean = { false },
        onReconnected: (String) -> Unit = {},
        recoveryScope: kotlinx.coroutines.CoroutineScope? = null,
    ): ConnectionFixture {
        val inventory = NaviampCoreConnectionInventory(
            connections = savedRecords.takeIf { hasSavedConnection }.orEmpty(),
            currentSourceId = currentSourceId?.takeIf { hasSavedConnection },
        )
        val port = FakeProviderSessionPort(
            inventory,
            connectFailure,
            musicFoldersLoadFailed,
            selectedMusicFolderIds,
            availableMusicFolders,
        )
        val store = NaviampCoreStateStore()
        return ConnectionFixture(
            store = store,
            port = port,
            controller = NaviampCoreConnectionController(
                connection = NaviampConnectionController(),
                stateStore = store,
                sessionPort = port,
                initialInventory = inventory,
                onSourceChanging = onSourceChanging,
                onConnected = onConnected,
                onSavedSourceSelected = onSavedSourceSelected,
                onCompanionChanged = onCompanionChanged,
                onUserConnected = onUserConnected,
                onOfflineRestored = onOfflineRestored,
                certificateFiles = certificateFiles,
                networkMetering = app.naviamp.domain.network.NetworkMeteringSource {
                    network() ?: app.naviamp.domain.network.NetworkMetering.Unknown
                }.takeIf { network() != null },
                offlineModeEnabled = offlineModeEnabled,
                onReconnected = onReconnected,
                recoveryScope = recoveryScope,
            ),
        )
    }

    private fun savedRecord(
        id: String = "source-1",
        displayName: String = "Home Music",
        serverUrl: String = "https://music.example",
    ) = NaviampCoreSavedConnectionRecord(
        id = id,
        displayName = displayName,
        serverUrl = serverUrl,
        username = "demo",
    )

    private fun savedConnectionUi() = NaviampSavedConnectionUi(
        id = "source-1",
        displayName = "Home Music",
        serverUrl = "https://music.example",
        username = "demo",
    )
}

private data class ConnectionFixture(
    val store: NaviampCoreStateStore,
    val port: FakeProviderSessionPort,
    val controller: NaviampCoreConnectionController,
)

private class FakeProviderSessionPort(
    initialInventory: NaviampCoreConnectionInventory,
    var connectFailure: Throwable?,
    private val musicFoldersLoadFailed: Boolean,
    private val selectedMusicFolderIds: List<String>,
    private val availableMusicFolders: List<ConnectionFormMusicFolder>,
) : NaviampCoreProviderSessionPort {
    override fun initialInventory() = inventory
    var inventory = initialInventory
    var editableCompanion: app.naviamp.domain.source.AurralCompanion? = null
    var companionSaveFailure: Throwable? = null
    val savedCompanions = mutableListOf<Pair<String, app.naviamp.domain.source.AurralCompanion>>()
    override suspend fun saveAurralCompanion(sourceId: String, companion: app.naviamp.domain.source.AurralCompanion) {
        companionSaveFailure?.let { throw it }
        savedCompanions += sourceId to companion
    }
    var companionVerification = app.naviamp.domain.provider.ProviderDiscoveryStatus.Ready
    val verifiedCompanions = mutableListOf<app.naviamp.domain.source.AurralCompanion>()
    override suspend fun verifyAurralCompanion(companion: app.naviamp.domain.source.AurralCompanion): app.naviamp.domain.provider.ProviderDiscoveryStatus {
        verifiedCompanions += companion
        return companionVerification
    }
    var refreshCalls = 0
    var activeSessionCleared = false
    var connectGate: CompletableDeferred<Unit>? = null
    val connectRequests = mutableListOf<Pair<NaviampCoreConnectionRequest, NaviampConnectionAttemptPlan>>()

    override suspend fun connect(
        request: NaviampCoreConnectionRequest,
        plan: NaviampConnectionAttemptPlan,
    ): NaviampCoreConnectedSession {
        connectRequests += request to plan
        connectGate?.await()
        connectFailure?.let { throw it }
        val sourceId = (request as? NaviampCoreConnectionRequest.Saved)?.id ?: "source-1"
        val saved = inventory.connections.firstOrNull { it.id == sourceId }
        val requestedSelection = (request as? NaviampCoreConnectionRequest.Form)?.form?.selectedMusicFolderIds
        inventory = inventory.copy(
            currentSourceId = sourceId,
            connections = inventory.connections.map { saved ->
                if (saved.id == sourceId && requestedSelection != null) {
                    saved.copy(selectedMusicFolderIds = requestedSelection)
                } else saved
            },
        )
        return NaviampCoreConnectedSession(
            sourceId = sourceId,
            displayName = saved?.displayName ?: "Home Music",
            serverVersion = "1.2.3",
            inventory = inventory,
        )
    }

    override suspend fun editableConnection(id: String) = NaviampCoreEditableConnection(
        form = ConnectionFormState(
            aurralCompanion = editableCompanion,
            serverUrl = "https://edited.example",
            username = "demo",
            selectedMusicFolderIds = selectedMusicFolderIds,
        ),
        availableMusicFolders = availableMusicFolders,
        musicFoldersLoadFailed = musicFoldersLoadFailed,
    )

    override suspend fun deleteConnection(id: String): NaviampCoreConnectionInventory {
        inventory = NaviampCoreConnectionInventory()
        return inventory
    }

    override suspend fun refreshActiveSession(): Boolean {
        refreshCalls += 1
        return true
    }
    override suspend fun smartPlaylistProvider(password: String?) = null
    override suspend fun persistActiveSession() = Unit
    override suspend fun clearActiveSession() {
        activeSessionCleared = true
    }
}
