package app.naviamp.android

import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.naviamp.android.security.AndroidKeystoreCredentialProtector
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.presentation.NaviampCoreProviderSessionRoute
import app.naviamp.presentation.NaviampCoreProviderSessionRouter
import app.naviamp.provider.jellyfin.*
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.NaviampStorageSchema
import app.naviamp.storage.StorageMediaSourceStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.*

/** Exercises Android SQLite and Keystore with a disposable database and synthetic credentials. */
class AndroidConnectCredentialStorageInstrumentedTest {
    @Test
    fun jellyfinSetupCredentialSurvivesDatabaseReopenThroughTheSharedRouter(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "connect-credential-test-${UUID.randomUUID()}.db"
        val password = "synthetic-setup-password"
        var driver: AndroidSqliteDriver? = null
        try {
            driver = AndroidSqliteDriver(NaviampStorageSchema, context, name)
            var queries = NaviampStorageDatabase(driver).naviampStorageQueries
            var store = StorageMediaSourceStore(queries, { 42L }, AndroidKeystoreCredentialProtector())
            val source = store.upsertProviderMediaSource(
                ProviderMediaSourceConnection(
                    displayName = "Fixture", baseUrl = "https://fixture.invalid", username = "fixture",
                    token = "", salt = "", nativeToken = "synthetic-token", password = password,
                    selectedMusicFolderIds = listOf("music"),
                ),
                "jellyfin:fixture", "jellyfin",
            )
            val raw = queries.selectMediaSourceById(source.id).executeAsOne().password
            assertNotNull(raw)
            assertNotEquals(password, raw)
            assertTrue(AndroidKeystoreCredentialProtector().isProtected(raw))
            driver.close()
            driver = AndroidSqliteDriver(NaviampStorageSchema, context, name)
            queries = NaviampStorageDatabase(driver).naviampStorageQueries
            store = StorageMediaSourceStore(queries, { 43L }, AndroidKeystoreCredentialProtector())
            val services = JellyfinSessionServiceFactory {
                JellyfinSessionService(object : JellyfinHttpClient {
                    override suspend fun get(url: String, headers: Map<String, String>): JellyfinHttpResponse =
                        error("Setup export must not request libraries or authenticate")
                    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): JellyfinHttpResponse =
                        error("Setup export must not request libraries or authenticate")
                }, JellyfinClientIdentity("fixture-device", "TV fixture", clientVersion = "test"))
            }
            val port = JellyfinCoreProviderSessionPort(
                mediaSources = store, sessionOpener = JellyfinProviderSessionOpener { _, _ -> error("Unused login") },
                sessionServices = services, deviceId = "fixture-device", initialSource = store.mediaSource(source.id),
            )
            val router = NaviampCoreProviderSessionRouter(listOf(
                NaviampCoreProviderSessionRoute(setOf("jellyfin"), port),
            ))
            val exported = assertNotNull(router.currentProvisioningConnection()).form
            assertEquals(password, exported.password)
            assertEquals(listOf("music"), exported.selectedMusicFolderIds)
            router.clearActiveSession()
            assertNull(router.currentProvisioningConnection())
        } finally {
            driver?.close()
            context.deleteDatabase(name)
        }
    }
}
