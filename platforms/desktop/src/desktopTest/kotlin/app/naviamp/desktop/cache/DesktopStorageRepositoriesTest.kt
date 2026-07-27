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
import kotlin.test.assertTrue

class DesktopStorageRepositoriesTest {
    @Test
    fun composesFocusedRepositoriesAndMaintenanceOverOneSharedDatabase() {
        val root = Files.createTempDirectory("naviamp-desktop-storage-repositories")
        val audio = Files.createDirectories(root.resolve("audio"))
        val downloads = Files.createDirectories(root.resolve("downloads"))
        val trackedEviction = Files.write(audio.resolve("tracked-eviction.bin"), byteArrayOf(1, 2, 3))
        val trackedConvertedEviction = Files.write(audio.resolve("tracked-eviction.opus"), byteArrayOf(1, 2, 3))
        val trackedCache = Files.write(audio.resolve("tracked-cache.bin"), byteArrayOf(1, 2, 3))
        val trackedConvertedCache = Files.write(audio.resolve("tracked-cache.opus"), byteArrayOf(1, 2, 3))
        val databasePathThatIsNotAFile = Files.createDirectories(audio.resolve("not-a-file"))
        val unrelatedCache = Files.write(audio.resolve("music-library-track.flac"), byteArrayOf(4, 5, 6))
        val unrelatedCacheNested = Files.write(
            Files.createDirectories(audio.resolve("album")).resolve("track.flac"),
            byteArrayOf(7, 8, 9),
        )
        val trackedDownload = Files.write(downloads.resolve("tracked-download.bin"), byteArrayOf(10, 11, 12))
        val trackedConvertedDownload = Files.write(downloads.resolve("tracked-download.opus"), byteArrayOf(10, 11, 12))
        Files.write(downloads.resolve("orphaned-download.bin"), byteArrayOf(4, 5, 6))
        val unrelatedConvertedDownload = Files.write(downloads.resolve("personal-conversion.opus"), byteArrayOf(4, 5, 6))
        val unrelatedDownloadNested = Files.write(
            Files.createDirectories(downloads.resolve("artist")).resolve("song.mp3"),
            byteArrayOf(13, 14, 15),
        )
        val legacyCache = Files.write(root.resolve("cache.db"), byteArrayOf(16, 17, 18))
        val legacyStorage = Files.write(root.resolve("legacy-storage.db"), byteArrayOf(19, 20, 21))

        DesktopStorageRepositories.open(
            location = StorageDatabaseLocation(root.toString(), "storage.db"),
            audioCacheDirectory = audio,
            downloadDirectory = downloads,
            nowEpochMillis = { 7L },
            credentialProtector = PassthroughStorageCredentialProtector,
            legacyDatabaseFilesOnReset = listOf(legacyCache, legacyStorage),
        ).use { repositories ->
            val queries = repositories.mediaSources.database.naviampStorageQueries
            queries.upsertCachedAudio(
                source_id = "source",
                remote_track_id = "eviction-track",
                quality_key = "original",
                file_path = trackedEviction.toString(),
                size_bytes = Files.size(trackedEviction),
                content_type = "audio/mpeg",
                created_at_epoch_millis = 6L,
                last_accessed_epoch_millis = 6L,
            )
            queries.upsertCachedAudio(
                source_id = "source",
                remote_track_id = "cached-track",
                quality_key = "original",
                file_path = trackedCache.toString(),
                size_bytes = Files.size(trackedCache),
                content_type = "audio/mpeg",
                created_at_epoch_millis = 7L,
                last_accessed_epoch_millis = 7L,
            )
            queries.upsertCachedAudio(
                source_id = "source",
                remote_track_id = "converted-eviction-track",
                quality_key = "transcoded:opus:128",
                file_path = trackedConvertedEviction.toString(),
                size_bytes = Files.size(trackedConvertedEviction),
                content_type = "audio/ogg",
                created_at_epoch_millis = 6L,
                last_accessed_epoch_millis = 6L,
            )
            queries.upsertCachedAudio(
                source_id = "source",
                remote_track_id = "converted-cached-track",
                quality_key = "transcoded:opus:128",
                file_path = trackedConvertedCache.toString(),
                size_bytes = Files.size(trackedConvertedCache),
                content_type = "audio/ogg",
                created_at_epoch_millis = 8L,
                last_accessed_epoch_millis = 8L,
            )
            queries.upsertCachedAudio(
                source_id = "source",
                remote_track_id = "invalid-directory-track",
                quality_key = "original",
                file_path = databasePathThatIsNotAFile.toString(),
                size_bytes = 0L,
                content_type = "audio/mpeg",
                created_at_epoch_millis = 9L,
                last_accessed_epoch_millis = 9L,
            )
            queries.upsertDownloadedAudio(
                source_id = "source",
                remote_track_id = "downloaded-track",
                quality_key = "original",
                file_path = trackedDownload.toString(),
                size_bytes = Files.size(trackedDownload),
                content_type = "audio/mpeg",
                title = "Track",
                artist_id = null,
                artist_name = "Artist",
                album_id = null,
                album_title = null,
                album_release_year = null,
                duration_seconds = null,
                cover_art_id = null,
                audio_codec = null,
                audio_bitrate_kbps = null,
                audio_content_type = null,
                audio_bit_depth = null,
                audio_sampling_rate_hz = null,
                favorited_at_iso8601 = null,
                user_rating = null,
                downloaded_at_epoch_millis = 7L,
            )
            queries.upsertDownloadedAudio(
                source_id = "source",
                remote_track_id = "converted-downloaded-track",
                quality_key = "transcoded:opus:128",
                file_path = trackedConvertedDownload.toString(),
                size_bytes = Files.size(trackedConvertedDownload),
                content_type = "audio/ogg",
                title = "Converted Track",
                artist_id = null,
                artist_name = "Artist",
                album_id = null,
                album_title = null,
                album_release_year = null,
                duration_seconds = null,
                cover_art_id = null,
                audio_codec = "opus",
                audio_bitrate_kbps = 128L,
                audio_content_type = "audio/ogg",
                audio_bit_depth = null,
                audio_sampling_rate_hz = null,
                favorited_at_iso8601 = null,
                user_rating = null,
                downloaded_at_epoch_millis = 8L,
            )
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
            repositories.audioStore.updateAudioCacheLimit(6L)
            repositories.maintenance.updateAudioCacheLimit(6L)
            assertEquals(6L, repositories.maintenance.stats().maxAudioBytes)
            assertFalse(trackedEviction.exists())
            assertFalse(trackedConvertedEviction.exists())
            assertTrue(trackedCache.exists())
            assertTrue(trackedConvertedCache.exists())
            assertTrue(unrelatedCache.exists())
            assertTrue(unrelatedCacheNested.exists())
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
            assertFalse(trackedCache.exists())
            assertFalse(trackedConvertedCache.exists())
            assertTrue(databasePathThatIsNotAFile.exists())
            assertTrue(unrelatedCache.exists())
            assertTrue(unrelatedCacheNested.exists())

            repositories.maintenance.clearAll()

            assertFalse(trackedDownload.exists())
            assertFalse(trackedConvertedDownload.exists())
            assertTrue(downloads.resolve("orphaned-download.bin").exists())
            assertTrue(unrelatedConvertedDownload.exists())
            assertTrue(unrelatedDownloadNested.exists())
            assertFalse(legacyCache.exists())
            assertFalse(legacyStorage.exists())
            assertEquals(0L, repositories.maintenance.stats().downloadBytes)
        }

        root.toFile().deleteRecursively()
    }
}
