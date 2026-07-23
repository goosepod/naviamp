package app.naviamp.desktop

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.presentation.NaviampCoreConnectionRequest
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeMusicFolder
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopCoreProviderSessionPortTest {
    @Test
    fun activeSessionSuppliesSmartPlaylistProviderAndPersistsItsNativeToken() = runTest {
        val repository = TestMediaSourceRepository(savedSource())
        val port = DesktopCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = DesktopNavidromeSessionOpener { request, _ ->
                session(request.savedConnectionForLogin ?: error("saved credentials missing"))
            },
        )
        port.connect(
            NaviampCoreConnectionRequest.Saved("source-1"),
            NaviampConnectionAttemptPlan(true, false, false, false),
        )

        assertSame(port.currentProvider(), port.smartPlaylistProvider(null))
        port.persistActiveSession()

        assertEquals("native-token", repository.lastPersisted?.nativeToken)
    }

    @Test
    fun editedConnectionReusesProtectedCredentialsFromTheExplicitCoreIdentity() = runTest {
        val repository = TestMediaSourceRepository(savedSource())
        var opened: app.naviamp.provider.navidrome.NavidromeConnectionLoginRequest? = null
        val port = DesktopCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = DesktopNavidromeSessionOpener { request, _ ->
                opened = request
                session(request.savedConnectionForLogin ?: error("saved credentials missing"))
            },
        )

        port.connect(
            request = NaviampCoreConnectionRequest.Form(
                form = ConnectionFormState(
                    displayName = "Renamed",
                    serverUrl = "https://music.example",
                    username = "demo",
                ),
                savedConnectionId = "source-1",
            ),
            plan = NaviampConnectionAttemptPlan(
                restoreSavedSession = false,
                clearExistingPlayback = true,
                clearProviderData = false,
                runFullLibraryRefresh = true,
            ),
        )

        assertEquals("token", opened?.savedConnectionForLogin?.token)
        assertEquals("Renamed", opened?.displayName)
        assertNotNull(port.providerSource.current())
    }

    @Test
    fun savedConnectionPublishesInventoryAndDeleteClearsTheLiveProvider() = runTest {
        val repository = TestMediaSourceRepository(savedSource())
        val port = DesktopCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = DesktopNavidromeSessionOpener { request, _ ->
                session(request.savedConnectionForLogin ?: error("saved credentials missing"))
            },
        )

        val connected = port.connect(
            NaviampCoreConnectionRequest.Saved("source-1"),
            NaviampConnectionAttemptPlan(
                restoreSavedSession = true,
                clearExistingPlayback = false,
                clearProviderData = false,
                runFullLibraryRefresh = false,
            ),
        )

        assertEquals("source-1", connected.inventory.currentSourceId)
        assertEquals("Home Music", connected.inventory.connections.single().displayName)
        assertNotNull(port.currentProvider())

        val inventory = port.deleteConnection("source-1")

        assertEquals(emptyList(), inventory.connections)
        assertNull(port.currentProvider())
    }

    @Test
    fun databaseResetDropsTheLiveProviderWithoutPerformingAnotherRepositoryMutation() = runTest {
        val repository = TestMediaSourceRepository(savedSource())
        val port = DesktopCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = DesktopNavidromeSessionOpener { request, _ ->
                session(request.savedConnectionForLogin ?: error("saved credentials missing"))
            },
        )
        port.connect(
            NaviampCoreConnectionRequest.Saved("source-1"),
            NaviampConnectionAttemptPlan(true, false, false, false),
        )

        port.clearActiveSession()

        assertNull(port.currentProvider())
        assertEquals("source-1", repository.mediaSources().single().id)
    }

    @Test
    fun editableConnectionMapsProviderFoldersWithoutOwningSelectionPolicy() = runTest {
        val port = DesktopCoreProviderSessionPort(
            mediaSources = TestMediaSourceRepository(savedSource()),
            sessionOpener = DesktopNavidromeSessionOpener { _, _ -> error("not used") },
            musicFolders = { listOf(NavidromeMusicFolder("1", "Main"), NavidromeMusicFolder("2", "Archive")) },
        )

        val editable = port.editableConnection("source-1")

        assertEquals("https://music.example", editable.form.serverUrl)
        assertEquals(listOf("Main", "Archive"), editable.availableMusicFolders.map { it.name })
    }

    @Test
    fun editableConnectionReportsFolderEffectFailureInsteadOfSilentlyMasqueradingAsAnEmptyLibrary() = runTest {
        val port = DesktopCoreProviderSessionPort(
            mediaSources = TestMediaSourceRepository(savedSource()),
            sessionOpener = DesktopNavidromeSessionOpener { _, _ -> error("not used") },
            musicFolders = { error("offline") },
        )

        val editable = port.editableConnection("source-1")

        assertEquals(emptyList(), editable.availableMusicFolders)
        assertTrue(editable.musicFoldersLoadFailed)
    }
}

private fun savedSource() = SavedMediaSource(
    id = "source-1",
    providerId = "navidrome",
    cacheNamespace = "navidrome:demo",
    displayName = "Home Music",
    baseUrl = "https://music.example",
    username = "demo",
    token = "token",
    salt = "salt",
    nativeToken = "native-token",
    selectedMusicFolderIds = listOf("1"),
    createdAtEpochMillis = 1L,
    lastConnectedAtEpochMillis = 2L,
    lastSyncStartedAtEpochMillis = null,
    lastSyncCompletedAtEpochMillis = null,
)

private fun session(connection: NavidromeConnection): DesktopNavidromeSession = DesktopNavidromeSession(
    connection = connection,
    provider = NavidromeProvider(connection),
    sourceId = "source-1",
    validation = ConnectionValidation(serverVersion = "0.58.0", apiVersion = "1.16.1"),
)

private class TestMediaSourceRepository(source: SavedMediaSource) :
    MediaSourceRepository,
    ProviderMediaSourceRepository {
    private val sources = linkedMapOf(source.id to source)
    var lastPersisted: ProviderMediaSourceConnection? = null

    override fun latestMediaSource(): SavedMediaSource? = sources.values.lastOrNull()
    override fun mediaSources(): List<SavedMediaSource> = sources.values.toList()
    override fun mediaSource(sourceId: String): SavedMediaSource? = sources[sourceId]
    override fun deleteMediaSource(sourceId: String) {
        sources.remove(sourceId)
    }

    override fun upsertProviderMediaSource(
        connection: ProviderMediaSourceConnection,
        cacheNamespace: String,
        providerId: String,
    ): MediaSourceIdentity {
        lastPersisted = connection
        return MediaSourceIdentity("source-1", cacheNamespace, connection.displayName)
    }
}
