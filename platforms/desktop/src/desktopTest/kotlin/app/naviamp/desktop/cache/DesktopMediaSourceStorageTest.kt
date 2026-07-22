package app.naviamp.desktop

import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageDatabaseLocation
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopMediaSourceStorageTest {
    @Test
    fun ownsSharedSchemaAndProtectedMediaSourcePersistenceAcrossRestarts() {
        val directory = Files.createTempDirectory("naviamp-desktop-media-sources-test")
        val location = StorageDatabaseLocation(directory.toString(), "sources.db")
        val sourceId = DesktopMediaSourceStorage.open(
            location = location,
            nowEpochMillis = { 41L },
            credentialProtector = TestCredentialProtector,
        ).use { storage ->
            val identity = storage.upsertProviderMediaSource(
                connection = ProviderMediaSourceConnection(
                    displayName = "Home",
                    baseUrl = "https://music.example.test",
                    username = "listener",
                    token = "secret-token",
                    salt = "secret-salt",
                ),
                cacheNamespace = "home-server",
                providerId = "navidrome",
            )
            val stored = storage.database.naviampStorageQueries
                .selectMediaSourceById(identity.id)
                .executeAsOne()
            assertTrue(stored.token.startsWith("protected:"))
            assertFalse(stored.token.contains("secret-token"))
            identity.id
        }

        DesktopMediaSourceStorage.open(
            location = location,
            nowEpochMillis = { 42L },
            credentialProtector = TestCredentialProtector,
        ).use { reopened ->
            assertEquals("secret-token", reopened.mediaSource(sourceId)?.token)
            assertEquals("secret-salt", reopened.mediaSource(sourceId)?.salt)
            assertEquals(41L, reopened.mediaSource(sourceId)?.lastConnectedAtEpochMillis)
        }

        directory.resolve("sources.db").deleteIfExists()
        directory.resolve("sources.db-shm").deleteIfExists()
        directory.resolve("sources.db-wal").deleteIfExists()
        directory.deleteIfExists()
    }
}

private object TestCredentialProtector : StorageCredentialProtector {
    override fun protect(value: String?): String? = value?.let {
        if (isProtected(it)) it else "protected:${it.reversed()}"
    }

    override fun reveal(value: String?): String? = value?.let {
        if (isProtected(it)) it.removePrefix("protected:").reversed() else it
    }

    override fun isProtected(value: String?): Boolean = value?.startsWith("protected:") == true
}
