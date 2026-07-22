package app.naviamp.desktop

import app.naviamp.storage.PassthroughStorageCredentialProtector
import app.naviamp.storage.StorageDatabaseLocation
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopStorageRepositoriesTest {
    @Test
    fun composesFocusedRepositoriesAndMaintenanceOverOneSharedDatabase() {
        val root = Files.createTempDirectory("naviamp-desktop-storage-repositories")
        val audio = Files.createDirectories(root.resolve("audio"))
        val downloads = Files.createDirectories(root.resolve("downloads"))
        Files.write(audio.resolve("cached.bin"), byteArrayOf(1, 2, 3))

        DesktopStorageRepositories.open(
            location = StorageDatabaseLocation(root.toString(), "storage.db"),
            audioCacheDirectory = audio,
            downloadDirectory = downloads,
            nowEpochMillis = { 7L },
            credentialProtector = PassthroughStorageCredentialProtector,
        ).use { repositories ->
            repositories.providerResponses.upsertResponse(
                cacheKey = "response",
                providerId = "provider",
                resourceType = "album",
                resourceId = "1",
                payload = "{}",
                createdAtEpochMillis = 7L,
                lastAccessedEpochMillis = 7L,
            )

            assertEquals(1L, repositories.maintenance.stats().responseCount)
            assertEquals(audio.toAbsolutePath().toString(), repositories.maintenance.stats().audioCacheDirectory)

            repositories.maintenance.clearCacheData()

            assertEquals(0L, repositories.maintenance.stats().responseCount)
            assertFalse(audio.resolve("cached.bin").exists())
        }

        DesktopFileTreeCleaner().clearDirectoryContents(root)
        Files.delete(root)
    }
}
