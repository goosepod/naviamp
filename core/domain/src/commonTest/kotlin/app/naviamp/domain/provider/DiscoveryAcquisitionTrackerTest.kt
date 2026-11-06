package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiscoveryAcquisitionTrackerTest {
    private val scope = DiscoveryAcquisitionScope("source", "account")
    private val artist = ProviderDiscoveryArtist("11111111-1111-1111-1111-111111111111", "Artist")
    private val release = ProviderDiscoveryRelease("44444444-4444-4444-4444-444444444444", "Album")
    private val action = ProviderDiscoveryAction.RequestAlbum(release.id)
    private val identity = ProviderDiscoveryRequestIdentity(requestGroupId = DiscoveryRequestGroupId("group"),
        jobIds = listOf(DiscoveryJobId("job")), canonicalAlbumId = DiscoveryCanonicalAlbumId("71"),
        managedAlbumId = DiscoveryManagedAlbumId(DiscoveryLibraryManager.Aurral, "71"))
    private val queued = ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Queued, identity)
    private fun tracker(repo: DiscoveryAcquisitionRepository) = DiscoveryAcquisitionTracker(repo) { 42L }
    private suspend fun seed(repo: DiscoveryAcquisitionRepository, result: ProviderDiscoveryOperation = queued) =
        tracker(repo).submit(scope, artist, action, release) { result }

    @Test fun intentExistsBeforeNetworkAndRestartNeverReplaysAnAmbiguousSubmission() = runTest {
        val repo = MemoryDiscoveryAcquisitionRepository()
        var calls = 0
        tracker(repo).submit(scope, artist, action, release) {
            calls++
            assertEquals(DiscoveryAcquisitionPhase.Uncertain, repo.records(scope).single().phase)
            throw IllegalStateException("lost response")
        }
        val result = tracker(repo).submit(scope, artist, action, release) { calls++; queued }
        assertEquals(ProviderDiscoveryOperationStatus.Uncertain, result.status)
        assertEquals(1, calls)
    }
    @Test fun cancellationKeepsDurableIntentAndRestartCannotReplay() = runTest {
        val repo = MemoryDiscoveryAcquisitionRepository()
        assertFailsWith<CancellationException> {
            tracker(repo).submit(scope, artist, action, release) { throw CancellationException() }
        }
        assertEquals(ProviderDiscoveryOperationStatus.Uncertain,
            tracker(repo).submit(scope, artist, action, release) { error("replayed") }.status)
    }
    @Test fun storageFailurePreventsTheMutation() = runTest {
        val repo = object : DiscoveryAcquisitionRepository by MemoryDiscoveryAcquisitionRepository() {
            override fun save(scope: DiscoveryAcquisitionScope, record: DiscoveryAcquisitionRecord) { error("disk full") }
        }
        assertEquals(ProviderDiscoveryOperationStatus.Unavailable,
            tracker(repo).submit(scope, artist, action, release) { error("must not submit") }.status)
    }
    @Test fun concurrentAttemptsOnlySubmitOnce() = runTest {
        val repo = MemoryDiscoveryAcquisitionRepository(); val tracker = tracker(repo)
        val gate = CompletableDeferred<Unit>(); var calls = 0
        val first = async { tracker.submit(scope, artist, action, release) { calls++; gate.await(); queued } }
        runCurrent()
        val second = async { tracker.submit(scope, artist, action, release) { calls++; queued } }
        runCurrent(); gate.complete(Unit)
        assertEquals(queued, first.await()); assertEquals(queued, second.await()); assertEquals(1, calls)
    }
    @Test fun accountAndSourceScopesAreIndependent() = runTest {
        val repo = MemoryDiscoveryAcquisitionRepository(); seed(repo)
        for (other in listOf(scope.copy(sourceId = "other"), scope.copy(accountKey = "other"))) {
            assertTrue(repo.records(other).isEmpty())
            tracker(repo).submit(other, artist, action, release) { queued }
            assertEquals(1, repo.records(other).size)
        }
        assertEquals(1, repo.records(scope).size)
    }
    @Test fun waitingHasNoDateDrivenPollOrAutomaticMutation() = runTest {
        val repo = MemoryDiscoveryAcquisitionRepository()
        val waiting = ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.WaitingForRelease, identity,
            waitingForRelease = ProviderDiscoveryWaitingForRelease(DiscoveryLibraryManager.Aurral, "2027-03"))
        seed(repo, waiting)
        val snapshot = DiscoveryAcquisitionSnapshot(repo.records(scope))
        assertNull(DiscoveryAcquisitionTracker.nextPollMillis(snapshot, 1, 0))
        assertEquals(waiting, tracker(repo).submit(scope, artist, action, release) { error("replayed") })
    }
    @Test fun completedHistoryAndManagerStatusCannotInventPlayableLibraryIdentity() = runTest {
        val repo = MemoryDiscoveryAcquisitionRepository(); seed(repo)
        val native = object : ProviderDiscovery {
            override suspend fun load() = ProviderDiscoveryFeed()
            override suspend fun acquisitionStatus(identity: ProviderDiscoveryRequestIdentity) =
                ProviderDiscoveryAcquisitionStatus(ProviderDiscoveryStatus.Ready, DiscoveryAcquisitionPhase.Complete, "complete")
            override suspend fun releaseOwnership(release: DiscoveryReleaseGroupMbid) =
                ProviderDiscoveryReleaseOwnership(ProviderDiscoveryStatus.Ready, ProviderDiscoveryReleaseAvailability.Available)
        }
        val snapshot = tracker(repo).refresh(scope, native)
        assertEquals(DiscoveryAcquisitionPhase.Complete, snapshot.records.single().phase)
        assertNull(snapshot.ownership.values.single().libraryAlbum)
    }
    @Test fun delayedImportBecomesAvailableOnlyAfterTheValidatedPlaybackBridge() = runTest {
        val repo = MemoryDiscoveryAcquisitionRepository(); seed(repo)
        var imported = false
        val album = Album(AlbumId("library-id"), "Album", "Artist", null, null)
        val native = object : ProviderDiscovery {
            override suspend fun load() = ProviderDiscoveryFeed()
            override suspend fun requestActivity() = ProviderDiscoveryActivities(ProviderDiscoveryStatus.Ready)
            override suspend fun releaseOwnership(release: DiscoveryReleaseGroupMbid) =
                ProviderDiscoveryReleaseOwnership(ProviderDiscoveryStatus.Ready,
                    if (imported) ProviderDiscoveryReleaseAvailability.Available else ProviderDiscoveryReleaseAvailability.Catalogued,
                    libraryAlbum = album.takeIf { imported })
        }
        assertEquals(DiscoveryAcquisitionPhase.Queued, tracker(repo).refresh(scope, native).records.single().phase)
        imported = true
        val snapshot = tracker(repo).refresh(scope, native)
        assertEquals(DiscoveryAcquisitionPhase.Available, snapshot.records.single().phase)
        assertEquals(album, snapshot.ownership.values.single().libraryAlbum)
        imported = false
        assertEquals(DiscoveryAcquisitionPhase.Complete, tracker(repo).refresh(scope, native).records.single().phase)
    }
    @Test fun historyCorrelationUsesQualifiedIdentifiersAndRejectsAmbiguousMatches() = runTest {
        for ((items, expected) in listOf(
            listOf(activity(identity.copy(jobIds = emptyList(), requestGroupId = null, managedAlbumId = DiscoveryManagedAlbumId(DiscoveryLibraryManager.Lidarr, "71")))) to DiscoveryAcquisitionPhase.Queued,
            listOf(activity(identity)) to DiscoveryAcquisitionPhase.Downloading,
            listOf(activity(identity), activity(identity).copy(id = "second")) to DiscoveryAcquisitionPhase.Queued,
        )) {
            val repo = MemoryDiscoveryAcquisitionRepository(); seed(repo)
            val native = object : ProviderDiscovery {
                override suspend fun load() = ProviderDiscoveryFeed()
                override suspend fun requestActivity() = ProviderDiscoveryActivities(ProviderDiscoveryStatus.Ready, items)
            }
            assertEquals(expected, tracker(repo).refresh(scope, native).records.single().phase)
        }
    }
    @Test fun offlineReadRetainsAcceptedInformationAndNeverMutates() = runTest {
        val repo = MemoryDiscoveryAcquisitionRepository(); seed(repo)
        val snapshot = tracker(repo).refresh(scope, object : ProviderDiscovery {
            override suspend fun load() = ProviderDiscoveryFeed()
            override suspend fun requestActivity(): ProviderDiscoveryActivities = error("offline")
            override suspend fun acquisitionStatus(identity: ProviderDiscoveryRequestIdentity): ProviderDiscoveryAcquisitionStatus = error("offline")
            override suspend fun requestAlbum(artist: ProviderDiscoveryArtist, release: ProviderDiscoveryRelease): ProviderDiscoveryOperation = error("mutation")
        })
        assertEquals(DiscoveryAcquisitionPhase.Queued, snapshot.records.single().phase)
        assertEquals(ProviderDiscoveryStatus.Unavailable, snapshot.status)
    }
    @Test fun pollBudgetBackoffAndAuthenticationStopAreBounded() = runTest {
        val repo = MemoryDiscoveryAcquisitionRepository(); seed(repo)
        val snapshot = DiscoveryAcquisitionSnapshot(repo.records(scope))
        assertEquals(30_000L, DiscoveryAcquisitionTracker.nextPollMillis(snapshot, 1, 0))
        assertEquals(60_000L, DiscoveryAcquisitionTracker.nextPollMillis(snapshot, 2, 1))
        assertEquals(120_000L, DiscoveryAcquisitionTracker.nextPollMillis(snapshot, 3, 2))
        assertNull(DiscoveryAcquisitionTracker.nextPollMillis(snapshot, 8, 0))
        assertNull(DiscoveryAcquisitionTracker.nextPollMillis(snapshot, 3, 3))
        for (status in listOf(ProviderDiscoveryStatus.Forbidden, ProviderDiscoveryStatus.SignInRequired, ProviderDiscoveryStatus.Unsupported))
            assertNull(DiscoveryAcquisitionTracker.nextPollMillis(snapshot.copy(status = status), 1, 0))
    }
    @Test fun serverCompletedHistoryDoesNotStartAnIdlePollLoop() {
        assertFalse(DiscoveryAcquisitionSnapshot(emptyList(), ProviderDiscoveryActivities(ProviderDiscoveryStatus.Ready,
            listOf(activity(identity).copy(status = "completed")))).active)
        assertEquals(DiscoveryAcquisitionPhase.Unknown, acquisitionPhase("new-server-status"))
        assertEquals(DiscoveryAcquisitionPhase.Complete, acquisitionPhase("available"))
    }
    @Test fun recordingRequestNeedsTheExactOwnedRecordingAndAValidatedAlbum() = runTest {
        val repo = MemoryDiscoveryAcquisitionRepository()
        val recording = "55555555-5555-5555-5555-555555555555"
        tracker(repo).submit(scope, artist, ProviderDiscoveryAction.RequestTrack(recording), release, "Track") { queued }
        var exactRecording = false
        val native = object : ProviderDiscovery {
            override suspend fun load() = ProviderDiscoveryFeed()
            override suspend fun requestActivity() = ProviderDiscoveryActivities(ProviderDiscoveryStatus.Ready)
            override suspend fun releaseOwnership(release: DiscoveryReleaseGroupMbid) = ProviderDiscoveryReleaseOwnership(
                ProviderDiscoveryStatus.Ready, ProviderDiscoveryReleaseAvailability.Partial,
                ownedRecordings = setOf(DiscoveryRecordingMbid(if (exactRecording) recording else "66666666-6666-6666-6666-666666666666")),
                libraryAlbum = Album(AlbumId("playable"), "Album", "Artist", null, null))
        }
        assertEquals(DiscoveryAcquisitionPhase.Queued, tracker(repo).refresh(scope, native).records.single().phase)
        exactRecording = true
        assertEquals(DiscoveryAcquisitionPhase.Available, tracker(repo).refresh(scope, native).records.single().phase)
    }
    @Test fun reconciliationRotatesThroughABoundedBatchOfSavedRecords() = runTest {
        val repo = MemoryDiscoveryAcquisitionRepository(); seed(repo)
        val initial = repo.records(scope).single()
        repeat(25) { repo.save(scope, initial.copy(id = "request-$it", actionId = "release-$it", releaseId = null)) }
        var reads = 0
        val native = object : ProviderDiscovery {
            override suspend fun load() = ProviderDiscoveryFeed()
            override suspend fun requestActivity() = ProviderDiscoveryActivities(ProviderDiscoveryStatus.Ready)
            override suspend fun acquisitionStatus(identity: ProviderDiscoveryRequestIdentity): ProviderDiscoveryAcquisitionStatus {
                reads++; return ProviderDiscoveryAcquisitionStatus(ProviderDiscoveryStatus.Ready, DiscoveryAcquisitionPhase.Searching)
            }
        }
        tracker(repo).refresh(scope, native)
        assertEquals(20, reads)
        assertEquals(20, repo.records(scope).count { it.checkedAtMillis != null })
        tracker(repo).refresh(scope, native)
        assertEquals(40, reads)
        assertTrue(repo.records(scope).all { it.checkedAtMillis != null })
    }
    @Test fun unrelatedServerActivityDoesNotStartAcquisitionPolling() {
        val snapshot = DiscoveryAcquisitionSnapshot(emptyList(), ProviderDiscoveryActivities(ProviderDiscoveryStatus.Ready,
            listOf(ProviderDiscoveryActivity("flow", "processing", null, kind = "flow_generating"))))
        assertFalse(snapshot.active)
        assertEquals(DiscoveryAcquisitionPhase.Processing, acquisitionPhase("processing"))
        assertEquals(DiscoveryAcquisitionPhase.Complete, acquisitionPhase("added"))
    }
    private fun activity(identity: ProviderDiscoveryRequestIdentity) = ProviderDiscoveryActivity("history", "downloading", identity)
}
