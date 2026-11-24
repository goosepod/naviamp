package app.naviamp.presentation

import app.naviamp.app.NaviampNavigationController
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.EmptyOfflinePlaylistRepository
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.cache.OfflinePlaylistRepository
import app.naviamp.domain.cache.OfflinePlaylistSnapshot
import app.naviamp.domain.cache.OfflinePlaylistSummary
import app.naviamp.domain.cache.PersistedDownloadJob
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.ui.NaviampMediaItemActionRequest
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NaviampOfflineAvailabilityUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampPlaylistDetailActionRequest
import app.naviamp.ui.NaviampPlaylistDetailCommand
import app.naviamp.ui.NaviampPlaylistMediaCommand
import app.naviamp.ui.NaviampShellConnectionUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedTrackRowUi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampCoreOfflinePlaylistActionsTest {
    private fun track(id: String) = FakeCoreMediaProvider().track.copy(id = TrackId(id), title = "Track $id")

    private class Snapshots(vararg snapshots: OfflinePlaylistSnapshot) : OfflinePlaylistRepository by EmptyOfflinePlaylistRepository {
        val saved = snapshots.associateBy { it.sourceId to it.playlist.id }
        override fun offlinePlaylistSummaries(sourceId: String) = saved.values.filter { it.sourceId == sourceId }
            .map { OfflinePlaylistSummary(it.sourceId, it.playlist, it.tracks.map { track -> track.id.value }) }
        override fun offlinePlaylist(sourceId: String, playlistId: String) = saved[sourceId to playlistId]
    }

    private class Effects : NaviampCorePlaylistPlaybackPort, NaviampCorePlaylistQueuePort, NaviampCorePlaylistDownloadPort {
        val played = mutableListOf<Pair<List<String>, Boolean>>()
        val queued = mutableListOf<List<String>>()
        override suspend fun play(playlist: Playlist, tracks: List<Track>, shuffle: Boolean) {
            played += tracks.map { it.id.value } to shuffle
        }
        override suspend fun addToQueue(playlist: Playlist, tracks: List<Track>) {
            queued += tracks.map { it.id.value }
        }
        override suspend fun download(playlist: Playlist, tracks: List<Track>, option: String?, onFinished: suspend (NaviampCoreDownloadOutcome) -> Unit) =
            error("Downloaded offline")
    }

    private class Fixture(
        val store: NaviampCoreStateStore,
        val browse: NaviampCorePlaylistBrowseController,
        val transactions: NaviampCorePlaylistTransactionController,
        val effects: Effects,
        var local: Set<String>,
    )

    private fun fixture(
        provider: MediaProvider?,
        offline: Boolean,
        snapshots: OfflinePlaylistRepository,
        local: Set<String>,
    ): Fixture {
        val store = NaviampCoreStateStore()
        store.updateShell { shell -> shell.copy(connectionSettings = shell.connectionSettings.copy(
            currentSourceId = "source", connection = NaviampShellConnectionUi(connected = true, offline = offline))) }
        val navigation = NaviampCoreNavigationController(NaviampNavigationController(), store,
            NaviampCoreArtistNavigator { error("Not expected") })
        val source = NaviampCoreMediaProviderSource { provider }
        lateinit var fixture: Fixture
        val browse = NaviampCorePlaylistBrowseController(store, source, navigation,
            offlinePlaylists = NaviampCoreOfflinePlaylistSource(snapshots) { sourceId ->
                fixture.local.takeIf { sourceId == "source" }
            })
        val effects = Effects()
        val sessionPort = object : NaviampCoreProviderSessionPort {
            override suspend fun connect(request: NaviampCoreConnectionRequest, plan: app.naviamp.app.NaviampConnectionAttemptPlan) =
                error("Not expected")
            override suspend fun editableConnection(id: String) = error("Not expected")
            override suspend fun deleteConnection(id: String) = NaviampCoreConnectionInventory()
            override suspend fun smartPlaylistProvider(password: String?) = provider
            override suspend fun refreshActiveSession() = false
            override suspend fun persistActiveSession() = Unit
            override suspend fun clearActiveSession() = Unit
        }
        val transactions = NaviampCorePlaylistTransactionController(store, source, browse, effects, effects, effects,
            sessionPort = sessionPort,
            messages = NaviampCorePlaylistMessages(
                offlineActionUnavailable = { "test: reconnect" },
                offlineNothingPlayable = { "test: nothing playable" },
            ))
        fixture = Fixture(store, browse, transactions, effects, local)
        return fixture
    }

    private val roadTrip = OfflinePlaylistSnapshot("source", Playlist("road", "Road Trip", 3),
        listOf("a", "b", "a").map(::track))
    private val silent = OfflinePlaylistSnapshot("source", Playlist("silent", "Silent", 2), listOf("x", "y").map(::track))
    private val otherAccount = OfflinePlaylistSnapshot("other", Playlist("other", "Other Account", 1), listOf(track("a")))

    private suspend fun Fixture.listed(id: String): SharedMediaItemUi {
        browse.execute(NaviampCoreCommand.Playlists.Refresh)
        return store.state.value.shell.playlists.playlists.single { it.id == id }
    }

    private suspend fun Fixture.open(item: SharedMediaItemUi) = browse.execute(NaviampCoreCommand.Media.ItemAction(
        NaviampMediaItemActionRequest(item, NaviampMediaItemCommand.Playlist(NaviampPlaylistMediaCommand.Select))))

    private suspend fun Fixture.detail(item: SharedMediaItemUi, command: NaviampPlaylistDetailCommand) =
        transactions.execute(NaviampCoreCommand.Playlists.Detail(NaviampPlaylistDetailActionRequest(item, command)))

    private fun Fixture.status() = store.state.value.shell.playlistDetail.status

    @Test fun serverFailureDuringASessionFallsBackToSavedPlaylistsForThisSourceOnly() = runTest {
        val unreachable = object : MediaProvider by FakeCoreMediaProvider() {
            override suspend fun playlists(limit: Int): List<Playlist> = error("Connection timed out")
            override suspend fun playlistTracks(playlistId: String): List<Track> = error("Connection timed out")
        }
        val fixture = fixture(unreachable, offline = false, Snapshots(roadTrip, otherAccount), local = setOf("a"))

        val item = fixture.listed("road")
        assertEquals(listOf("road"), fixture.store.state.value.shell.playlists.playlists.map { it.id })
        assertEquals(NaviampOfflineAvailabilityUi(2, 3), item.offlineAvailability)
        assertNull(fixture.store.state.value.shell.playlists.status)

        fixture.open(item)
        assertEquals(listOf("a", "b", "a"), fixture.store.state.value.shell.playlistDetail.detail?.tracks?.map(SharedTrackRowUi::id))
    }

    @Test fun zeroPlayableTracksKeepsThePlaylistButCannotStartPlaybackOrQueue() = runTest {
        val fixture = fixture(provider = null, offline = true, Snapshots(silent), local = emptySet())
        val item = fixture.listed("silent")
        assertEquals(NaviampOfflineAvailabilityUi(0, 2), item.offlineAvailability)

        fixture.detail(item, NaviampPlaylistDetailCommand.Play(shuffle = false))
        assertEquals("test: nothing playable", fixture.status())
        fixture.detail(item, NaviampPlaylistDetailCommand.AddToQueue)
        assertEquals("test: nothing playable", fixture.status())
        assertTrue(fixture.effects.played.isEmpty() && fixture.effects.queued.isEmpty())
    }

    @Test fun shuffleAndQueueUseOnlyTracksWhoseFilesAreStillPresent() = runTest {
        val fixture = fixture(provider = null, offline = true, Snapshots(roadTrip), local = setOf("a", "b"))
        val item = fixture.listed("road")
        fixture.open(item)

        fixture.local = setOf("b")
        fixture.detail(item, NaviampPlaylistDetailCommand.Play(shuffle = true))
        fixture.detail(item, NaviampPlaylistDetailCommand.AddToQueue)

        assertEquals(listOf(listOf("b") to true), fixture.effects.played)
        assertEquals(listOf(listOf("b")), fixture.effects.queued)
        fixture.open(item)
        val rows = checkNotNull(fixture.store.state.value.shell.playlistDetail.detail).tracks
        assertEquals(listOf("a", "b", "a"), rows.map { it.id }, "A missing file updates availability without removing rows")
        assertEquals(listOf(true, false, true), rows.map { it.unavailableOffline })
    }

    @Test fun playlistChangesWhileOfflineAreRefusedWithAClearMessage() = runTest {
        val fixture = fixture(provider = null, offline = true, Snapshots(roadTrip), local = setOf("a"))
        val item = fixture.listed("road")
        fixture.open(item)
        val choice = NaviampPlaylistChoiceUi("other", "Other")

        for (command in listOf(
            NaviampPlaylistDetailCommand.Rename("New name"),
            NaviampPlaylistDetailCommand.Delete,
            NaviampPlaylistDetailCommand.Download(null),
            NaviampPlaylistDetailCommand.AddToPlaylist(choice),
            NaviampPlaylistDetailCommand.CreatePlaylistAndAdd("Copy"),
            NaviampPlaylistDetailCommand.Copy("Copy", deduplicate = false),
        )) {
            fixture.store.updateShell { it.copy(playlistDetail = it.playlistDetail.copy(status = null)) }
            fixture.detail(item, command)
            assertEquals("test: reconnect", fixture.status(), command.toString())
        }
        val failure = assertFailsWith<IllegalStateException> {
            fixture.transactions.execute(NaviampCoreCommand.Playlists.UpdateTracks(item, emptyList(), null))
        }
        assertEquals("test: reconnect", failure.message)
        assertEquals(false, item.canEditPlaylist)
        assertEquals(false, item.canManagePlaylist)
    }

    @Test fun removingTheLastDownloadedTrackPrunesOnlyOrphanedOneTimeSnapshots() = runTest {
        val pruned = mutableListOf<Pair<String, String?>>()
        val downloaded = mutableListOf("a", "b")
        val repository = object : DownloadRepository<String, String> {
            override suspend fun downloadedAudioFile(sourceId: String, trackId: TrackId, quality: StreamQuality) = null
            override suspend fun downloadedAudioFile(sourceId: String, trackId: TrackId) = null
            override suspend fun downloadAudioTrack(sourceId: String, provider: MediaProvider, track: Track, quality: StreamQuality, maxDownloadBytes: Long) = error("Not used")
            override fun downloadedTracks(sourceId: String) = downloaded.toList()
            override fun removeDownloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) { downloaded.remove(trackId.value) }
            override fun removeDownloadedAudio(sourceId: String, trackId: TrackId) { downloaded.remove(trackId.value) }
        }
        val replacement = object : DownloadReplacementRepository<String> {
            override suspend fun replaceDownloadedAudioTrack(sourceId: String, provider: MediaProvider, track: Track, quality: StreamQuality, maxDownloadBytes: Long) = error("Not used")
        }
        val keep = object : KeepDownloadedRepository, OfflinePlaylistRepository by EmptyOfflinePlaylistRepository {
            override fun pruneOrphanedOfflinePlaylists(sourceId: String, removedTrackId: String?) { pruned += sourceId to removedTrackId }
            override fun savedDownloadJobs(sourceId: String) = emptyList<PersistedDownloadJob>()
            override fun saveDownloadJob(job: PersistedDownloadJob) = Unit
            override fun deleteDownloadJob(sourceId: String, jobId: String) = Unit
            override fun keepDownloadedPolicies(sourceId: String) = emptyList<KeepDownloadedCollectionPolicy>()
            override fun keepDownloadedPolicy(sourceId: String, kind: KeepDownloadedCollectionKind, collectionId: String) = null
            override fun upsertKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy) = Unit
            override fun deleteKeepDownloadedPolicy(sourceId: String, kind: KeepDownloadedCollectionKind, collectionId: String) = Unit
            override fun keepDownloadedTrackIds(sourceId: String, kind: KeepDownloadedCollectionKind, collectionId: String) = emptySet<String>()
            override fun replaceKeepDownloadedTrackIds(policy: KeepDownloadedCollectionPolicy, trackIds: Set<String>) = Unit
            override fun managedKeepDownloadedTrackIds(sourceId: String) = emptySet<String>()
            override fun markManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) = Unit
            override fun unmarkManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) = Unit
            override fun manuallyRetainedTrackIds(sourceId: String) = emptySet<String>()
            override fun retainManualTrack(sourceId: String, trackId: String) = Unit
            override fun releaseTrackRetention(sourceId: String, trackId: String) = Unit
        }
        val services = repositoryNaviampCoreDownloadServices(repository, replacement, keep,
            toCoreDownload = { NaviampCoreDownloadedTrack(it, track(it), 100) }, isStoredDownloadAvailable = { true })

        services.storage.remove("source", track("a"))
        services.storage.deleteAll("source")

        assertEquals(listOf("source" to "a", "source" to null), pruned)
        assertEquals(keep, services.offlinePlaylists)
    }
}
