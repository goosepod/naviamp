package app.naviamp.provider.jellyfin

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.presentation.NaviampCoreConnectionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class JellyfinCoreProviderSessionPortTest {
    @Test
    fun validatedLoginPersistsReusablePasswordAndExportsItWithoutLibraryRequests() = runTest {
        val f = Fixture()
        val port = f.port()
        port.connect(form("reusable-secret"), plan)
        assertEquals("reusable-secret", f.repository.lastWrite?.password)
        val requests = f.http.requests
        val exported = assertNotNull(port.currentProvisioningConnection()).form
        assertEquals("reusable-secret", exported.password)
        assertEquals("jellyfin", exported.providerId)
        assertEquals(listOf("music"), exported.selectedMusicFolderIds)
        assertEquals(requests, f.http.requests)

        // A new process can reuse the protected repository's revealed password, without login.
        val restored = f.port(f.repository.mediaSource("source"))
        assertEquals("reusable-secret", restored.currentProvisioningConnection()?.form?.password)
        assertEquals(requests, f.http.requests)
    }

    @Test
    fun savedTokenReconnectRetainsPasswordWithoutPasswordAuthentication() = runTest {
        val f = Fixture(saved(password = "retained-secret"))
        val port = f.port()
        port.connect(NaviampCoreConnectionRequest.Saved("source"), plan)
        assertEquals(0, f.http.logins)
        assertEquals("retained-secret", f.repository.lastWrite?.password)
        assertEquals("retained-secret", port.currentProvisioningConnection()?.form?.password)
    }

    @Test
    fun tokenOnlySourceNeverExportsItsAccessTokenAsAPassword() = runTest {
        val f = Fixture(saved())
        val port = f.port(f.repository.mediaSource("source"))
        assertEquals("", port.currentProvisioningConnection()?.form?.password)
        assertEquals(0, f.http.requests)
        port.clearActiveSession()
        assertNull(port.currentProvisioningConnection())
    }

    @Test
    fun sourceSwitchAndDeletionCannotReuseAnotherSourcesPassword() = runTest {
        val f = Fixture(saved(), saved(id = "other", username = "bob"))
        val port = f.port()
        port.connect(form("alice-secret"), plan)
        port.connect(NaviampCoreConnectionRequest.Saved("other"), plan)
        assertEquals("", port.currentProvisioningConnection()?.form?.password)
        port.deleteConnection("other")
        assertNull(port.currentProvisioningConnection())
    }

    @Test
    fun failedLoginDoesNotReplaceTheSavedOrActiveReusablePassword() = runTest {
        val f = Fixture(saved(password = "original-secret"))
        val port = f.port(f.repository.mediaSource("source"))
        f.http.rejectLogin = true
        assertFailsWith<JellyfinException> { port.connect(form("invalid-secret"), plan) }
        assertNull(f.repository.lastWrite)
        assertEquals("original-secret", port.currentProvisioningConnection()?.form?.password)
    }

    @Test
    fun aSecondInstallationSigningInNoLongerSignsOutTheFirst() = runTest {
        // #730: Jellyfin revokes a user's earlier tokens for the same DeviceId on every sign-in.
        val server = RevokingServer()
        val phone = Installation(server, "naviamp-android-phone")
        val television = Installation(server, "naviamp-android-television")
        phone.port().connect(form("secret"), plan)
        television.port().connect(form("secret"), plan)

        val relaunchedPhone = phone.port(phone.repository.mediaSource("source"))
        relaunchedPhone.connect(NaviampCoreConnectionRequest.Saved("source"), plan)
        assertNotNull(relaunchedPhone.currentProvider())
        assertEquals(listOf("naviamp-android-phone", "naviamp-android-television"), server.loginDeviceIds)

        // The former shared platform constant reproduces the report: the phone's token is revoked.
        val sharedServer = RevokingServer()
        val legacyPhone = Installation(sharedServer, "naviamp-android")
        legacyPhone.port().connect(form("secret"), plan)
        Installation(sharedServer, "naviamp-android").port().connect(form("secret"), plan)
        val failure = assertFails {
            legacyPhone.port(legacyPhone.repository.mediaSource("source"))
                .connect(NaviampCoreConnectionRequest.Saved("source"), plan)
        }
        assertTrue(failure.message.orEmpty().contains("reconnect with your password"), failure.message)
    }

    @Test
    fun revokedTokenSignsInAgainThroughTheExistingConnection() = runTest {
        val server = RevokingServer()
        val phone = Installation(server, "naviamp-android-phone")
        phone.port().connect(form("secret"), plan)
        server.revokeAll()

        val relaunched = phone.port(phone.repository.mediaSource("source"))
        assertFails { relaunched.connect(NaviampCoreConnectionRequest.Saved("source"), plan) }

        // Re-entering the password on the saved connection reuses it instead of adding a duplicate.
        val editing = form("secret").copy(savedConnectionId = "source")
        relaunched.connect(editing, plan)
        assertEquals(listOf("source"), phone.repository.mediaSources().map { it.id })
        val renewedToken = assertNotNull(phone.repository.mediaSource("source")?.nativeToken)
        assertTrue(server.isValid(renewedToken))

        val nextLaunch = phone.port(phone.repository.mediaSource("source"))
        nextLaunch.connect(NaviampCoreConnectionRequest.Saved("source"), plan)
        assertNotNull(nextLaunch.currentProvider())
    }

    /** One app installation: its own saved connections and its own Jellyfin DeviceId. */
    private class Installation(server: JellyfinHttpClient, deviceId: String) {
        val repository = Repository(emptyList())
        private val services = JellyfinSessionServiceFactory {
            JellyfinSessionService(server, JellyfinClientIdentity(deviceId, "Android", clientVersion = "test"))
        }
        private val id = deviceId
        fun port(initialSource: SavedMediaSource? = null) = JellyfinCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = jellyfinProviderSessionOpener(services, Maintenance, repository) { 1_000L },
            sessionServices = services,
            deviceId = id,
            initialSource = initialSource,
        )
    }

    /** Models Jellyfin 12.4 token issuance: a sign-in revokes the user's tokens for that DeviceId. */
    private class RevokingServer : JellyfinHttpClient {
        private val deviceByToken = mutableMapOf<String, String>()
        private var issued = 0
        val loginDeviceIds = mutableListOf<String>()

        fun isValid(token: String) = token in deviceByToken
        fun revokeAll() = deviceByToken.clear()

        override suspend fun get(url: String, headers: Map<String, String>): JellyfinHttpResponse {
            val token = headers.authorizationField("Token")
            if (token == null || token !in deviceByToken) return JellyfinHttpResponse(401, "")
            return when {
                url.endsWith("/System/Info") -> JellyfinHttpResponse(200, """{"Id":"server","Version":"12.4.0"}""")
                url.endsWith("/Users/Me") -> JellyfinHttpResponse(200, """{"Id":"user","Name":"alice"}""")
                else -> error("Unexpected metadata request")
            }
        }

        override suspend fun postJson(url: String, body: String, headers: Map<String, String>): JellyfinHttpResponse {
            val deviceId = checkNotNull(headers.authorizationField("DeviceId"))
            loginDeviceIds += deviceId
            deviceByToken.values.removeAll { it == deviceId }
            val token = "token-${++issued}"
            deviceByToken[token] = deviceId
            return JellyfinHttpResponse(200, """{"AccessToken":"$token","User":{"Id":"user","Name":"alice"}}""")
        }

        private fun Map<String, String>.authorizationField(name: String): String? =
            Regex("""\b$name="([^"]*)"""").find(getValue("Authorization"))?.groupValues?.get(1)
    }

    private class Fixture(vararg sources: SavedMediaSource) {
        val repository = Repository(sources.toList())
        val http = Http()
        private val services = JellyfinSessionServiceFactory {
            JellyfinSessionService(http, JellyfinClientIdentity("device", "TV fixture", clientVersion = "test"))
        }
        fun port(initialSource: SavedMediaSource? = null) = JellyfinCoreProviderSessionPort(
            mediaSources = repository,
            sessionOpener = jellyfinProviderSessionOpener(services, Maintenance, repository) { 1_000L },
            sessionServices = services,
            deviceId = "device",
            initialSource = initialSource,
        )
    }

    private class Http : JellyfinHttpClient {
        var requests = 0
        var logins = 0
        var rejectLogin = false
        override suspend fun get(url: String, headers: Map<String, String>): JellyfinHttpResponse {
            requests++
            return when {
                url.endsWith("/System/Info") -> JellyfinHttpResponse(200, """{"Id":"server","Version":"fixture"}""")
                url.endsWith("/Users/Me") -> JellyfinHttpResponse(200, """{"Id":"user","Name":"alice"}""")
                else -> error("Unexpected metadata request")
            }
        }
        override suspend fun postJson(url: String, body: String, headers: Map<String, String>): JellyfinHttpResponse {
            requests++
            logins++
            return if (rejectLogin) JellyfinHttpResponse(401, "") else JellyfinHttpResponse(200,
                """{"AccessToken":"access-token","User":{"Id":"user","Name":"alice"}}""")
        }
    }

    private class Repository(initial: List<SavedMediaSource>) : MediaSourceRepository, ProviderMediaSourceRepository {
        val sources = initial.associateBy { it.id }.toMutableMap()
        var lastWrite: ProviderMediaSourceConnection? = null
        override fun latestMediaSource() = sources.values.lastOrNull()
        override fun mediaSources() = sources.values.toList()
        override fun mediaSource(sourceId: String) = sources[sourceId]
        override fun deleteMediaSource(sourceId: String) { sources.remove(sourceId) }
        override fun upsertProviderMediaSource(connection: ProviderMediaSourceConnection, cacheNamespace: String,
            providerId: String, preferredSourceId: String?): MediaSourceIdentity {
            lastWrite = connection
            val id = preferredSourceId ?: "source"
            sources[id] = saved(id, connection.username, connection.password).copy(
                nativeToken = connection.nativeToken,
                selectedMusicFolderIds = connection.selectedMusicFolderIds,
                cacheNamespace = cacheNamespace,
            )
            return MediaSourceIdentity(id, cacheNamespace, connection.displayName)
        }
    }

    private object Maintenance : CacheMaintenanceRepository<Unit> {
        override fun clearProviderData() = Unit
        override fun clearCacheData() = Unit
        override fun clearDownloadData() = Unit
        override fun clearAll() = Unit
        override fun stats() = Unit
    }

    companion object {
        private val plan = NaviampConnectionAttemptPlan(false, false, false, false)
        private fun form(password: String) = NaviampCoreConnectionRequest.Form(ConnectionFormState(
            providerId = "jellyfin", serverUrl = "https://fixture.example", username = "alice",
            password = password, selectedMusicFolderIds = listOf("music"),
        ))
        private fun saved(id: String = "source", username: String = "alice", password: String? = null) = SavedMediaSource(
            id = id, providerId = "jellyfin", cacheNamespace = "jellyfin:$id", displayName = "Fixture",
            baseUrl = "https://fixture.example", username = username, token = "", salt = "",
            nativeToken = "access-token", password = password, selectedMusicFolderIds = listOf("music"),
            createdAtEpochMillis = 1, lastConnectedAtEpochMillis = null,
            lastSyncStartedAtEpochMillis = null, lastSyncCompletedAtEpochMillis = null,
        )
    }
}
