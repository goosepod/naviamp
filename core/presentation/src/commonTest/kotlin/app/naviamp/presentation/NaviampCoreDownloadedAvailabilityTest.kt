package app.naviamp.presentation

import app.naviamp.domain.*
import app.naviamp.domain.cache.*
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NaviampCoreDownloadedAvailabilityTest {
    @Test fun verifiedSnapshotIsSourceScopedReadOnlyAndExcludesMissingAndZeroByteRecords() = runTest {
        val track = FakeCoreMediaProvider().track
        val rows = listOf(
            NaviampCoreDownloadedTrack("valid", track, 100),
            NaviampCoreDownloadedTrack("missing", track.copy(id = TrackId("missing")), 200),
            NaviampCoreDownloadedTrack("zero", track.copy(id = TrackId("zero")), 0),
        )
        val sources = mutableListOf<String>()
        val repository = object : DownloadRepository<String, NaviampCoreDownloadedTrack> {
            override fun downloadedTracks(sourceId: String): List<NaviampCoreDownloadedTrack> {
                sources += sourceId
                return if (sourceId == "source") rows else emptyList()
            }
            override suspend fun downloadedAudioFile(sourceId: String, trackId: TrackId, quality: StreamQuality): String? = error("Not an audio resolution")
            override suspend fun downloadedAudioFile(sourceId: String, trackId: TrackId): String? = error("Not an audio resolution")
            override suspend fun downloadAudioTrack(sourceId: String, provider: MediaProvider, track: Track, quality: StreamQuality, maxDownloadBytes: Long): String = error("Started transfer")
            override fun removeDownloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) = error("Pruned download")
            override fun removeDownloadedAudio(sourceId: String, trackId: TrackId) = error("Pruned download")
        }
        val replacement = object : DownloadReplacementRepository<String> {
            override suspend fun replaceDownloadedAudioTrack(sourceId: String, provider: MediaProvider, track: Track, quality: StreamQuality, maxDownloadBytes: Long): String = error("Replaced file")
        }
        val keep = object : KeepDownloadedRepository {
            override fun savedDownloadJobs(sourceId: String): List<PersistedDownloadJob> = error("Read jobs")
            override fun saveDownloadJob(job: PersistedDownloadJob) = error("Wrote job")
            override fun deleteDownloadJob(sourceId: String, jobId: String) = error("Deleted job")
            override fun keepDownloadedPolicies(sourceId: String): List<KeepDownloadedCollectionPolicy> = error("Read policies")
            override fun keepDownloadedPolicy(sourceId: String, kind: KeepDownloadedCollectionKind, collectionId: String): KeepDownloadedCollectionPolicy? = error("Read policy")
            override fun upsertKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy) = error("Wrote policy")
            override fun deleteKeepDownloadedPolicy(sourceId: String, kind: KeepDownloadedCollectionKind, collectionId: String) = error("Deleted policy")
            override fun keepDownloadedTrackIds(sourceId: String, kind: KeepDownloadedCollectionKind, collectionId: String): Set<String> = error("Read retention")
            override fun replaceKeepDownloadedTrackIds(policy: KeepDownloadedCollectionPolicy, trackIds: Set<String>) = error("Wrote retention")
            override fun managedKeepDownloadedTrackIds(sourceId: String): Set<String> = error("Read managed")
            override fun markManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) = error("Wrote managed")
            override fun unmarkManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) = error("Wrote managed")
            override fun manuallyRetainedTrackIds(sourceId: String): Set<String> = error("Read manual")
            override fun retainManualTrack(sourceId: String, trackId: String) = error("Wrote manual")
            override fun releaseTrackRetention(sourceId: String, trackId: String) = error("Released retention")
        }
        val services = repositoryNaviampCoreDownloadServices(repository, replacement, keep,
            toCoreDownload = { it }, isStoredDownloadAvailable = { it.storageId != "missing" })
        assertEquals(listOf(rows[0]), services.storage.availableDownloads("source"))
        assertEquals(emptyList(), services.storage.availableDownloads("other-source"))
        assertEquals(rows, services.storage.snapshot("source").downloads, "Verification must not remove metadata records")
        assertEquals(listOf("source", "other-source", "source"), sources)
    }
}
