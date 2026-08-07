package app.naviamp.provider.navidrome

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderIdentityMigrationRepository
import app.naviamp.domain.cache.ProviderIdentityMigrationResult
import app.naviamp.domain.cache.ProviderIdentityProbeState
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.ProviderIdNavidrome
import app.naviamp.domain.provider.ProviderIdSubsonic
import app.naviamp.domain.provider.ProviderIdBandcamp
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.presentation.NaviampCoreConnectionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NavidromeCoreProviderSessionPortTest {
    @Test
    fun genericSubsonicSavedSourceRestoresAndRoutesWithoutNavidromeMigration() = runTest {
        val source = savedSource(providerId = ProviderIdSubsonic)
        val repository = TestMediaSourceRepository(source)
        var migrationChecks = 0
        val port = NavidromeCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = NavidromeProviderSessionOpener { request, _ ->
                assertEquals(ProviderIdSubsonic, request.providerId)
                session(request.savedConnectionForLogin ?: error("saved credentials missing"))
            },
            initialSource = source,
            canonicalIdMigrationSupport = {
                migrationChecks += 1
                NavidromeCanonicalIdMigrationSupport.Confirmed
            },
        )

        assertEquals(ProviderIdSubsonic, port.currentProvider()?.id?.value)
        assertEquals("source-1", port.currentSourceId())
        port.connect(
            NaviampCoreConnectionRequest.Saved("source-1"),
            NaviampConnectionAttemptPlan(true, false, false, false),
        )

        assertEquals(0, migrationChecks)
        assertNull(repository.migratedIdentityVersion)
        assertFalse(port.refreshActiveSession())
    }

    @Test
    fun bandcampSavedSourceRoutesThroughTheSharedSubsonicSession() = runTest {
        val source = savedSource(providerId = ProviderIdBandcamp)
        val repository = TestMediaSourceRepository(source)
        val port = NavidromeCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = NavidromeProviderSessionOpener { request, _ ->
                assertEquals(ProviderIdBandcamp, request.providerId)
                assertFalse(request.nativeAuthEnabled)
                session(request.savedConnectionForLogin ?: error("saved credentials missing"))
            },
            initialSource = source,
        )
        val router = subsonicFamilyProviderSessionRouter(port)

        val connected = router.connect(
            NaviampCoreConnectionRequest.Saved("source-1"),
            NaviampConnectionAttemptPlan(true, false, false, false),
        )

        assertEquals(ProviderIdBandcamp, router.currentProvider()?.id?.value)
        assertEquals("source-1", connected.sourceId)
        assertFalse(router.refreshActiveSession())
    }

    @Test
    fun savedStartupSourceImmediatelySuppliesTheSharedProviderAndSelectedInventory() {
        val source = savedSource()
        val port = NavidromeCoreProviderSessionPort(
            mediaSources = TestMediaSourceRepository(source),
            sessionOpener = NavidromeProviderSessionOpener { _, _ -> error("not used") },
            initialSource = source,
        )

        assertNotNull(port.providerSource.current())
        assertEquals("source-1", port.initialInventory().currentSourceId)
    }

    @Test
    fun activeSessionSuppliesSmartPlaylistProviderAndPersistsItsNativeToken() = runTest {
        val repository = TestMediaSourceRepository(savedSource())
        val port = testPort(repository)
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
        var opened: NavidromeConnectionLoginRequest? = null
        val port = NavidromeCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = NavidromeProviderSessionOpener { request, _ ->
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
            plan = NaviampConnectionAttemptPlan(false, true, false, true),
        )

        assertEquals("token", opened?.savedConnectionForLogin?.token)
        assertEquals("Renamed", opened?.displayName)
        assertNotNull(port.providerSource.current())
    }

    @Test
    fun savedConnectionPublishesInventoryAndDeleteClearsTheLiveProvider() = runTest {
        val repository = TestMediaSourceRepository(savedSource())
        val port = testPort(repository)

        val connected = port.connect(
            NaviampCoreConnectionRequest.Saved("source-1"),
            NaviampConnectionAttemptPlan(true, false, false, false),
        )

        assertEquals("source-1", connected.inventory.currentSourceId)
        assertEquals("Home Music", connected.inventory.connections.single().displayName)
        assertNotNull(port.currentProvider())

        val inventory = port.deleteConnection("source-1")

        assertEquals(emptyList(), inventory.connections)
        assertNull(port.currentProvider())
    }

    @Test
    fun databaseResetDropsTheLiveProviderWithoutMutatingTheRepository() = runTest {
        val repository = TestMediaSourceRepository(savedSource())
        val port = testPort(repository)
        port.connect(
            NaviampCoreConnectionRequest.Saved("source-1"),
            NaviampConnectionAttemptPlan(true, false, false, false),
        )

        port.clearActiveSession()

        assertNull(port.currentProvider())
        assertEquals("source-1", repository.mediaSources().single().id)
    }

    @Test
    fun editableConnectionMapsProviderFoldersAndReportsEffectFailures() = runTest {
        val success = NavidromeCoreProviderSessionPort(
            mediaSources = TestMediaSourceRepository(savedSource()),
            sessionOpener = NavidromeProviderSessionOpener { _, _ -> error("not used") },
            musicFolders = { listOf(NavidromeMusicFolder("1", "Main"), NavidromeMusicFolder("2", "Archive")) },
        ).editableConnection("source-1")
        val failure = NavidromeCoreProviderSessionPort(
            mediaSources = TestMediaSourceRepository(savedSource()),
            sessionOpener = NavidromeProviderSessionOpener { _, _ -> error("not used") },
            musicFolders = { error("offline") },
        ).editableConnection("source-1")

        assertEquals("https://music.example", success.form.serverUrl)
        assertEquals(listOf("Main", "Archive"), success.availableMusicFolders.map { it.name })
        assertEquals(emptyList(), failure.availableMusicFolders)
        assertTrue(failure.musicFoldersLoadFailed)
    }

    @Test
    fun advertisedTopSongsByArtistIdActivatesMigrationWithoutAReleaseNumberGate() = runTest {
        val repository = TestMediaSourceRepository(savedSource())
        val port = NavidromeCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = NavidromeProviderSessionOpener { request, _ ->
                session(request.savedConnectionForLogin ?: error("saved credentials missing"), "custom-build")
            },
            canonicalIdMigrationSupport = { NavidromeCanonicalIdMigrationSupport.Confirmed },
        )

        port.connect(
            NaviampCoreConnectionRequest.Saved("source-1"),
            NaviampConnectionAttemptPlan(true, false, false, false),
        )

        assertEquals(1L, repository.migratedIdentityVersion)
        assertEquals(
            "3LyqmwQBm5IRqlVjNYASwb",
            repository.identityTransform?.invoke("zzzzzzzzzzzzzzzzzzzzzz"),
        )
    }

    @Test
    fun restoredSessionAlsoActivatesAnOutstandingIdentityMigration() = runTest {
        val source = savedSource().copy(nativeToken = null)
        val repository = TestMediaSourceRepository(source)
        val port = NavidromeCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = NavidromeProviderSessionOpener { _, _ -> error("not used") },
            initialSource = source,
            validateProvider = { ConnectionValidation(serverVersion = "custom-build", apiVersion = "1.16.1") },
            canonicalIdMigrationSupport = { NavidromeCanonicalIdMigrationSupport.Confirmed },
        )

        assertFalse(port.refreshActiveSession())

        assertEquals(1L, repository.migratedIdentityVersion)
    }

    @Test
    fun missingExtensionIsSkippedUntilTheReportedServerBuildChanges() = runTest {
        val repository = TestMediaSourceRepository(savedSource())
        var serverVersion = "0.63.2 (old-build)"
        var supportChecks = 0
        val port = NavidromeCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = NavidromeProviderSessionOpener { request, _ ->
                session(request.savedConnectionForLogin ?: error("saved credentials missing"), serverVersion)
            },
            canonicalIdMigrationSupport = {
                supportChecks += 1
                NavidromeCanonicalIdMigrationSupport.Unsupported
            },
        )

        port.connect(NaviampCoreConnectionRequest.Saved("source-1"), NaviampConnectionAttemptPlan(true, false, false, false))
        port.connect(NaviampCoreConnectionRequest.Saved("source-1"), NaviampConnectionAttemptPlan(true, false, false, false))
        assertEquals(1, supportChecks)
        assertEquals(ProviderIdentityProbeState(1L, serverVersion), repository.probeState)

        serverVersion = "0.63.3 (new-build)"
        port.connect(NaviampCoreConnectionRequest.Saved("source-1"), NaviampConnectionAttemptPlan(true, false, false, false))
        assertEquals(2, supportChecks)
        assertEquals(ProviderIdentityProbeState(1L, serverVersion), repository.probeState)
    }

    @Test
    fun failedExtensionDiscoveryIsNotCheckpointedAsACompatibilityResult() = runTest {
        val repository = TestMediaSourceRepository(savedSource())
        val port = NavidromeCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = NavidromeProviderSessionOpener { request, _ ->
                session(request.savedConnectionForLogin ?: error("saved credentials missing"), "unreachable-build")
            },
            canonicalIdMigrationSupport = { NavidromeCanonicalIdMigrationSupport.Inconclusive },
        )

        port.connect(NaviampCoreConnectionRequest.Saved("source-1"), NaviampConnectionAttemptPlan(true, false, false, false))

        assertNull(repository.probeState)
        assertNull(repository.migratedIdentityVersion)
    }
}

