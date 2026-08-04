package app.naviamp.desktop

import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageDatabaseLocation
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopMediaSourceStorageTest {
    @Test
    fun sharedCatalogOwnsProtectedMediaSourcePersistenceAcrossDesktopRestarts() {
        val directory = Files.createTempDirectory("naviamp-desktop-media-sources-test")
        val location = StorageDatabaseLocation(directory.toString(), "sources.db")
        val audio = Files.createDirectories(directory.resolve("audio"))
        val downloads = Files.createDirectories(directory.resolve("downloads"))
        val sourceId = DesktopStorageRepositories.open(
            location = location,
            audioCacheDirectory = audio,
            downloadDirectory = downloads,
            nowEpochMillis = { 41L },
            credentialProtector = TestCredentialProtector,
        ).use { storage ->
            val identity = storage.mediaSources.upsertProviderMediaSource(
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
            val stored = storage.database.naviampStorageQueries.selectMediaSourceById(identity.id).executeAsOne()
            assertTrue(stored.token.startsWith("protected:"))
            assertFalse(stored.token.contains("secret-token"))
            identity.id
        }

        DesktopStorageRepositories.open(
            location = location,
            audioCacheDirectory = audio,
            downloadDirectory = downloads,
            nowEpochMillis = { 42L },
            credentialProtector = TestCredentialProtector,
        ).use { reopened ->
            assertEquals("secret-token", reopened.mediaSources.mediaSource(sourceId)?.token)
            assertEquals("secret-salt", reopened.mediaSources.mediaSource(sourceId)?.salt)
            assertEquals(41L, reopened.mediaSources.mediaSource(sourceId)?.lastConnectedAtEpochMillis)
        }

        directory.toFile().deleteRecursively()
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
