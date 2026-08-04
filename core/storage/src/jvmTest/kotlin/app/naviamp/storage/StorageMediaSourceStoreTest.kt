package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.ConnectionTlsSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageMediaSourceStoreTest {
    @Test
    fun sharedStoreProtectsAndRestoresAllCredentialFields() {
        withDatabase { database ->
            val store = StorageMediaSourceStore(
                queries = database.naviampStorageQueries,
                nowMillis = { 42L },
                credentialProtector = ReversingCredentialProtector,
            )
            val connection = providerConnection()

            val identity = store.upsertProviderMediaSource(connection, "cache", "navidrome")
            val stored = database.naviampStorageQueries.selectMediaSourceById(identity.id).executeAsOne()
            val restored = store.mediaSource(identity.id)

            assertTrue(stored.token.startsWith("protected:"))
            assertFalse(stored.token.contains(connection.token))
            assertTrue(stored.client_certificate_keystore_password?.startsWith("protected:") == true)
            assertFalse(stored.custom_headers_json.orEmpty().contains("secret-value"))
            assertEquals(connection.token, restored?.token)
            assertEquals(connection.salt, restored?.salt)
            assertEquals(connection.nativeToken, restored?.nativeToken)
            assertEquals(connection.tlsSettings, restored?.tlsSettings)
            assertEquals(connection.secondaryUrls, restored?.secondaryUrls)
            assertEquals(connection.customHeaders, restored?.customHeaders)
            assertEquals(connection.selectedMusicFolderIds, restored?.selectedMusicFolderIds)
        }
    }

    @Test
    fun protectedStoreMigratesCredentialsWrittenByPassthroughHost() {
        withDatabase { database ->
            val queries = database.naviampStorageQueries
            val plainStore = StorageMediaSourceStore(queries, nowMillis = { 1L })
            val identity = plainStore.upsertProviderMediaSource(providerConnection(), "cache", "navidrome")

            StorageMediaSourceStore(
                queries = queries,
                nowMillis = { 2L },
                credentialProtector = ReversingCredentialProtector,
            )

            val migrated = queries.selectMediaSourceById(identity.id).executeAsOne()
            assertTrue(migrated.token.startsWith("protected:"))
            assertTrue(migrated.salt.startsWith("protected:"))
            assertTrue(migrated.native_token?.startsWith("protected:") == true)
            assertFalse(migrated.custom_headers_json.orEmpty().contains("secret-value"))
        }
    }

    @Test
    fun credentialProtectionFailureDoesNotPersistAnUnauthenticatedSource() {
        withDatabase { database ->
            val store = StorageMediaSourceStore(
                queries = database.naviampStorageQueries,
                nowMillis = { 42L },
                credentialProtector = FailingCredentialProtector,
            )

            val failure = assertFailsWith<IllegalStateException> {
                store.upsertProviderMediaSource(providerConnection(), "cache", "navidrome")
            }

            assertEquals("Could not securely store provider token.", failure.message)
            assertTrue(database.naviampStorageQueries.selectMediaSources().executeAsList().isEmpty())
        }
    }

    @Test
    fun pruningDeletesOnlyKnownCacheFilesAndRetainsSourceWhenDeletionCannotBeVerified() {
        withDatabase { database ->
            var now = 1L
            val queries = database.naviampStorageQueries
            val store = StorageMediaSourceStore(queries, nowMillis = { now })
            val removable = store.upsertProviderMediaSource(
                providerConnection().copy(baseUrl = "https://old.example.test"),
                "old-cache",
                "navidrome",
            )
            val retained = store.upsertProviderMediaSource(
                providerConnection().copy(baseUrl = "https://blocked.example.test"),
                "blocked-cache",
                "navidrome",
            )
            now = 100L
            val active = store.upsertProviderMediaSource(
                providerConnection().copy(baseUrl = "https://active.example.test"),
                "active-cache",
                "navidrome",
            )
            queries.insertTestCachedAudio(removable.id, "cached", "/known/old-cache.opus")
            queries.insertTestCachedAudio(retained.id, "blocked", "/not-a-regular-file")
            val attemptedPaths = mutableListOf<String>()

            val pruned = store.pruneUnusedSourceScopes(
                activeSourceIds = setOf(active.id),
                lastConnectedBeforeEpochMillis = 50L,
                limit = 20L,
                deleteKnownAudioCacheFile = { path ->
                    attemptedPaths += path
                    path != "/not-a-regular-file"
                },
                deleteKnownDownloadFile = { error("Downloaded sources must not be pruning candidates.") },
            )

            assertEquals(1, pruned)
            assertEquals(setOf("/known/old-cache.opus", "/not-a-regular-file"), attemptedPaths.toSet())
            assertEquals(null, queries.selectMediaSourceById(removable.id).executeAsOneOrNull())
            assertTrue(queries.selectCachedAudioForSource(removable.id).executeAsList().isEmpty())
            assertTrue(queries.selectMediaSourceById(retained.id).executeAsOneOrNull() != null)
            assertEquals(
                "/not-a-regular-file",
                queries.selectCachedAudioForSource(retained.id).executeAsOne().file_path,
            )
        }
    }
}

private fun NaviampStorageQueries.insertTestCachedAudio(sourceId: String, trackId: String, filePath: String) {
    upsertCachedAudio(
        source_id = sourceId,
        remote_track_id = trackId,
        quality_key = "transcoded:opus:128",
        file_path = filePath,
        size_bytes = 3L,
        content_type = "audio/ogg",
        created_at_epoch_millis = 1L,
        last_accessed_epoch_millis = 1L,
    )
}

private inline fun withDatabase(block: (NaviampStorageDatabase) -> Unit) {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    try {
        NaviampStorageDatabase.Schema.create(driver)
        block(NaviampStorageDatabase(driver))
    } finally {
        driver.close()
    }
}

private fun providerConnection() = ProviderMediaSourceConnection(
    displayName = "Server",
    baseUrl = "https://music.example.test",
    username = "listener",
    token = "token-value",
    salt = "salt-value",
    nativeToken = "native-token",
    tlsSettings = ConnectionTlsSettings(
        insecureSkipTlsVerification = true,
        customCertificatePath = "/cert.pem",
        clientCertificateKeyStorePath = "/client.p12",
        clientCertificateKeyStorePassword = "certificate-password",
    ),
    secondaryUrls = listOf(ConnectionSecondaryUrl("https://backup.example.test", "Backup", 1)),
    customHeaders = listOf(
        ConnectionHeaderDefinition("X-Public", "public-value"),
        ConnectionHeaderDefinition("X-Secret", "secret-value", valueIsSecret = true),
    ),
    selectedMusicFolderIds = listOf("folder-one", "folder-two"),
)

private object ReversingCredentialProtector : StorageCredentialProtector {
    override fun protect(value: String?): String? = value?.let {
        if (isProtected(it)) it else "protected:${it.reversed()}"
    }

    override fun reveal(value: String?): String? = value?.let {
        if (isProtected(it)) it.removePrefix("protected:").reversed() else it
    }

    override fun isProtected(value: String?): Boolean = value?.startsWith("protected:") == true
}

private object FailingCredentialProtector : StorageCredentialProtector {
    override fun protect(value: String?): String? = null

    override fun reveal(value: String?): String? = value

    override fun isProtected(value: String?): Boolean = false
}
