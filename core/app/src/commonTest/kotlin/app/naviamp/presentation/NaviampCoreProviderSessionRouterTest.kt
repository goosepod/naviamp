package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.app.RecordingProvider
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.ConnectionFormState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class NaviampCoreProviderSessionRouterTest {
    @Test
    fun restoresTheActiveSourceFromANonFirstProviderRoute() {
        val base = inventory()
        val subsonic = RecordingSessionPort(base)
        val jellyfin = RecordingSessionPort(base.copy(currentSourceId = "jellyfin-source"))

        val router = router(subsonic, jellyfin)

        assertSame(jellyfin.provider, router.currentProvider())
        assertEquals("jellyfin-source", router.currentSourceId())
        assertEquals("jellyfin-source", router.initialInventory().currentSourceId)
    }

    @Test
    fun routesNewAndSavedConnectionsByProviderIdentity() = runTest {
        val inventory = inventory()
        val subsonic = RecordingSessionPort(inventory)
        val jellyfin = RecordingSessionPort(inventory)
        val router = router(subsonic, jellyfin)

        router.connect(
            NaviampCoreConnectionRequest.Form(
                ConnectionFormState(
                    providerId = "subsonic",
                    serverUrl = "https://subsonic.example",
                    username = "demo",
                    password = "secret",
                ),
            ),
            plan(),
        )
        router.connect(NaviampCoreConnectionRequest.Saved("jellyfin-source"), plan())

        assertEquals(listOf("form:subsonic"), subsonic.connections)
        assertEquals(listOf("saved:jellyfin-source"), jellyfin.connections)
        assertSame(jellyfin.provider, router.currentProvider())
        assertSame(jellyfin.provider, router.providerSource.current())
        assertEquals("jellyfin-source", router.currentSourceId())
    }

    @Test
    fun delegatesActiveSessionLifecycleOnlyToTheSelectedRoute() = runTest {
        val inventory = inventory()
        val subsonic = RecordingSessionPort(inventory)
        val jellyfin = RecordingSessionPort(inventory)
        val router = router(subsonic, jellyfin)
        router.connect(NaviampCoreConnectionRequest.Saved("subsonic-source"), plan())

        router.refreshActiveSession()
        router.persistActiveSession()
        router.smartPlaylistProvider("secret")

        assertEquals(1, subsonic.refreshCalls)
        assertEquals(1, subsonic.persistCalls)
        assertEquals(listOf<String?>("secret"), subsonic.smartPlaylistPasswords)
        assertEquals(0, jellyfin.refreshCalls)
        assertEquals(0, jellyfin.persistCalls)
    }

    @Test
    fun rejectsUnregisteredProviderWithoutFallingBackToAnotherProtocol() = runTest {
        val inventory = inventory().copy(
            connections = inventory().connections + NaviampCoreSavedConnectionRecord(
                id = "future-source",
                displayName = "Future",
                serverUrl = "https://future.example",
                username = "demo",
                providerId = "future",
            ),
        )
        val router = router(RecordingSessionPort(inventory), RecordingSessionPort(inventory))

        val failure = assertFailsWith<IllegalStateException> {
            router.connect(NaviampCoreConnectionRequest.Saved("future-source"), plan())
        }

        assertEquals("future support is not available yet.", failure.message)
    }

    private fun router(
        subsonic: RecordingSessionPort,
        jellyfin: RecordingSessionPort,
    ) = NaviampCoreProviderSessionRouter(
        listOf(
            NaviampCoreProviderSessionRoute(setOf("navidrome", "subsonic"), subsonic),
            NaviampCoreProviderSessionRoute(setOf("jellyfin"), jellyfin),
        ),
    )

    private fun inventory() = NaviampCoreConnectionInventory(
        connections = listOf(
            NaviampCoreSavedConnectionRecord(
                id = "subsonic-source",
                displayName = "Subsonic",
                serverUrl = "https://subsonic.example",
                username = "demo",
                providerId = "subsonic",
            ),
            NaviampCoreSavedConnectionRecord(
                id = "jellyfin-source",
                displayName = "Jellyfin",
                serverUrl = "https://jellyfin.example",
                username = "demo",
                providerId = "jellyfin",
            ),
        ),
    )

    private fun plan() = NaviampConnectionAttemptPlan(
        restoreSavedSession = false,
        clearExistingPlayback = false,
        clearProviderData = false,
        runFullLibraryRefresh = false,
    )
}

private class RecordingSessionPort(
    private var inventory: NaviampCoreConnectionInventory,
) : NaviampCoreProviderSessionPort {
    val provider = RecordingProvider(failReports = false)
    val connections = mutableListOf<String>()
    var refreshCalls = 0
    var persistCalls = 0
    val smartPlaylistPasswords = mutableListOf<String?>()

    override fun currentProvider(): MediaProvider = provider
    override fun initialInventory(): NaviampCoreConnectionInventory = inventory

    override suspend fun connect(
        request: NaviampCoreConnectionRequest,
        plan: NaviampConnectionAttemptPlan,
    ): NaviampCoreConnectedSession {
        val sourceId = when (request) {
            is NaviampCoreConnectionRequest.Form -> {
                connections += "form:${request.form.providerId}"
                "${request.form.providerId}-source"
            }
            is NaviampCoreConnectionRequest.Saved -> {
                connections += "saved:${request.id}"
                request.id
            }
        }
        inventory = inventory.copy(currentSourceId = sourceId)
        return NaviampCoreConnectedSession(sourceId, sourceId, inventory = inventory)
    }

    override suspend fun editableConnection(id: String): NaviampCoreEditableConnection =
        NaviampCoreEditableConnection(ConnectionFormState())

    override suspend fun deleteConnection(id: String): NaviampCoreConnectionInventory {
        inventory = inventory.copy(
            connections = inventory.connections.filterNot { it.id == id },
            currentSourceId = inventory.currentSourceId.takeUnless { it == id },
        )
        return inventory
    }

    override suspend fun smartPlaylistProvider(password: String?): MediaProvider {
        smartPlaylistPasswords += password
        return provider
    }

    override suspend fun refreshActiveSession(): Boolean {
        refreshCalls += 1
        return true
    }

    override suspend fun persistActiveSession() {
        persistCalls += 1
    }

    override suspend fun clearActiveSession() = Unit
}