private fun testPort(repository: TestMediaSourceRepository) = NavidromeCoreProviderSessionPort(
    mediaSources = repository,
    sessionOpener = NavidromeProviderSessionOpener { request, _ ->
        session(request.savedConnectionForLogin ?: error("saved credentials missing"))
    },
)

private fun savedSource(providerId: String = ProviderIdNavidrome) = SavedMediaSource(
    id = "source-1",
    providerId = providerId,
    cacheNamespace = "$providerId:demo",
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

private fun session(connection: NavidromeConnection, serverVersion: String = "0.58.0"): NavidromeProviderConnectionSession =
    NavidromeProviderConnectionSession(
        connection = connection,
        provider = NavidromeProvider(connection),
        sourceId = "source-1",
        validation = ConnectionValidation(serverVersion = serverVersion, apiVersion = "1.16.1"),
    )

private class TestMediaSourceRepository(source: SavedMediaSource) :
    MediaSourceRepository,
    ProviderMediaSourceRepository,
    ProviderIdentityMigrationRepository {
    private val sources = linkedMapOf(source.id to source)
    var lastPersisted: ProviderMediaSourceConnection? = null
    var migratedIdentityVersion: Long? = null
    var identityTransform: ((String) -> String)? = null
    var probeState: ProviderIdentityProbeState? = null

    override fun latestMediaSource(): SavedMediaSource? = sources.values.lastOrNull()
    override fun mediaSources(): List<SavedMediaSource> = sources.values.toList()
    override fun mediaSource(sourceId: String): SavedMediaSource? = sources[sourceId]
    override fun deleteMediaSource(sourceId: String) {
        sources.remove(sourceId)
    }

    override fun providerIdentityVersion(sourceId: String): Long = migratedIdentityVersion ?: 0L

    override fun providerIdentityProbeState(sourceId: String): ProviderIdentityProbeState? = probeState

    override fun recordProviderIdentityProbeState(sourceId: String, state: ProviderIdentityProbeState) {
        probeState = state
    }

    override fun upsertProviderMediaSource(
        connection: ProviderMediaSourceConnection,
        cacheNamespace: String,
        providerId: String,
        preferredSourceId: String?,
    ): MediaSourceIdentity {
        lastPersisted = connection
        return MediaSourceIdentity("source-1", cacheNamespace, connection.displayName)
    }

    override fun migrateProviderIdentities(
        sourceId: String,
        providerId: String,
        targetVersion: Long,
        transform: (String) -> String,
    ): ProviderIdentityMigrationResult {
        migratedIdentityVersion = targetVersion
        probeState = null
        identityTransform = transform
        return ProviderIdentityMigrationResult(migrated = true)
    }

}
