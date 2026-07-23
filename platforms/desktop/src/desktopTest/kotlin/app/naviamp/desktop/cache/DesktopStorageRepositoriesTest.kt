package app.naviamp.desktop

import app.naviamp.storage.PassthroughStorageCredentialProtector
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.PlaybackSessionSettings
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
        Files.write(downloads.resolve("orphaned-download.bin"), byteArrayOf(4, 5, 6))
        val legacyCache = Files.write(root.resolve("cache.db"), byteArrayOf(7, 8, 9))
        val legacyStorage = Files.write(root.resolve("legacy-storage.db"), byteArrayOf(10, 11, 12))

        DesktopStorageRepositories.open(
            location = StorageDatabaseLocation(root.toString(), "storage.db"),
            audioCacheDirectory = audio,
            downloadDirectory = downloads,
            nowEpochMillis = { 7L },
            credentialProtector = PassthroughStorageCredentialProtector,
            clearUntrackedDownloadsOnReset = true,
            legacyDatabaseFilesOnReset = listOf(legacyCache, legacyStorage),
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
            repositories.audioStore.updateAudioCacheLimit(1_024L)
            repositories.maintenance.updateAudioCacheLimit(1_024L)
            assertEquals(1_024L, repositories.maintenance.stats().maxAudioBytes)
            repositories.playbackSessions.savePlaybackSession(
                PlaybackSessionSettings.fromTracks(
                    listOf(
                        Track(
                            id = TrackId("track"),
                            title = "Track",
                            artistName = "Artist",
                            albumTitle = null,
                            durationSeconds = null,
                            coverArtId = null,
                            audioInfo = null,
                            replayGain = null,
                        ),
                    ),
                    currentIndex = 0,
                ),
                "source",
            )
            assertEquals(
                "track",
                repositories.playbackSessions.loadPlaybackSession("source")?.currentTrack()?.id?.value,
            )

            repositories.maintenance.clearCacheData()

            assertEquals(0L, repositories.maintenance.stats().responseCount)
            assertFalse(audio.resolve("cached.bin").exists())

            repositories.maintenance.clearAll()

            assertFalse(downloads.resolve("orphaned-download.bin").exists())
            assertFalse(legacyCache.exists())
            assertFalse(legacyStorage.exists())
            assertEquals(0L, repositories.maintenance.stats().downloadBytes)
        }

        DesktopFileTreeCleaner().clearDirectoryContents(root)
        Files.delete(root)
    }
}
